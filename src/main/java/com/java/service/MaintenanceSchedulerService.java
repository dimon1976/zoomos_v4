package com.java.service;

import com.java.dto.DataCleanupRequestDto;
import com.java.dto.DataCleanupResultDto;
import com.java.service.maintenance.FileManagementService;
import com.java.service.maintenance.DatabaseMaintenanceService;
import com.java.service.maintenance.SystemHealthService;
import com.java.service.maintenance.DataCleanupService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Сервис автоматизации обслуживания системы.
 * Расписание хранится в zoomos_settings (ключи maint.*) и управляется через /maintenance/schedule.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MaintenanceSchedulerService {

    private final FileManagementService fileManagementService;
    private final DatabaseMaintenanceService databaseMaintenanceService;
    private final SystemHealthService systemHealthService;
    private final MaintenanceNotificationService notificationService;
    private final DataCleanupService dataCleanupService;
    private final ZoomosSettingsService settingsService;

    @Qualifier("maintenanceTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final List<String> TASK_KEYS = List.of(
            "fileArchive", "dbCleanup", "healthCheck", "perfAnalysis", "fullMaintenance");

    private final Map<String, ScheduledFuture<?>> scheduleMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        rescheduleAll();
        log.info("MaintenanceSchedulerService: инициализирован, активных задач: {}", scheduleMap.size());
    }

    /**
     * Пересчитывает все задачи по текущим настройкам из zoomos_settings.
     * Вызывается при старте и после сохранения настроек через UI.
     */
    public void rescheduleAll() {
        boolean globalEnabled = "true".equals(settingsService.getString("maint.enabled", "false"));
        TASK_KEYS.forEach(key -> {
            unscheduleTask(key);
            if (globalEnabled && "true".equals(settingsService.getString("maint." + key + ".enabled", "true"))) {
                String cron = settingsService.getString("maint." + key + ".cron", "");
                if (!cron.isBlank()) {
                    scheduleTask(key, cron);
                }
            }
        });
        log.info("MaintenanceSchedulerService: rescheduleAll завершён, активных задач: {}", scheduleMap.size());
    }

    /**
     * Ручной немедленный запуск задачи по ключу (асинхронно на пуле планировщика).
     */
    public void triggerTask(String taskKey) {
        log.info("MaintenanceSchedulerService: ручной запуск задачи '{}'", taskKey);
        taskScheduler.execute(getTaskRunnable(taskKey));
    }

    /** Возвращает true если глобальный планировщик включён */
    public boolean isEnabled() {
        return "true".equals(settingsService.getString("maint.enabled", "false"));
    }

    /** Возвращает true если конкретная задача сейчас активно запланирована */
    public boolean isTaskScheduled(String taskKey) {
        return scheduleMap.containsKey(taskKey);
    }

    private void scheduleTask(String key, String cron) {
        try {
            String springCron = toSpringCron(cron);
            ScheduledFuture<?> future = taskScheduler.schedule(getTaskRunnable(key), new CronTrigger(springCron));
            scheduleMap.put(key, future);
            log.info("MaintenanceSchedulerService: задача '{}' запланирована по cron='{}'", key, springCron);
        } catch (Exception e) {
            log.error("MaintenanceSchedulerService: ошибка планирования задачи '{}' cron='{}': {}", key, cron, e.getMessage());
        }
    }

    /** Конвертирует 5-польный Unix cron в 6-польный Spring cron (добавляет секунды). */
    private String toSpringCron(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 5) {
            parts = ("0 " + raw.trim()).split("\\s+");
        }
        return String.join(" ", parts);
    }

    private DataCleanupRequestDto buildCleanupRequest(String initiatedBy) {
        int retentionDays = settingsService.getInt("database.maintenance.cleanup.old-data.days", 120);
        return DataCleanupRequestDto.builder()
                .cutoffDate(LocalDateTime.now().minusDays(retentionDays))
                .entityTypes(Set.of("IMPORT_SESSIONS", "EXPORT_SESSIONS", "FILE_OPERATIONS", "IMPORT_ERRORS"))
                .batchSize(1000)
                .dryRun(false)
                .initiatedBy(initiatedBy)
                .build();
    }

    private void unscheduleTask(String key) {
        ScheduledFuture<?> existing = scheduleMap.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private Runnable getTaskRunnable(String key) {
        return switch (key) {
            case "fileArchive"     -> this::scheduleFileArchiving;
            case "dbCleanup"       -> this::scheduleDatabaseCleanup;
            case "healthCheck"     -> this::scheduleHealthCheck;
            case "perfAnalysis"    -> this::schedulePerformanceAnalysis;
            case "fullMaintenance" -> this::scheduleFullMaintenance;
            default -> () -> log.warn("MaintenanceSchedulerService: неизвестная задача '{}'", key);
        };
    }

    private void recordLastRun(String key) {
        settingsService.set("maint." + key + ".lastRunAt", LocalDateTime.now().format(FORMATTER));
    }

    // ─── Тела задач ──────────────────────────────────────────────────────────

    public void scheduleFileArchiving() {
        log.info("Запуск планового архивирования файлов: {}", LocalDateTime.now().format(FORMATTER));
        recordLastRun("fileArchive");
        try {
            var result = fileManagementService.archiveOldFiles(30);
            if (result.isSuccess()) {
                log.info("Плановое архивирование завершено: архивировано {} файлов, размер: {}",
                        result.getArchivedFiles(), result.getFormattedArchivedSize());
                notificationService.sendMaintenanceNotification(
                        "Архивирование файлов",
                        String.format("Успешно архивировано %d файлов (%s)",
                                result.getArchivedFiles(), result.getFormattedArchivedSize()),
                        "success");
            } else {
                log.info("Плановое архивирование: файлы для архивирования не найдены");
            }
        } catch (Exception e) {
            log.error("Ошибка планового архивирования файлов", e);
            notificationService.sendMaintenanceNotification(
                    "Ошибка архивирования",
                    "Ошибка при выполнении планового архивирования: " + e.getMessage(),
                    "error");
        }
    }

    public void scheduleDatabaseCleanup() {
        log.info("Запуск плановой очистки БД: {}", LocalDateTime.now().format(FORMATTER));
        recordLastRun("dbCleanup");
        try {
            DataCleanupRequestDto request = buildCleanupRequest("SCHEDULER");
            DataCleanupResultDto result = dataCleanupService.executeCleanup(request);
            if (result.isSuccess()) {
                long totalDeleted = result.getTotalRecordsDeleted();
                log.info("Плановая очистка БД завершена: удалено {} записей, освобождено {}",
                        totalDeleted, result.getFormattedFreedSpace());
                notificationService.sendMaintenanceNotification(
                        "Очистка базы данных",
                        String.format("Успешно удалено %d записей, освобождено %s",
                                totalDeleted, result.getFormattedFreedSpace()),
                        "success");
            } else {
                log.info("Плановая очистка БД: данные для очистки не найдены");
            }
        } catch (Exception e) {
            log.error("Ошибка плановой очистки БД", e);
            notificationService.sendMaintenanceNotification(
                    "Ошибка очистки БД",
                    "Ошибка при выполнении плановой очистки БД: " + e.getMessage(),
                    "error");
        }
    }

    public void scheduleHealthCheck() {
        log.debug("Выполнение плановой проверки состояния системы: {}", LocalDateTime.now().format(FORMATTER));
        recordLastRun("healthCheck");
        try {
            var health = systemHealthService.checkSystemHealth();
            if (!"HEALTHY".equals(health.getOverallStatus())) {
                log.warn("Обнаружены проблемы в системе: статус = {}, оценка = {}",
                        health.getOverallStatus(), health.getSystemScore());
                notificationService.sendMaintenanceNotification(
                        "Предупреждение системы",
                        String.format("Состояние: %s, Оценка: %.1f, Рекомендация: %s",
                                health.getOverallStatus(), health.getSystemScore(), health.getRecommendation()),
                        "warning");
            }
            var resources = systemHealthService.getSystemResources();
            if (resources.getMemoryUsagePercent() > 90) {
                notificationService.sendMaintenanceNotification(
                        "Критическая нагрузка",
                        String.format("Использование памяти: %.1f%% (критический уровень)", resources.getMemoryUsagePercent()),
                        "error");
            } else if (resources.getMemoryUsagePercent() > 80) {
                notificationService.sendMaintenanceNotification(
                        "Высокая нагрузка",
                        String.format("Использование памяти: %.1f%% (высокий уровень)", resources.getMemoryUsagePercent()),
                        "warning");
            }
            if (resources.getDiskUsagePercent() > 95) {
                notificationService.sendMaintenanceNotification(
                        "Критическая заполненность диска",
                        String.format("Использование диска: %.1f%% (критический уровень)", resources.getDiskUsagePercent()),
                        "error");
            }
        } catch (Exception e) {
            log.error("Ошибка плановой проверки состояния системы", e);
            notificationService.sendMaintenanceNotification(
                    "Ошибка мониторинга",
                    "Ошибка при выполнении проверки состояния системы: " + e.getMessage(),
                    "error");
        }
    }

    public void schedulePerformanceAnalysis() {
        log.info("Запуск планового анализа производительности БД: {}", LocalDateTime.now().format(FORMATTER));
        recordLastRun("perfAnalysis");
        try {
            var slowQueries = databaseMaintenanceService.analyzeQueryPerformance();
            if (!slowQueries.isEmpty()) {
                int slowQueryCount = (int) slowQueries.stream().mapToInt(q -> q.isSlowQuery() ? 1 : 0).sum();
                if (slowQueryCount > 0) {
                    log.warn("Обнаружено {} медленных запросов в системе", slowQueryCount);
                    notificationService.sendMaintenanceNotification(
                            "Анализ производительности",
                            String.format("Обнаружено %d медленных запросов. Рекомендуется оптимизация.", slowQueryCount),
                            "warning");
                } else {
                    log.info("Анализ производительности БД: проблем не обнаружено");
                }
            }
        } catch (Exception e) {
            log.error("Ошибка планового анализа производительности БД", e);
            notificationService.sendMaintenanceNotification(
                    "Ошибка анализа производительности",
                    "Ошибка при выполнении анализа производительности БД: " + e.getMessage(),
                    "error");
        }
    }

    public void scheduleFullMaintenance() {
        log.info("Запуск планового полного обслуживания системы: {}", LocalDateTime.now().format(FORMATTER));
        recordLastRun("fullMaintenance");
        try {
            var fileResult = fileManagementService.archiveOldFiles(30);
            DataCleanupResultDto dbResult = dataCleanupService.executeCleanup(buildCleanupRequest("SCHEDULER_FULL_MAINTENANCE"));
            var health = systemHealthService.checkSystemHealth();

            StringBuilder report = new StringBuilder("Полное обслуживание системы завершено:\n");
            if (fileResult.isSuccess()) {
                report.append(String.format("• Архивировано файлов: %d (%s)\n",
                        fileResult.getArchivedFiles(), fileResult.getFormattedArchivedSize()));
            } else {
                report.append("• Файлы для архивирования не найдены\n");
            }
            if (dbResult.isSuccess()) {
                report.append(String.format("• Очищено БД: %d записей (%s)\n",
                        dbResult.getTotalRecordsDeleted(), dbResult.getFormattedFreedSpace()));
            } else {
                report.append("• БД: данные для очистки не найдены\n");
            }
            report.append(String.format("• Состояние системы: %s (%.1f)",
                    health.getOverallStatus(), health.getSystemScore()));

            log.info("Плановое полное обслуживание завершено");
            notificationService.sendMaintenanceNotification(
                    "Полное обслуживание системы",
                    report.toString(),
                    "HEALTHY".equals(health.getOverallStatus()) ? "success" : "warning");
        } catch (Exception e) {
            log.error("Ошибка планового полного обслуживания", e);
            notificationService.sendMaintenanceNotification(
                    "Ошибка полного обслуживания",
                    "Ошибка при выполнении полного обслуживания: " + e.getMessage(),
                    "error");
        }
    }
}
