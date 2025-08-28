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
        private DateModificationStats dateModificationStats; // статистика изменений дат
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
        private DateModificationStats dateModificationStats; // статистика изменений дат для конкретного поля
    }

    public enum ChangeType {
        UP, DOWN, STABLE
    }

    public enum AlertLevel {
        NORMAL, WARNING, CRITICAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateModificationStats {
        private Long modifiedCount; // количество измененных дат
        private Long totalCount; // общее количество записей
        private Double modificationPercentage; // процент изменений
        private AlertLevel alertLevel; // уровень предупреждения для изменений дат
    }
}
