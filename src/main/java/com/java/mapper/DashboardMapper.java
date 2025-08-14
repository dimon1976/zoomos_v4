package com.java.mapper;

import com.java.dto.DashboardOperationDto;
import com.java.model.FileOperation;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class DashboardMapper {

    public DashboardOperationDto toOperationDto(FileOperation operation) {
        return DashboardOperationDto.builder()
                .id(operation.getId())
                .operationType(operation.getOperationType().name())
                .operationTypeDisplay(getOperationTypeDisplay(operation.getOperationType()))
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .status(operation.getStatus().name())
                .statusDisplay(getStatusDisplay(operation.getStatus()))
                .clientId(operation.getClient().getId())
                .clientName(operation.getClient().getName())
                .recordCount(operation.getRecordCount())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .processingProgress(operation.getProcessingProgress())
                .fileSize(operation.getFileSize())
                .fileSizeFormatted(formatFileSize(operation.getFileSize()))
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .duration(calculateDuration(operation.getStartedAt(), operation.getCompletedAt()))
                .errorMessage(operation.getErrorMessage())
                .hasErrors(operation.getErrorMessage() != null)
                .sourceFilePath(operation.getSourceFilePath())
                .resultFilePath(operation.getResultFilePath())
                .build();
    }

    private String getOperationTypeDisplay(FileOperation.OperationType type) {
        return switch (type) {
            case IMPORT -> "Импорт";
            case EXPORT -> "Экспорт";
            case PROCESS -> "Обработка";
        };
    }

    private String getStatusDisplay(FileOperation.OperationStatus status) {
        return switch (status) {
            case PENDING -> "Ожидание";
            case PROCESSING -> "В процессе";
            case COMPLETED -> "Завершено";
            case FAILED -> "Ошибка";
        };
    }

    private String formatFileSize(Long sizeInBytes) {
        if (sizeInBytes == null || sizeInBytes == 0) {
            return null;
        }

        final String[] units = {"Б", "КБ", "МБ", "ГБ"};
        int unitIndex = 0;
        double size = sizeInBytes.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    private String calculateDuration(ZonedDateTime startedAt, ZonedDateTime completedAt) {
        if (startedAt == null) {
            return "Н/Д";
        }

        if (completedAt == null) {
            long minutesRunning = ChronoUnit.MINUTES.between(startedAt, ZonedDateTime.now());
            return minutesRunning + " мин";
        }

        long minutes = ChronoUnit.MINUTES.between(startedAt, completedAt);
        if (minutes < 1) {
            long seconds = ChronoUnit.SECONDS.between(startedAt, completedAt);
            return seconds + " сек";
        } else if (minutes < 60) {
            return minutes + " мин";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + " ч " + remainingMinutes + " мин";
        }
    }
}