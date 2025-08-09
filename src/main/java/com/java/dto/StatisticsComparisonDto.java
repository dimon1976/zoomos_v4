package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsComparisonDto {

    private String groupFieldValue; // Значение группировки
    private List<OperationStatistics> operations; // Статистика по операциям (отсортированная по дате)

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationStatistics {
        private Long exportSessionId;
        private String operationName;
        private java.time.ZonedDateTime exportDate;
        private Map<String, MetricValue> metrics; // поле -> значение с отклонением
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricValue {
        private Long currentValue;
        private Long previousValue;
        private Double changePercentage; // процент изменения
        private ChangeType changeType; // UP, DOWN, STABLE
        private AlertLevel alertLevel; // NORMAL, WARNING, CRITICAL
    }

    public enum ChangeType {
        UP, DOWN, STABLE
    }

    public enum AlertLevel {
        NORMAL, WARNING, CRITICAL
    }
}
