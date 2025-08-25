package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataDto {
    
    private List<TimeSeriesPoint> operationsTrend;
    private List<TimeSeriesPoint> recordsTrend;
    private List<TimeSeriesPoint> errorsTrend;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private LocalDate date;
        private String label; // Человекочитаемая дата
        private Long value;
        private String category; // Для группировки (импорт/экспорт)
    }
}