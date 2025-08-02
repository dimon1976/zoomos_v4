package com.java.dto;

import com.java.model.enums.ExportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportSessionDto {

    private Long id;
    private Long fileOperationId;
    private Long templateId;
    private String templateName;
    private List<Long> sourceOperationIds;
    private String fileName;

    // Фильтры
    private ZonedDateTime dateFilterFrom;
    private ZonedDateTime dateFilterTo;
    private List<ExportTemplateFilterDto> appliedFilters;

    // Статистика
    private Long totalRows;
    private Long exportedRows;
    private Long filteredRows;
    private Long modifiedRows;

    // Статус
    private ExportStatus status;
    private String errorMessage;
    private Integer progressPercentage;

    // Результат
    private String resultFilePath;
    private Long fileSize;

    // Время
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private String duration;
}