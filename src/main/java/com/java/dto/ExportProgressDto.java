package com.java.dto;

import com.java.model.enums.ExportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportProgressDto {

    private Long sessionId;
    private ExportStatus status;
    private Long totalRows;
    private Long exportedRows;
    private Long filteredRows;
    private Integer progressPercentage;
    private String currentOperation;
    private String estimatedTimeRemaining;
    private Boolean isCompleted;

    // Для WebSocket обновлений
    private String updateType; // PROGRESS, STATUS_CHANGE, ERROR, COMPLETED
    private String message;
    private Long timestamp;
}