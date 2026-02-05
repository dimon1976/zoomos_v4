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
    private Long tempDirSizeBytes;
    private String tempDirSizeFormatted;
    private Long importDirSizeBytes;
    private String importDirSizeFormatted;
    private Long exportDirSizeBytes;
    private String exportDirSizeFormatted;
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

    // Расширенная аналитика
    private Long totalAvDataRecords; // Общее количество записей AvData
    private Long errorOperations; // Количество операций с ошибками
    private Double avgRecordsPerOperation; // Среднее количество записей на операцию
    
    // Статистика по времени
    private Double avgProcessingTimeSeconds; // В секундах для более точности
    private Long operationsLastHour;
    private Long operationsLast24Hours;
    
    // Статистика файлов по типам
    private Long csvFilesProcessed;
    private Long xlsxFilesProcessed;
    private Long xlsFilesProcessed;
    
    // Качество данных
    private Double dataQualityScore; // Процент успешных операций от 0 до 100
    private Long duplicateRecords; // Количество дублирующихся записей
    private Long incompleteRecords; // Записи с незаполненными обязательными полями

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemInfoDto {
        private String javaVersion;
        private String springBootVersion;

        // JVM Heap память (МБ) - с точностью до сотых
        private Double jvmTotalMemoryMb;
        private Double jvmUsedMemoryMb;
        private Double jvmFreeMemoryMb;

        // Системная память ПК (ОЗУ) (ГБ) - с точностью до сотых
        private Double systemTotalMemoryGb;
        private Double systemUsedMemoryGb;
        private Double systemFreeMemoryGb;

        private String operatingSystem;
        private String databaseUrl;
        private Long uptimeMinutes;
    }
}