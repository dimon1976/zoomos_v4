package com.java.service.utils.redirect;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RedirectProgressDto {
    private String operationId;
    private String message;
    private int percentage;
    private int processed;
    private int total;
    private String status; // IN_PROGRESS, COMPLETED, ERROR
    private String fileName; // Для завершенных операций
    private LocalDateTime timestamp;
}