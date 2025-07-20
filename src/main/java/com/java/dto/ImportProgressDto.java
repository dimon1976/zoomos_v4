package com.java.dto;

import com.java.model.enums.ImportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProgressDto {

    private Long sessionId;
    private ImportStatus status;
    private Long totalRows;
    private Long processedRows;
    private Long successRows;
    private Long errorRows;
    private Integer progressPercentage;
    private String currentOperation;
    private String estimatedTimeRemaining;
    private Boolean isCompleted;

    // Для WebSocket обновлений
    private String updateType; // PROGRESS, STATUS_CHANGE, ERROR, COMPLETED
    private String message;
    private Long timestamp;
}
