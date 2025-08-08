package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportStatisticsDto {

    // Информация об операциях
    @Builder.Default
    private List<ExportOperationInfo> operations = new ArrayList<>();

    // Сгруппированные данные
    @Builder.Default
    private List<StatisticsRow> rows = new ArrayList<>();

    // Настройки
    private Integer deviationThreshold;
    private String groupByField;
    private List<String> metricFields;
    private boolean filterApplied;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportOperationInfo {
        private Long sessionId;
        private String fileName;
        private ZonedDateTime exportDate;
        private Long totalRows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsRow {
        private String groupValue;
        private List<MetricValue> metrics;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MetricValue {
            private Long sessionId;
            private Long count;
            private Double changePercent; // null для первой операции
            private ChangeType changeType;
        }
    }

    public enum ChangeType {
        INCREASE, DECREASE, NO_CHANGE, NO_DATA
    }
}