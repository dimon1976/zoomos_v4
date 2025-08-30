package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HealthCheckResultDto {
    private String componentName;
    private boolean healthy;
    private String status;
    private String message;
    private String details;
    private long checkDurationMs;
    private LocalDateTime checkTime;
    private String severity;
    private String recommendation;
    private double score;
}