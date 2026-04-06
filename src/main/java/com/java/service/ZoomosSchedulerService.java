package com.java.service;

import com.java.util.CronUtils;
import com.java.dto.zoomos.ZoomosCheckParams;
import com.java.model.entity.CheckRunStatus;
import com.java.model.entity.ZoomosCheckRun;
import com.java.model.entity.ZoomosShopSchedule;
import com.java.repository.ZoomosCheckRunRepository;
import com.java.repository.ZoomosShopScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;


@Service
@Slf4j
@RequiredArgsConstructor
public class ZoomosSchedulerService {

    private final ZoomosShopScheduleRepository scheduleRepo;
    private final ZoomosCheckRunRepository checkRunRepository;
    private final ZoomosCheckService checkService;

    @Qualifier("zoomosSchedulerTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    /** Ключ — scheduleId (не shopId), чтобы поддерживать несколько расписаний на магазин */
    private final Map<Long, ScheduledFuture<?>> scheduleMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Переводим зависшие RUNNING-проверки в FAILED (остались после рестарта приложения)
        List<ZoomosCheckRun> stuckRuns = checkRunRepository.findAllByStatus(CheckRunStatus.RUNNING);
        if (!stuckRuns.isEmpty()) {
            ZonedDateTime now = ZonedDateTime.now();
            stuckRuns.forEach(r -> { r.setStatus(CheckRunStatus.FAILED); r.setCompletedAt(now); });
            checkRunRepository.saveAll(stuckRuns);
            log.warn("ZoomosSchedulerService: {} зависших RUNNING-проверок переведены в FAILED", stuckRuns.size());
        }
        List<ZoomosShopSchedule> enabledSchedules = scheduleRepo.findAllByIsEnabledTrue();
        enabledSchedules.forEach(this::scheduleCheck);
        log.info("ZoomosSchedulerService: загружено {} активных расписаний", scheduleMap.size());

        // Диагностика: уведомить о пропущенных запусках (lastRunAt > 2 часов назад)
        ZonedDateTime threshold = ZonedDateTime.now().minusHours(2);
        enabledSchedules.forEach(s -> {
            ZonedDateTime lastRun = s.getLastRunAt();
            if (lastRun == null || lastRun.isBefore(threshold)) {
                log.warn("Расписание id={} shopId={} cron='{}': lastRunAt={} — возможно пропущен запуск. " +
                         "Spring CronTrigger не наверстывает пропущенные запуски — следующий запуск будет по расписанию.",
                        s.getId(), s.getShopId(), s.getCronExpression(), lastRun);
            }
        });
    }

    public void saveAndReschedule(ZoomosShopSchedule schedule) {
        scheduleRepo.save(schedule);
        unschedule(schedule.getId());
        if (schedule.isEnabled()) {
            scheduleCheck(schedule);
        }
    }

    public void deleteScheduleById(Long scheduleId) {
        unschedule(scheduleId);
        scheduleRepo.findById(scheduleId).ifPresent(scheduleRepo::delete);
    }

    /** Переключает одно конкретное расписание по scheduleId */
    public void toggleEnabledById(Long scheduleId) {
        scheduleRepo.findById(scheduleId).ifPresent(schedule -> {
            schedule.setEnabled(!schedule.isEnabled());
            saveAndReschedule(schedule);
            log.info("Расписание id={} shopId={} — isEnabled={}", scheduleId, schedule.getShopId(), schedule.isEnabled());
        });
    }

    /** Переключает ВСЕ расписания магазина (для index.html).
     *  Если хоть одно включено — выключает все; иначе — включает все. */
    public void toggleAllByShopId(Long shopId) {
        List<ZoomosShopSchedule> all = scheduleRepo.findAllByShopId(shopId);
        boolean anyEnabled = all.stream().anyMatch(ZoomosShopSchedule::isEnabled);
        for (ZoomosShopSchedule s : all) {
            s.setEnabled(!anyEnabled);
            saveAndReschedule(s);
        }
        log.info("toggleAllByShopId shopId={}: anyWasEnabled={}, теперь={}", shopId, anyEnabled, !anyEnabled);
    }

    private void unschedule(Long scheduleId) {
        ScheduledFuture<?> existing = scheduleMap.remove(scheduleId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void scheduleCheck(ZoomosShopSchedule s) {
        try {
            String springCron = CronUtils.toSpringCron(s.getCronExpression());
            CronTrigger trigger = new CronTrigger(springCron);
            ScheduledFuture<?> future = taskScheduler.schedule(() -> runCheck(s), trigger);
            scheduleMap.put(s.getId(), future);
            Date nextFire = trigger.nextExecutionTime(new SimpleTriggerContext());
            log.info("Расписание id={} shopId={} cron='{}' зарегистрировано, следующий запуск: {}",
                    s.getId(), s.getShopId(), springCron, nextFire);
        } catch (Exception e) {
            log.error("Ошибка при планировании id={} shopId={} cron='{}': {}",
                    s.getId(), s.getShopId(), s.getCronExpression(), e.getMessage());
        }
    }

    private void runCheck(ZoomosShopSchedule s) {
        // Перезагружаем из БД по scheduleId, чтобы всегда использовать актуальные настройки
        ZoomosShopSchedule latest = scheduleRepo.findById(s.getId()).orElse(s);
        LocalDate today = LocalDate.now();
        LocalDate dateFrom = today.plusDays(latest.getDateOffsetFrom());
        LocalDate dateTo   = today.plusDays(latest.getDateOffsetTo());
        String operationId = UUID.randomUUID().toString();
        log.info("Автопроверка id={} shopId={}: {} — {} (offset {} .. {})",
                s.getId(), s.getShopId(), dateFrom, dateTo, latest.getDateOffsetFrom(), latest.getDateOffsetTo());
        try {
            checkService.runCheck(ZoomosCheckParams.builder()
                    .shopId(latest.getShopId())
                    .scheduleId(s.getId())
                    .dateFrom(dateFrom)
                    .dateTo(dateTo)
                    .timeFrom(latest.getTimeFrom())
                    .timeTo(latest.getTimeTo())
                    .dropThreshold(latest.getDropThreshold())
                    .errorGrowthThreshold(latest.getErrorGrowthThreshold())
                    .baselineDays(latest.getBaselineDays())
                    .minAbsoluteErrors(latest.getMinAbsoluteErrors())
                    .trendDropThreshold(latest.getTrendDropThreshold())
                    .trendErrorThreshold(latest.getTrendErrorThreshold())
                    .operationId(operationId)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка автопроверки id={} shopId={}: {}", s.getId(), s.getShopId(), e.getMessage(), e);
        }
    }

    /** Возвращает true если для shopId есть хотя бы одно активное расписание в памяти */
    public boolean isScheduled(Long shopId) {
        return scheduleRepo.findAllByShopId(shopId).stream()
                .anyMatch(s -> s.getId() != null && scheduleMap.containsKey(s.getId()));
    }
}
