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
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZoomosSchedulerService {

    private final ZoomosShopScheduleRepository scheduleRepo;
    private final ZoomosCheckService checkService;

    @Qualifier("zoomosSchedulerTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    /** Ключ — scheduleId (не shopId), чтобы поддерживать несколько расписаний на магазин */
    private final Map<Long, ScheduledFuture<?>> scheduleMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        scheduleRepo.findAllByIsEnabledTrue().forEach(this::scheduleCheck);
        log.info("ZoomosSchedulerService: загружено {} активных расписаний", scheduleMap.size());
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

    /** @deprecated используется только для обратной совместимости с index.html */
    public void deleteSchedule(Long shopId) {
        scheduleRepo.findFirstByShopId(shopId).ifPresent(s -> deleteScheduleById(s.getId()));
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

    /** @deprecated используется для обратной совместимости с index.html */
    public void toggleEnabled(Long shopId) {
        toggleAllByShopId(shopId);
    }

    private void unschedule(Long scheduleId) {
        ScheduledFuture<?> existing = scheduleMap.remove(scheduleId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void scheduleCheck(ZoomosShopSchedule s) {
        try {
            String springCron = toSpringCron(s.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runCheck(s), new CronTrigger(springCron));
            scheduleMap.put(s.getId(), future);
            log.info("Запланирована проверка для id={} shopId={} по cron='{}'", s.getId(), s.getShopId(), springCron);
        } catch (Exception e) {
            log.error("Ошибка при планировании id={} shopId={}: {}", s.getId(), s.getShopId(), e.getMessage());
        }
    }

    /**
     * Конвертирует пользовательское cron-выражение в Spring-формат.
     * Пользователь вводит Quartz-нумерацию дня недели (1=Вс, 2=Пн, 3=Вт ... 7=Сб).
     * Spring CronExpression использует Unix (0=Вс, 1=Пн ... 6=Сб).
     * Конвертация: значение - 1 (кроме * и шагов).
     */
    private String toSpringCron(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 5) {
            // 5-полное Unix-выражение без секунд — добавляем "0 " в начало
            parts = ("0 " + raw.trim()).split("\\s+");
        }
        if (parts.length == 6) {
            parts[5] = convertDowField(parts[5]);
        }
        return String.join(" ", parts);
    }

    /** Конвертирует поле дня недели: вычитает 1 из каждого числового значения. */
    private String convertDowField(String field) {
        if (field.equals("*")) return field;
        return Arrays.stream(field.split(","))
                .map(part -> {
                    if (part.contains("-")) {
                        String[] bounds = part.split("-", 2);
                        try {
                            int from = Integer.parseInt(bounds[0]) - 1;
                            int to   = Integer.parseInt(bounds[1]) - 1;
                            return from + "-" + to;
                        } catch (NumberFormatException e) { return part; }
                    }
                    if (part.startsWith("*/")) return part;
                    try { return String.valueOf(Integer.parseInt(part) - 1); }
                    catch (NumberFormatException e) { return part; } // MON, TUE и т.д. — без изменений
                })
                .collect(Collectors.joining(","));
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
            checkService.runCheck(latest.getShopId(), dateFrom, dateTo,
                    latest.getTimeFrom(), latest.getTimeTo(),
                    latest.getDropThreshold(), latest.getErrorGrowthThreshold(),
                    latest.getBaselineDays(), latest.getMinAbsoluteErrors(),
                    latest.getTrendDropThreshold(), latest.getTrendErrorThreshold(),
                    operationId);
            scheduleRepo.findById(s.getId()).ifPresent(schedule -> {
                schedule.setLastRunAt(ZonedDateTime.now());
                scheduleRepo.save(schedule);
            });
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
