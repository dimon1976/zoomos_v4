package com.java.dto;

import com.java.model.enums.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSessionDto {

    private Long id;
    private Long fileOperationId;
    private Long templateId;
    private String templateName;
    private String fileName;
    private Long totalRows;
    private Long processedRows;
    private Long successRows;
    private Long errorRows;
    private Long duplicateRows;
    private ImportStatus status;
    private String errorMessage;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private Integer progressPercentage;
    private String estimatedTimeRemaining;
    private Boolean isCancelled;
}