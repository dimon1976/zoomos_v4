package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для проверки здоровья системы статистики
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsHealthDto {

    private String overallStatus; // HEALTHY, DEGRADED, CRITICAL
    private ZonedDateTime checkedAt;
    private Long responseTimeMs;
    
    // Проверки по компонентам
    private DatabaseHealthDto database;
    private CacheHealthDto cache;
    private PerformanceHealthDto performance;
    
    // Сводная информация
    private List<HealthIssueDto> issues;
    private Map<String, Object> metrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseHealthDto {
        private String status; // HEALTHY, SLOW, ERROR
        private Long connectionTimeMs;
        private Long recentStatisticsCount;
        private ZonedDateTime lastStatisticsUpdate;
        private String indexStatus; // OPTIMAL, NEEDS_OPTIMIZATION
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHealthDto {
        private String status; // ACTIVE, INACTIVE, ERROR
        private Double hitRatio; // Процент попаданий в кэш
        private Integer totalCaches;
        private Integer activeCaches;
        private Long memoryUsageMb;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceHealthDto {
        private String status; // OPTIMAL, SLOW, CRITICAL
        private Double averageQueryTimeMs;
        private Double averageCacheTimeMs;
        private Long totalQueriesLast24h;
        private Long slowQueriesCount; // Запросы > 1 секунды
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthIssueDto {
        private String severity; // INFO, WARNING, CRITICAL
        private String component; // DATABASE, CACHE, PERFORMANCE
        private String description;
        private String recommendation;
        private ZonedDateTime detectedAt;
    }
}