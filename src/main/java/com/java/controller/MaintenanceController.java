package com.java.controller;

import com.java.dto.*;
import com.java.service.maintenance.DatabaseMaintenanceService;
import com.java.service.maintenance.FileManagementService;
import com.java.service.maintenance.SystemHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Контроллер для системы обслуживания и мониторинга
 */
@Controller
@RequestMapping("/maintenance")
@RequiredArgsConstructor
@Slf4j
public class MaintenanceController {

    private final FileManagementService fileManagementService;
    private final DatabaseMaintenanceService databaseMaintenanceService; 
    private final SystemHealthService systemHealthService;

    // === ГЛАВНАЯ СТРАНИЦА ОБСЛУЖИВАНИЯ ===
    
    @GetMapping
    public String index(Model model) {
        log.debug("GET request to maintenance page");
        model.addAttribute("pageTitle", "Система обслуживания");
        return "maintenance/index";
    }
    
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Map<String, String>> indexApi() {
        log.debug("GET request to maintenance API");
        Map<String, String> response = new HashMap<>();
        response.put("status", "active");
        response.put("message", "Система обслуживания готова к работе");
        response.put("endpoints", "/files, /database, /system, /cleanup");
        return ResponseEntity.ok(response);
    }
    
    // === HTML СТРАНИЦЫ ===
    
    @GetMapping("/files")
    public String filesPage(Model model) {
        log.debug("GET request to files management page");
        model.addAttribute("pageTitle", "Управление файлами");
        return "maintenance/files";
    }
    
    @GetMapping("/database")  
    public String databasePage(Model model) {
        log.debug("GET request to database maintenance page");
        model.addAttribute("pageTitle", "Обслуживание БД");
        return "maintenance/database";
    }
    
    @GetMapping("/system")
    public String systemPage(Model model) {
        log.debug("GET request to system health page");
        model.addAttribute("pageTitle", "Диагностика системы");
        return "maintenance/system";
    }
    
    @GetMapping("/operations")
    public String operationsPage(Model model) {
        log.debug("GET request to manual operations page");
        model.addAttribute("pageTitle", "Ручные операции");
        return "maintenance/operations";
    }
    
    // === FILE MANAGEMENT ENDPOINTS ===
    
    @GetMapping("/files/stats")
    @ResponseBody
    public ResponseEntity<List<DirectoryStatsDto>> getFileStats() {
        log.info("Запрос статистики файлов через API");
        try {
            List<DirectoryStatsDto> stats = fileManagementService.analyzeDiskSpace();
            log.debug("Возвращено {} записей статистики файлов", stats.size());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка получения статистики файлов: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/files/archive")
    @ResponseBody
    public ResponseEntity<ArchiveResultDto> archiveFiles() {
        log.info("Запуск архивирования файлов через API");
        try {
            ArchiveResultDto result = fileManagementService.archiveOldFiles(30);
            log.info("Архивирование завершено. Обработано файлов: {}", result.getArchivedFiles());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка архивирования файлов: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/files/duplicates")
    @ResponseBody
    public ResponseEntity<List<DuplicateFileDto>> findDuplicates() {
        log.info("Поиск дублей файлов через API");
        try {
            List<DuplicateFileDto> duplicates = fileManagementService.findDuplicateFiles();
            log.debug("Найдено {} дублей файлов", duplicates.size());
            return ResponseEntity.ok(duplicates);
        } catch (Exception e) {
            log.error("Ошибка поиска дублей файлов: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // === DATABASE MAINTENANCE ENDPOINTS ===
    
    @GetMapping("/database/stats")
    @ResponseBody
    public ResponseEntity<DatabaseStatsDto> getDatabaseStats() {
        log.info("Запрос статистики БД через API");
        try {
            DatabaseStatsDto stats = databaseMaintenanceService.getDatabaseStats();
            log.debug("Статистика БД получена. Размер: {}", stats.getFormattedTotalSize());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка получения статистики БД: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/database/cleanup")
    @ResponseBody
    public ResponseEntity<DatabaseCleanupResultDto> cleanupDatabase() {
        log.info("Запуск очистки БД через API");
        try {
            DatabaseCleanupResultDto result = databaseMaintenanceService.cleanupOldData();
            int totalDeleted = result.getDeletedImportSessions() + result.getDeletedExportSessions() + 
                               result.getDeletedFileOperations() + result.getDeletedOrphanedRecords();
            log.info("Очистка БД завершена. Удалено записей: {}", totalDeleted);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка очистки БД: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/database/performance")
    @ResponseBody
    public ResponseEntity<List<QueryPerformanceDto>> getDatabasePerformance() {
        log.info("Анализ производительности БД через API");
        try {
            List<QueryPerformanceDto> performance = databaseMaintenanceService.analyzeQueryPerformance();
            log.debug("Анализ производительности завершен. Проанализировано запросов: {}", performance.size());
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            log.error("Ошибка анализа производительности БД: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // === SYSTEM HEALTH ENDPOINTS ===
    
    @GetMapping("/system/health")
    @ResponseBody
    public ResponseEntity<SystemHealthDto> getSystemHealth() {
        log.info("Проверка состояния системы через API");
        try {
            SystemHealthDto health = systemHealthService.checkSystemHealth();
            log.debug("Проверка системы завершена. Статус: {}, Оценка: {}", 
                     health.getOverallStatus(), health.getSystemScore());
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Ошибка проверки состояния системы: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/system/resources")
    @ResponseBody
    public ResponseEntity<SystemResourcesDto> getSystemResources() {
        log.info("Мониторинг ресурсов системы через API");
        try {
            SystemResourcesDto resources = systemHealthService.getSystemResources();
            log.debug("Мониторинг ресурсов завершен. CPU: {}%, Память: {}%", 
                     String.format("%.1f", resources.getCpuUsagePercent()),
                     String.format("%.1f", resources.getMemoryUsagePercent()));
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            log.error("Ошибка мониторинга ресурсов системы: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/system/report")
    @ResponseBody
    public ResponseEntity<HealthCheckResultDto> getSystemReport() {
        log.info("Генерация диагностического отчета через API");
        try {
            HealthCheckResultDto report = systemHealthService.generateDiagnosticReport();
            log.info("Диагностический отчет сгенерирован. Статус: {}, Время проверки: {}мс", 
                    report.getStatus(), report.getCheckDurationMs());
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Ошибка генерации диагностического отчета: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // === ОБЩИЙ CLEANUP ENDPOINT ===
    
    @PostMapping("/cleanup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performFullCleanup() {
        log.info("Запуск полной очистки системы через API");
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Очистка файлов
            ArchiveResultDto fileResult = fileManagementService.archiveOldFiles(30);
            result.put("files", Map.of(
                "archivedFiles", fileResult.getArchivedFiles(),
                "freedSpaceMb", fileResult.getTotalArchivedSizeBytes() / (1024 * 1024),
                "status", "success"
            ));
            
            // Очистка БД
            DatabaseCleanupResultDto dbResult = databaseMaintenanceService.cleanupOldData();
            int totalDeletedRecords = dbResult.getDeletedImportSessions() + dbResult.getDeletedExportSessions() + 
                                     dbResult.getDeletedFileOperations() + dbResult.getDeletedOrphanedRecords();
            result.put("database", Map.of(
                "deletedRecords", totalDeletedRecords,
                "freedSpaceMb", dbResult.getFreedSpaceBytes() / (1024 * 1024),
                "status", "success"
            ));
            
            // Проверка системы после очистки
            SystemHealthDto health = systemHealthService.checkSystemHealth();
            result.put("systemHealth", Map.of(
                "status", health.getOverallStatus(),
                "score", health.getSystemScore(),
                "recommendation", health.getRecommendation()
            ));
            
            result.put("overall", Map.of(
                "status", "success",
                "message", "Полная очистка системы завершена успешно"
            ));
            
            log.info("Полная очистка завершена. Архивировано файлов: {}, Удалено записей БД: {}", 
                    fileResult.getArchivedFiles(), totalDeletedRecords);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Ошибка полной очистки системы: {}", e.getMessage());
            result.put("overall", Map.of(
                "status", "error",
                "message", "Ошибка при выполнении полной очистки: " + e.getMessage()
            ));
            return ResponseEntity.internalServerError().body(result);
        }
    }
}