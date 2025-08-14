package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    // Общие статистики
    private Long totalOperations;
    private Long totalClients;
    private Long activeOperations; // PENDING + PROCESSING
    private Long completedOperations;
    private Long failedOperations;

    // Статистика по типам операций
    private Long importOperations;
    private Long exportOperations;
    private Long processOperations;

    // Статистика по файлам
    private Long totalFilesProcessed;
    private Long totalFileSizeBytes;
    private String totalFileSizeFormatted;
    private Long totalRecordsProcessed;

    // Статистика за период
    private Long operationsToday;
    private Long operationsThisWeek;
    private Long operationsThisMonth;

    // Производительность
    private Double averageProcessingTimeMinutes;
    private Double successRate; // процент успешных операций

    // Последние обновления
    private ZonedDateTime lastUpdateTime;
    private String topClientByOperations;
    private String mostUsedFileType;

    // Системная информация
    private SystemInfoDto systemInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemInfoDto {
        private String javaVersion;
        private String springBootVersion;
        private Long totalMemoryMb;
        private Long usedMemoryMb;
        private Long freeMemoryMb;
        private String operatingSystem;
        private String databaseUrl;
        private Long uptimeMinutes;
    }
}