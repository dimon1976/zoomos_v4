package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для dashboard с агрегированной статистикой
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDashboardDto {

    private Long clientId;
    private String clientName;
    private ZonedDateTime generatedAt;
    private String period;
    
    // Топ группы
    private List<TopGroupDto> topGroups;
    
    // Дневные тренды
    private List<DailyTrendDto> dailyTrends;
    
    // Общие метрики
    private SummaryMetricsDto summary;
    
    // Статус кэша и производительности
    private String cacheStatus;
    private Long queryTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopGroupDto {
        private String groupValue;
        private Long totalCount;
        private Long recordCount;
        private Long dateModifications;
        private Double percentageOfTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendDto {
        private String date; // YYYY-MM-DD format
        private String groupValue;
        private Long dailyCount;
        private Double changeFromPrevious; // Процент изменения от предыдущего дня
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryMetricsDto {
        private Long totalOperations;
        private Long totalRecordsProcessed;
        private Long totalDateModifications;
        private Double averageRecordsPerOperation;
        private Double dateModificationRate; // Процент операций с изменениями дат
        
        // Тренды за период
        private String trendDirection; // UP, DOWN, STABLE
        private Double trendPercentage;
    }
}