package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * DTO для передачи исторических данных статистики для построения графиков
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsHistoryDto {

    private String groupValue;      // Значение группы
    private String metricName;      // Название метрики
    private List<DataPoint> dataPoints;  // Исторические точки данных
    private TrendInfo trendInfo;    // Информация о тренде

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private ZonedDateTime date;     // Дата операции экспорта
        private Long value;             // Значение метрики
        private Long exportSessionId;   // ID сессии экспорта
        private String operationName;   // Название операции
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendInfo {
        private TrendDirection direction;  // Направление тренда
        private Double slope;              // Наклон линии тренда (коэффициент регрессии)
        private Double confidence;         // Коэффициент уверенности (R²)
        private Double changePercentage;   // Процент изменения за период
        private String description;        // Текстовое описание тренда
    }

    public enum TrendDirection {
        STRONG_GROWTH,    // Сильный рост (>10%)
        GROWTH,           // Рост (5-10%)
        STABLE,           // Стабильность (±5%)
        DECLINE,          // Снижение (-5% до -10%)
        STRONG_DECLINE    // Сильное снижение (<-10%)
    }
}
