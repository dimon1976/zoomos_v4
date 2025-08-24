package com.java.util;

import com.java.model.FileOperation;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ControllerUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public String getOperationTypeDisplay(FileOperation.OperationType type) {
        return switch (type) {
            case IMPORT -> "Импорт";
            case EXPORT -> "Экспорт";
            case PROCESS -> "Обработка";
        };
    }

    public String getStatusDisplay(FileOperation.OperationStatus status) {
        return switch (status) {
            case PENDING -> "Ожидание";
            case PROCESSING -> "В процессе";
            case COMPLETED -> "Завершено";
            case FAILED -> "Ошибка";
        };
    }

    public String getStatusClass(FileOperation.OperationStatus status) {
        return switch (status) {
            case PENDING -> "status-pending";
            case PROCESSING -> "status-processing";
            case COMPLETED -> "status-success";
            case FAILED -> "status-error";
        };
    }

    public String formatDateTime(ZonedDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}