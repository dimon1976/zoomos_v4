package com.java.dto;

import com.java.model.enums.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportStatisticsDto {

    private Long templateId;
    private String templateName;

    // Общая статистика
    private Long totalImports;
    private Long successfulImports;
    private Long failedImports;
    private Long totalRowsProcessed;
    private Long totalErrors;

    // Средние показатели
    private Double averageRowsPerImport;
    private Double averageProcessingTimeSeconds;
    private Double successRate;

    // Последние операции
    private ZonedDateTime lastImportDate;
    private ImportStatus lastImportStatus;

    // Распределение ошибок по типам
    private Map<String, Long> errorDistribution;
}