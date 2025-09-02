package com.java.service;

import com.java.service.maintenance.FileManagementService;
import com.java.service.maintenance.DatabaseMaintenanceService;
import com.java.service.maintenance.SystemHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Сервис автоматизации обслуживания системы
 * Выполняет регулярные операции по расписанию
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "maintenance.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class MaintenanceSchedulerService {

    private final FileManagementService fileManagementService;
    private final DatabaseMaintenanceService databaseMaintenanceService;
    private final SystemHealthService systemHealthService;
    private final MaintenanceNotificationService notificationService;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Ежедневное архивирование старых файлов
     * Выполняется каждый день в 02:00
     */
    @Scheduled(cron = "${maintenance.scheduler.file-archive.cron:0 0 2 * * *}")
    public void scheduleFileArchiving() {
        log.info("Запуск планового архивирования файлов: {}", LocalDateTime.now().format(FORMATTER));
        
        try {
            var result = fileManagementService.archiveOldFiles(30);
            
            if (result.isSuccess()) {
                log.info("Плановое архивирование завершено: архивировано {} файлов, размер: {}", 
                    result.getArchivedFiles(), result.getFormattedArchivedSize());
                
                notificationService.sendMaintenanceNotification(
                    "Архивирование файлов", 
                    String.format("Успешно архивировано %d файлов (%s)", 
                        result.getArchivedFiles(), result.getFormattedArchivedSize()),
                    "success"
                );
            } else {
                log.info("Плановое архивирование: файлы для архивирования не найдены");
            }
        } catch (Exception e) {
            log.error("Ошибка планового архивирования файлов", e);
            notificationService.sendMaintenanceNotification(
                "Ошибка архивирования", 
                "Ошибка при выполнении планового архивирования: " + e.getMessage(),
                "error"
            );
        }
    }
    
    /**
     * Еженедельная очистка базы данных
     * Выполняется каждое воскресенье в 03:00
     */
    @Scheduled(cron = "${maintenance.scheduler.database-cleanup.cron:0 0 3 * * SUN}")
    public void scheduleDatabaseCleanup() {
        log.info("Запуск плановой очистки БД: {}", LocalDateTime.now().format(FORMATTER));
        
        try {
            var result = databaseMaintenanceService.cleanupOldData();
            
            if (result.isSuccess()) {
                int totalDeleted = result.getDeletedImportSessions() + result.getDeletedExportSessions() + 
                                 result.getDeletedFileOperations() + result.getDeletedOrphanedRecords();
                
                log.info("Плановая очистка БД завершена: удалено {} записей, освобождено {}", 
                    totalDeleted, result.getFormattedFreedSpace());
                
                notificationService.sendMaintenanceNotification(
                    "Очистка базы данных", 
                    String.format("Успешно удалено %d записей, освобождено %s", 
                        totalDeleted, result.getFormattedFreedSpace()),
                    "success"
                );
            } else {
                log.info("Плановая очистка БД: данные для очистки не найдены");
            }
        } catch (Exception e) {
            log.error("Ошибка плановой очистки БД", e);
            notificationService.sendMaintenanceNotification(
                "Ошибка очистки БД", 
                "Ошибка при выполнении плановой очистки БД: " + e.getMessage(),
                "error"
            );
        }
    }
    
    /**
     * Ежечасная проверка состояния системы
     * Выполняется каждый час
     */
    @Scheduled(cron = "${maintenance.scheduler.health-check.cron:0 0 * * * *}")
    public void scheduleHealthCheck() {
        log.debug("Выполнение плановой проверки состояния системы: {}", LocalDateTime.now().format(FORMATTER));
        
        try {
            var health = systemHealthService.checkSystemHealth();
            
            // Отправляем уведомления только при проблемах
            if (!"HEALTHY".equals(health.getOverallStatus())) {
                log.warn("Обнаружены проблемы в системе: статус = {}, оценка = {}", 
                    health.getOverallStatus(), health.getSystemScore());
                
                notificationService.sendMaintenanceNotification(
                    "Предупреждение системы", 
                    String.format("Состояние: %s, Оценка: %.1f, Рекомендация: %s", 
                        health.getOverallStatus(), health.getSystemScore(), health.getRecommendation()),
                    "warning"
                );
            }
            
            // Проверяем критические ресурсы
            var resources = systemHealthService.getSystemResources();
            if (resources.getMemoryUsagePercent() > 90) {
                notificationService.sendMaintenanceNotification(
                    "Критическая нагрузка", 
                    String.format("Использование памяти: %.1f%% (критический уровень)", resources.getMemoryUsagePercent()),
                    "error"
                );
            } else if (resources.getMemoryUsagePercent() > 80) {
                notificationService.sendMaintenanceNotification(
                    "Высокая нагрузка", 
                    String.format("Использование памяти: %.1f%% (высокий уровень)", resources.getMemoryUsagePercent()),
                    "warning"
                );
            }
            
            if (resources.getDiskUsagePercent() > 95) {
                notificationService.sendMaintenanceNotification(
                    "Критическая заполненность диска", 
                    String.format("Использование диска: %.1f%% (критический уровень)", resources.getDiskUsagePercent()),
                    "error"
                );
            }
            
        } catch (Exception e) {
            log.error("Ошибка плановой проверки состояния системы", e);
            notificationService.sendMaintenanceNotification(
                "Ошибка мониторинга", 
                "Ошибка при выполнении проверки состояния системы: " + e.getMessage(),
                "error"
            );
        }
    }
    
    /**
     * Еженедельный анализ производительности БД
     * Выполняется каждый понедельник в 01:00
     */
    @Scheduled(cron = "${maintenance.scheduler.performance-analysis.cron:0 0 1 * * MON}")
    public void schedulePerformanceAnalysis() {
        log.info("Запуск планового анализа производительности БД: {}", LocalDateTime.now().format(FORMATTER));
        
        try {
            var slowQueries = databaseMaintenanceService.analyzeQueryPerformance();
            
            if (!slowQueries.isEmpty()) {
                int slowQueryCount = (int) slowQueries.stream()
                    .mapToInt(q -> q.isSlowQuery() ? 1 : 0)
                    .sum();
                
                if (slowQueryCount > 0) {
                    log.warn("Обнаружено {} медленных запросов в системе", slowQueryCount);
                    
                    notificationService.sendMaintenanceNotification(
                        "Анализ производительности", 
                        String.format("Обнаружено %d медленных запросов. Рекомендуется оптимизация.", slowQueryCount),
                        "warning"
                    );
                } else {
                    log.info("Анализ производительности БД: проблем не обнаружено");
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка планового анализа производительности БД", e);
            notificationService.sendMaintenanceNotification(
                "Ошибка анализа производительности", 
                "Ошибка при выполнении анализа производительности БД: " + e.getMessage(),
                "error"
            );
        }
    }
    
    /**
     * Ежемесячное полное обслуживание
     * Выполняется первого числа каждого месяца в 04:00
     */
    @Scheduled(cron = "${maintenance.scheduler.full-maintenance.cron:0 0 4 1 * *}")
    public void scheduleFullMaintenance() {
        log.info("Запуск планового полного обслуживания системы: {}", LocalDateTime.now().format(FORMATTER));
        
        try {
            // Архивирование файлов
            var fileResult = fileManagementService.archiveOldFiles(30);
            
            // Очистка БД
            var dbResult = databaseMaintenanceService.cleanupOldData();
            
            // Проверка системы
            var health = systemHealthService.checkSystemHealth();
            
            StringBuilder report = new StringBuilder();
            report.append("Полное обслуживание системы завершено:\n");
            
            if (fileResult.isSuccess()) {
                report.append(String.format("• Архивировано файлов: %d (%s)\n", 
                    fileResult.getArchivedFiles(), fileResult.getFormattedArchivedSize()));
            } else {
                report.append("• Файлы для архивирования не найдены\n");
            }
            
            if (dbResult.isSuccess()) {
                int totalDeleted = dbResult.getDeletedImportSessions() + dbResult.getDeletedExportSessions() + 
                                 dbResult.getDeletedFileOperations() + dbResult.getDeletedOrphanedRecords();
                report.append(String.format("• Очищено БД: %d записей (%s)\n", 
                    totalDeleted, dbResult.getFormattedFreedSpace()));
            } else {
                report.append("• БД: данные для очистки не найдены\n");
            }
            
            report.append(String.format("• Состояние системы: %s (%.1f)", 
                health.getOverallStatus(), health.getSystemScore()));
            
            log.info("Плановое полное обслуживание завершено");
            
            notificationService.sendMaintenanceNotification(
                "Полное обслуживание системы", 
                report.toString(),
                "HEALTHY".equals(health.getOverallStatus()) ? "success" : "warning"
            );
            
        } catch (Exception e) {
            log.error("Ошибка планового полного обслуживания", e);
            notificationService.sendMaintenanceNotification(
                "Ошибка полного обслуживания", 
                "Ошибка при выполнении полного обслуживания: " + e.getMessage(),
                "error"
            );
        }
    }
}