package com.java.service.maintenance;

import com.java.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemHealthService {
    
    private final DataSource dataSource;
    
    @Value("${system.health.cpu.warning-threshold:80.0}")
    private double cpuWarningThreshold;
    
    @Value("${system.health.memory.warning-threshold:85.0}")
    private double memoryWarningThreshold;
    
    @Value("${system.health.disk.warning-threshold:90.0}")
    private double diskWarningThreshold;
    
    @Value("${system.health.database.connection-timeout:5000}")
    private long dbConnectionTimeout;
    
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    
    public SystemHealthDto checkSystemHealth() {
        log.info("Выполнение комплексной проверки состояния системы");
        
        SystemHealthDto health = new SystemHealthDto();
        health.setCheckTime(LocalDateTime.now());
        
        // Проверяем все компоненты
        DatabaseHealthDto dbHealth = checkDatabaseHealth();
        SystemResourcesDto resources = getSystemResources();
        
        Map<String, String> componentStatuses = new HashMap<>();
        componentStatuses.put("database", dbHealth.getConnectionStatus());
        componentStatuses.put("memory", resources.getMemoryUsagePercent() < memoryWarningThreshold ? "OK" : "WARNING");
        componentStatuses.put("disk", resources.getDiskUsagePercent() < diskWarningThreshold ? "OK" : "WARNING");
        
        // Рассчитываем общий статус
        boolean allHealthy = dbHealth.isConnectionActive() && 
                           resources.getMemoryUsagePercent() < memoryWarningThreshold &&
                           resources.getDiskUsagePercent() < diskWarningThreshold;
        
        health.setOverallStatus(allHealthy ? "HEALTHY" : "WARNING");
        health.setComponentStatuses(componentStatuses);
        health.setDatabaseHealthy(dbHealth.isConnectionActive());
        health.setSystemResourcesNormal(resources.getMemoryUsagePercent() < memoryWarningThreshold);
        
        // Рассчитываем оценку системы (0-100)
        double score = calculateSystemScore(dbHealth, resources);
        health.setSystemScore(score);
        
        health.setUptimeSeconds(runtimeBean.getUptime() / 1000);
        health.setFormattedUptime(formatUptime(health.getUptimeSeconds()));
        health.setRecommendation(generateRecommendation(dbHealth, resources));
        
        log.info("Проверка системы завершена. Общий статус: {}, Оценка: {}", 
                health.getOverallStatus(), health.getSystemScore());
        
        return health;
    }
    
    public DatabaseHealthDto checkDatabaseHealth() {
        log.debug("Проверка состояния базы данных");
        
        DatabaseHealthDto dbHealth = new DatabaseHealthDto();
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            long connectionTime = System.currentTimeMillis() - startTime;
            dbHealth.setConnectionTimeMs(connectionTime);
            dbHealth.setConnectionActive(true);
            dbHealth.setConnectionStatus("CONNECTED");
            dbHealth.setTransactionManagerHealthy(true);
            dbHealth.setLastSuccessfulQuery(LocalDateTime.now());
            
            // Получаем метаданные БД
            DatabaseMetaData metaData = connection.getMetaData();
            dbHealth.setDatabaseVersion(metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion());
            
            // Статистика подключений
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT count(*) as active_connections FROM pg_stat_activity WHERE state = 'active'")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbHealth.setActiveConnections(rs.getInt("active_connections"));
                }
            }
            
            // Размер БД
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT pg_database_size(current_database()) / (1024*1024) as size_mb")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbHealth.setDatabaseSizeMb(rs.getLong("size_mb"));
                }
            }
            
            dbHealth.setMaxConnections(100); // По умолчанию для PostgreSQL
            dbHealth.setConnectionPoolUsage((double) dbHealth.getActiveConnections() / dbHealth.getMaxConnections() * 100);
            
            log.debug("БД здорова. Время подключения: {}мс, активных подключений: {}", 
                     connectionTime, dbHealth.getActiveConnections());
            
        } catch (Exception e) {
            log.error("Ошибка при проверке БД: {}", e.getMessage());
            dbHealth.setConnectionActive(false);
            dbHealth.setConnectionStatus("ERROR: " + e.getMessage());
            dbHealth.setTransactionManagerHealthy(false);
            dbHealth.setConnectionTimeMs(System.currentTimeMillis() - startTime);
        }
        
        return dbHealth;
    }
    
    public SystemResourcesDto getSystemResources() {
        log.debug("Сбор информации о системных ресурсах");
        
        SystemResourcesDto resources = new SystemResourcesDto();
        resources.setMeasurementTime(LocalDateTime.now());
        
        // CPU
        double cpuUsage = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
        }
        resources.setCpuUsagePercent(cpuUsage > 0 ? cpuUsage : 0);
        
        // Память
        long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long freeMemory = totalMemory - usedMemory;
        
        resources.setTotalMemoryMb(totalMemory / (1024 * 1024));
        resources.setUsedMemoryMb(usedMemory / (1024 * 1024));
        resources.setFreeMemoryMb(freeMemory / (1024 * 1024));
        resources.setMemoryUsagePercent((double) usedMemory / totalMemory * 100);
        
        // Диск
        File workingDir = new File(System.getProperty("user.dir"));
        long totalSpace = workingDir.getTotalSpace();
        long freeSpace = workingDir.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;
        
        resources.setTotalDiskSpaceMb(totalSpace / (1024 * 1024));
        resources.setUsedDiskSpaceMb(usedSpace / (1024 * 1024));
        resources.setFreeDiskSpaceMb(freeSpace / (1024 * 1024));
        resources.setDiskUsagePercent((double) usedSpace / totalSpace * 100);
        
        // Форматированные значения
        resources.setFormattedTotalMemory(formatSize(resources.getTotalMemoryMb()));
        resources.setFormattedUsedMemory(formatSize(resources.getUsedMemoryMb()));
        resources.setFormattedTotalDisk(formatSize(resources.getTotalDiskSpaceMb()));
        resources.setFormattedFreeDisk(formatSize(resources.getFreeDiskSpaceMb()));
        
        log.debug("Ресурсы: CPU {}%, Память {}%, Диск {}%", 
                 String.format("%.1f", cpuUsage),
                 String.format("%.1f", resources.getMemoryUsagePercent()),
                 String.format("%.1f", resources.getDiskUsagePercent()));
        
        return resources;
    }
    
    public PerformanceMetricsDto getPerformanceMetrics() {
        log.debug("Сбор метрик производительности");
        
        PerformanceMetricsDto metrics = new PerformanceMetricsDto();
        metrics.setMeasurementStart(LocalDateTime.now().minusMinutes(5));
        metrics.setMeasurementEnd(LocalDateTime.now());
        metrics.setMeasurementDurationSeconds(300);
        
        // Информация о потоках
        int activeThreads = threadBean.getThreadCount();
        metrics.setActiveThreads(activeThreads);
        metrics.setThreadPoolUsagePercent((double) activeThreads / 50 * 100); // Примерный максимум
        
        // Базовые метрики (в реальном приложении будут собираться из метрик Spring)
        metrics.setAvgResponseTimeMs(150.0);
        metrics.setMaxResponseTimeMs(2500.0);
        metrics.setTotalRequests(1000L);
        metrics.setErrorCount(5L);
        metrics.setErrorRatePercent((double) metrics.getErrorCount() / metrics.getTotalRequests() * 100);
        metrics.setThroughputRequestsPerSecond((double) metrics.getTotalRequests() / metrics.getMeasurementDurationSeconds());
        
        // Метрики по эндпоинтам
        Map<String, Double> endpointTimes = new HashMap<>();
        endpointTimes.put("/clients", 120.0);
        endpointTimes.put("/import", 300.0);
        endpointTimes.put("/export", 250.0);
        metrics.setEndpointResponseTimes(endpointTimes);
        
        Map<String, Long> endpointCounts = new HashMap<>();
        endpointCounts.put("/clients", 400L);
        endpointCounts.put("/import", 200L);
        endpointCounts.put("/export", 150L);
        metrics.setEndpointRequestCounts(endpointCounts);
        
        log.debug("Метрики производительности собраны. Средний отклик: {}мс, Активных потоков: {}", 
                 metrics.getAvgResponseTimeMs(), activeThreads);
        
        return metrics;
    }
    
    public HealthCheckResultDto generateDiagnosticReport() {
        log.info("Генерация диагностического отчета системы");
        
        HealthCheckResultDto report = new HealthCheckResultDto();
        report.setComponentName("SYSTEM_OVERALL");
        report.setCheckTime(LocalDateTime.now());
        
        long startTime = System.currentTimeMillis();
        
        try {
            SystemHealthDto health = checkSystemHealth();
            SystemResourcesDto resources = getSystemResources();
            DatabaseHealthDto dbHealth = checkDatabaseHealth();
            
            boolean allHealthy = health.getOverallStatus().equals("HEALTHY");
            report.setHealthy(allHealthy);
            report.setStatus(health.getOverallStatus());
            report.setScore(health.getSystemScore());
            
            StringBuilder details = new StringBuilder();
            details.append("=== СИСТЕМНЫЙ ДИАГНОСТИЧЕСКИЙ ОТЧЕТ ===\n");
            details.append("Время проверки: ").append(health.getCheckTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("\n");
            details.append("Время работы: ").append(health.getFormattedUptime()).append("\n\n");
            
            details.append("--- БАЗА ДАННЫХ ---\n");
            details.append("Статус: ").append(dbHealth.getConnectionStatus()).append("\n");
            details.append("Время подключения: ").append(dbHealth.getConnectionTimeMs()).append("мс\n");
            details.append("Активных подключений: ").append(dbHealth.getActiveConnections()).append("\n\n");
            
            details.append("--- СИСТЕМНЫЕ РЕСУРСЫ ---\n");
            details.append("CPU: ").append(String.format("%.1f%%", resources.getCpuUsagePercent())).append("\n");
            details.append("Память: ").append(String.format("%.1f%% (%s из %s)", 
                    resources.getMemoryUsagePercent(), 
                    resources.getFormattedUsedMemory(), 
                    resources.getFormattedTotalMemory())).append("\n");
            details.append("Диск: ").append(String.format("%.1f%% (%s свободно)", 
                    resources.getDiskUsagePercent(), 
                    resources.getFormattedFreeDisk())).append("\n");
            
            report.setDetails(details.toString());
            report.setMessage(allHealthy ? "Система работает нормально" : "Обнаружены проблемы");
            report.setSeverity(allHealthy ? "INFO" : "WARNING");
            report.setRecommendation(health.getRecommendation());
            
            log.info("Диагностический отчет сгенерирован. Статус: {}, Оценка: {}", 
                    report.getStatus(), report.getScore());
            
        } catch (Exception e) {
            log.error("Ошибка генерации диагностического отчета: {}", e.getMessage());
            report.setHealthy(false);
            report.setStatus("ERROR");
            report.setMessage("Ошибка при генерации отчета: " + e.getMessage());
            report.setSeverity("ERROR");
            report.setScore(0.0);
        }
        
        report.setCheckDurationMs(System.currentTimeMillis() - startTime);
        return report;
    }
    
    private double calculateSystemScore(DatabaseHealthDto dbHealth, SystemResourcesDto resources) {
        double score = 100.0;
        
        // Штрафы за проблемы
        if (!dbHealth.isConnectionActive()) score -= 40.0;
        if (dbHealth.getConnectionTimeMs() > 1000) score -= 10.0;
        if (resources.getMemoryUsagePercent() > memoryWarningThreshold) score -= 20.0;
        if (resources.getDiskUsagePercent() > diskWarningThreshold) score -= 15.0;
        if (resources.getCpuUsagePercent() > cpuWarningThreshold) score -= 15.0;
        
        return Math.max(0.0, score);
    }
    
    private String generateRecommendation(DatabaseHealthDto dbHealth, SystemResourcesDto resources) {
        if (!dbHealth.isConnectionActive()) {
            return "КРИТИЧНО: Проблемы с подключением к базе данных. Проверьте конфигурацию и доступность PostgreSQL.";
        }
        
        if (resources.getMemoryUsagePercent() > memoryWarningThreshold) {
            return "ВНИМАНИЕ: Высокое использование памяти (" + 
                   String.format("%.1f%%", resources.getMemoryUsagePercent()) + 
                   "). Рассмотрите увеличение heap size или оптимизацию приложения.";
        }
        
        if (resources.getDiskUsagePercent() > diskWarningThreshold) {
            return "ВНИМАНИЕ: Заканчивается дисковое пространство (" + 
                   String.format("%.1f%%", resources.getDiskUsagePercent()) + 
                   "). Необходима очистка или расширение диска.";
        }
        
        if (dbHealth.getConnectionTimeMs() > 1000) {
            return "Медленное подключение к БД (" + dbHealth.getConnectionTimeMs() + 
                   "мс). Проверьте сетевую задержку и нагрузку на БД.";
        }
        
        return "Система работает в штатном режиме. Все компоненты функционируют нормально.";
    }
    
    private String formatUptime(long uptimeSeconds) {
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        
        if (days > 0) {
            return String.format("%d дн %d ч %d мин", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else {
            return String.format("%d мин", minutes);
        }
    }
    
    private String formatSize(long sizeMb) {
        if (sizeMb >= 1024) {
            return String.format("%.1f GB", sizeMb / 1024.0);
        } else {
            return sizeMb + " MB";
        }
    }
}