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
public class DashboardOperationDto {

    private Long id;
    private String operationType;
    private String operationTypeDisplay;
    private String fileName;
    private String fileType;
    private String status;
    private String statusDisplay;

    // Информация о клиенте
    private Long clientId;
    private String clientName;

    // Прогресс и статистика
    private Integer recordCount;
    private Integer totalRecords;
    private Integer processedRecords;
    private Integer processingProgress;

    // Файловая информация
    private Long fileSize;
    private String fileSizeFormatted;

    // Временные метки
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private String duration;

    // Ошибки
    private String errorMessage;
    private Boolean hasErrors;

    // Дополнительная информация
    private String sourceFilePath;
    private String resultFilePath;
}