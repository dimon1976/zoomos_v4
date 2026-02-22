package com.java.service;

import com.java.model.entity.ZoomosShopSchedule;
import com.java.repository.ZoomosShopScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZoomosSchedulerService {

    private final ZoomosShopScheduleRepository scheduleRepo;
    private final ZoomosCheckService checkService;

    @Qualifier("zoomosSchedulerTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduleMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        scheduleRepo.findAllByIsEnabledTrue().forEach(this::scheduleCheck);
        log.info("ZoomosSchedulerService: загружено {} активных расписаний", scheduleMap.size());
    }

    public void saveAndReschedule(ZoomosShopSchedule schedule) {
        scheduleRepo.save(schedule);
        unschedule(schedule.getShopId());
        if (schedule.isEnabled()) {
            scheduleCheck(schedule);
        }
    }

    public void deleteSchedule(Long shopId) {
        unschedule(shopId);
        scheduleRepo.findByShopId(shopId).ifPresent(scheduleRepo::delete);
    }

    public void toggleEnabled(Long shopId) {
        scheduleRepo.findByShopId(shopId).ifPresent(schedule -> {
            schedule.setEnabled(!schedule.isEnabled());
            saveAndReschedule(schedule);
            log.info("Расписание shopId={} — isEnabled={}", shopId, schedule.isEnabled());
        });
    }

    private void unschedule(Long shopId) {
        ScheduledFuture<?> existing = scheduleMap.remove(shopId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void scheduleCheck(ZoomosShopSchedule s) {
        try {
            // Unix 5-field cron → Spring 6-field (добавляем "0" в начало для секунд)
            String springCron = "0 " + s.getCronExpression();
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runCheck(s), new CronTrigger(springCron));
            scheduleMap.put(s.getShopId(), future);
            log.info("Запланирована проверка для shopId={} по cron='{}'", s.getShopId(), springCron);
        } catch (Exception e) {
            log.error("Ошибка при планировании shopId={}: {}", s.getShopId(), e.getMessage());
        }
    }

    private void runCheck(ZoomosShopSchedule s) {
        LocalDate today = LocalDate.now();
        LocalDate dateFrom = today.plusDays(s.getDateOffsetFrom());
        LocalDate dateTo   = today.plusDays(s.getDateOffsetTo());
        String operationId = UUID.randomUUID().toString();
        log.info("Автопроверка shopId={}: {} — {}", s.getShopId(), dateFrom, dateTo);
        try {
            checkService.runCheck(s.getShopId(), dateFrom, dateTo,
                    s.getTimeFrom(), s.getTimeTo(),
                    s.getDropThreshold(), s.getErrorGrowthThreshold(),
                    s.getBaselineDays(), operationId);
            scheduleRepo.findByShopId(s.getShopId()).ifPresent(schedule -> {
                schedule.setLastRunAt(ZonedDateTime.now(ZoneOffset.UTC));
                scheduleRepo.save(schedule);
            });
        } catch (Exception e) {
            log.error("Ошибка автопроверки shopId={}: {}", s.getShopId(), e.getMessage(), e);
        }
    }

    /** Возвращает true если для shopId есть активное расписание в памяти */
    public boolean isScheduled(Long shopId) {
        return scheduleMap.containsKey(shopId);
    }
}
