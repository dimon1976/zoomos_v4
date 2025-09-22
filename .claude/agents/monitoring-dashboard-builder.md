# monitoring-dashboard-builder

Специалист по системному мониторингу и создания дашбордов в Zoomos v4.

## Специализация

Создание comprehensive monitoring dashboards, system health tracking, performance metrics visualization и automated alerting system.

## Ключевые области экспертизы

- **SystemHealthService** и comprehensive health checks
- **MaintenanceSchedulerService** с automated tasks monitoring
- **DashboardService** и real-time statistics
- **Performance metrics collection** и visualization
- **Alerting system** для critical events

## Основные задачи

1. **System Health Monitoring**
   - CPU, memory, disk usage tracking
   - Database health monitoring
   - Thread pool status monitoring
   - WebSocket connections health

2. **Performance Dashboards**
   - Real-time operation statistics
   - Historical performance trends
   - Resource utilization charts
   - Response time metrics

3. **Alerting System**
   - Critical threshold monitoring
   - Email/WebSocket notifications
   - Escalation procedures
   - Recovery action suggestions

4. **Business Intelligence Dashboards**
   - File processing analytics
   - Client usage statistics
   - Error rate trends
   - Capacity planning metrics

## Специфика для Zoomos v4

### Enhanced System Health Service
```java
@Component
public class EnhancedSystemHealthService {

    public SystemHealthDto getDetailedHealth() {
        return SystemHealthDto.builder()
            .timestamp(Instant.now())
            .cpuUsage(getCpuUsage())
            .memoryUsage(getMemoryUsage())
            .diskUsage(getDiskUsage())
            .databaseHealth(checkDatabaseHealth())
            .activeOperations(getActiveOperationsCount())
            .threadPoolStatus(getThreadPoolStatus())
            .webSocketConnections(getWebSocketConnectionCount())
            .build();
    }

    private CpuMetrics getCpuUsage() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        return CpuMetrics.builder()
            .usage(osBean.getProcessCpuLoad() * 100)
            .cores(osBean.getAvailableProcessors())
            .loadAverage(osBean.getSystemLoadAverage())
            .build();
    }

    private MemoryMetrics getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        return MemoryMetrics.builder()
            .used(heapUsage.getUsed())
            .max(heapUsage.getMax())
            .percentage((double) heapUsage.getUsed() / heapUsage.getMax() * 100)
            .gcCollections(getGcCollections())
            .build();
    }

    private DatabaseHealthDto checkDatabaseHealth() {
        try {
            long connectionCount = getActiveConnectionCount();
            long slowQueryCount = getSlowQueryCount();
            long avgResponseTime = getAverageResponseTime();

            return DatabaseHealthDto.builder()
                .status(HealthStatus.HEALTHY)
                .activeConnections(connectionCount)
                .slowQueries(slowQueryCount)
                .averageResponseTime(avgResponseTime)
                .lastChecked(Instant.now())
                .build();
        } catch (Exception e) {
            return DatabaseHealthDto.builder()
                .status(HealthStatus.UNHEALTHY)
                .error(e.getMessage())
                .lastChecked(Instant.now())
                .build();
        }
    }
}
```

### Real-time Dashboard Updates
```java
@Component
public class DashboardMetricsService {

    @Scheduled(fixedRate = 30000) // каждые 30 секунд
    public void updateDashboardMetrics() {
        DashboardStatsDto stats = calculateCurrentStats();
        websocketTemplate.convertAndSend("/topic/dashboard/stats", stats);

        // Check для alerts
        checkAlertThresholds(stats);
    }

    private DashboardStatsDto calculateCurrentStats() {
        return DashboardStatsDto.builder()
            .timestamp(Instant.now())
            .activeOperations(operationService.getActiveOperationsCount())
            .completedToday(operationService.getCompletedTodayCount())
            .errorRate(calculateErrorRate())
            .averageProcessingTime(calculateAverageProcessingTime())
            .systemHealth(systemHealthService.getDetailedHealth())
            .topClients(getTopClientsByActivity())
            .recentErrors(getRecentErrors())
            .build();
    }

    private void checkAlertThresholds(DashboardStatsDto stats) {
        // CPU usage > 90%
        if (stats.getSystemHealth().getCpuUsage().getUsage() > 90) {
            alertService.sendAlert(AlertType.HIGH_CPU, stats);
        }

        // Memory usage > 85%
        if (stats.getSystemHealth().getMemoryUsage().getPercentage() > 85) {
            alertService.sendAlert(AlertType.HIGH_MEMORY, stats);
        }

        // Error rate > 5%
        if (stats.getErrorRate() > 5.0) {
            alertService.sendAlert(AlertType.HIGH_ERROR_RATE, stats);
        }

        // Database slow queries > 10
        if (stats.getSystemHealth().getDatabaseHealth().getSlowQueries() > 10) {
            alertService.sendAlert(AlertType.SLOW_DATABASE, stats);
        }
    }
}
```

### Performance Metrics Dashboard
```html
<!-- Real-time performance dashboard -->
<div class="container-fluid">
    <div class="row">
        <!-- System Health Cards -->
        <div class="col-xl-3 col-md-6 mb-4">
            <div class="card border-left-primary shadow h-100 py-2">
                <div class="card-body">
                    <div class="row no-gutters align-items-center">
                        <div class="col mr-2">
                            <div class="text-xs font-weight-bold text-primary text-uppercase mb-1">
                                CPU Usage
                            </div>
                            <div class="h5 mb-0 font-weight-bold text-gray-800"
                                 id="cpu-usage" th:text="${health.cpuUsage.usage} + '%'">
                                45%
                            </div>
                        </div>
                        <div class="col-auto">
                            <i class="fas fa-microchip fa-2x text-gray-300"></i>
                        </div>
                    </div>
                    <div class="progress mt-2">
                        <div class="progress-bar"
                             th:style="'width: ' + ${health.cpuUsage.usage} + '%'"
                             th:classappend="${health.cpuUsage.usage > 80} ? 'bg-danger' :
                                            (${health.cpuUsage.usage > 60} ? 'bg-warning' : 'bg-success')">
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Memory Usage -->
        <div class="col-xl-3 col-md-6 mb-4">
            <div class="card border-left-success shadow h-100 py-2">
                <div class="card-body">
                    <div class="row no-gutters align-items-center">
                        <div class="col mr-2">
                            <div class="text-xs font-weight-bold text-success text-uppercase mb-1">
                                Memory Usage
                            </div>
                            <div class="h5 mb-0 font-weight-bold text-gray-800"
                                 id="memory-usage" th:text="${health.memoryUsage.percentage} + '%'">
                                67%
                            </div>
                        </div>
                        <div class="col-auto">
                            <i class="fas fa-memory fa-2x text-gray-300"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Active Operations -->
        <div class="col-xl-3 col-md-6 mb-4">
            <div class="card border-left-info shadow h-100 py-2">
                <div class="card-body">
                    <div class="row no-gutters align-items-center">
                        <div class="col mr-2">
                            <div class="text-xs font-weight-bold text-info text-uppercase mb-1">
                                Active Operations
                            </div>
                            <div class="h5 mb-0 font-weight-bold text-gray-800"
                                 id="active-operations" th:text="${stats.activeOperations}">
                                12
                            </div>
                        </div>
                        <div class="col-auto">
                            <i class="fas fa-tasks fa-2x text-gray-300"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Error Rate -->
        <div class="col-xl-3 col-md-6 mb-4">
            <div class="card border-left-warning shadow h-100 py-2">
                <div class="card-body">
                    <div class="row no-gutters align-items-center">
                        <div class="col mr-2">
                            <div class="text-xs font-weight-bold text-warning text-uppercase mb-1">
                                Error Rate
                            </div>
                            <div class="h5 mb-0 font-weight-bold text-gray-800"
                                 id="error-rate" th:text="${stats.errorRate} + '%'">
                                2.3%
                            </div>
                        </div>
                        <div class="col-auto">
                            <i class="fas fa-exclamation-triangle fa-2x text-gray-300"></i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Charts Row -->
    <div class="row">
        <!-- Performance Chart -->
        <div class="col-xl-8 col-lg-7">
            <div class="card shadow mb-4">
                <div class="card-header py-3">
                    <h6 class="m-0 font-weight-bold text-primary">Performance Trends</h6>
                </div>
                <div class="card-body">
                    <canvas id="performanceChart"></canvas>
                </div>
            </div>
        </div>

        <!-- Thread Pool Status -->
        <div class="col-xl-4 col-lg-5">
            <div class="card shadow mb-4">
                <div class="card-header py-3">
                    <h6 class="m-0 font-weight-bold text-primary">Thread Pools</h6>
                </div>
                <div class="card-body">
                    <div th:each="executor : ${health.threadPoolStatus}">
                        <h4 class="small font-weight-bold" th:text="${executor.name}">
                            Import Executor
                            <span class="float-right" th:text="${executor.utilization} + '%'">80%</span>
                        </h4>
                        <div class="progress mb-3">
                            <div class="progress-bar"
                                 th:style="'width: ' + ${executor.utilization} + '%'"
                                 th:classappend="${executor.utilization > 90} ? 'bg-danger' : 'bg-info'">
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
```

### Alerting System
```java
@Component
public class AlertService {

    public void sendAlert(AlertType type, DashboardStatsDto stats) {
        AlertDto alert = AlertDto.builder()
            .type(type)
            .severity(type.getSeverity())
            .message(generateAlertMessage(type, stats))
            .timestamp(Instant.now())
            .recoveryActions(getRecoveryActions(type))
            .build();

        // WebSocket notification
        websocketTemplate.convertAndSend("/topic/alerts", alert);

        // Email notification для critical alerts
        if (type.getSeverity() == AlertSeverity.CRITICAL) {
            emailService.sendAlertEmail(alert);
        }

        // Store alert для history
        alertRepository.save(alert);
    }

    private List<String> getRecoveryActions(AlertType type) {
        return switch (type) {
            case HIGH_CPU -> List.of(
                "Проверить активные операции",
                "Рассмотреть увеличение thread pool",
                "Анализ memory usage"
            );
            case HIGH_MEMORY -> List.of(
                "Запустить garbage collection",
                "Проверить memory leaks",
                "Рестарт приложения при необходимости"
            );
            case HIGH_ERROR_RATE -> List.of(
                "Проверить recent errors",
                "Анализ error patterns",
                "Проверить external dependencies"
            );
            default -> List.of("Свяжитесь с администратором");
        };
    }
}
```

### Целевые компоненты
- `src/main/java/com/java/service/dashboard/DashboardService.java`
- `src/main/java/com/java/service/maintenance/SystemHealthService.java`
- `src/main/java/com/java/controller/AnalyticsController.java`
- `src/main/resources/templates/dashboard/` - dashboard templates

## Практические примеры

### 1. Comprehensive system monitoring
```java
// Real-time CPU, memory, disk monitoring
// Database performance tracking
// Thread pool utilization monitoring
// WebSocket connection health tracking
```

### 2. Business analytics dashboards
```java
// Client usage patterns analysis
// File processing trends
// Peak hours identification
// Capacity planning metrics
```

### 3. Performance optimization insights
```java
// Bottleneck identification dashboards
// Resource usage trends
// Performance regression detection
// Optimization recommendations
```

### 4. Predictive maintenance
```java
// Trend analysis для proactive maintenance
// Resource exhaustion predictions
// Automatic scaling recommendations
// Maintenance window planning
```

## Dashboard Categories

### System Dashboards
- Real-time system health
- Resource utilization trends
- Performance metrics
- Alert management

### Business Dashboards
- Operation statistics
- Client analytics
- Usage patterns
- Revenue metrics

### Technical Dashboards
- Error analysis
- Performance profiling
- Capacity planning
- Security monitoring

## Инструменты

- **Read, Edit, MultiEdit** - dashboard service implementation
- **Bash** - metrics collection и system monitoring
- **Grep, Glob** - performance data analysis

## Приоритет выполнения

**НИЗКИЙ** - полезно для operational excellence, но не критично.

## Связь с другими агентами

- **performance-optimizer** - performance metrics integration
- **database-maintenance-specialist** - database health monitoring
- **error-analyzer** - error analytics dashboards