package com.java.model.utils;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedirectResult {
    private String originalUrl;
    private String finalUrl;
    private Integer redirectCount;
    private PageStatus status;
    private String errorMessage;
    private Long startTime;
    private Long endTime;
    private String strategy;
    private Integer httpCode;
    
    public Long getProcessingTimeMs() {
        if (startTime != null && endTime != null) {
            return endTime - startTime;
        }
        return null;
    }
}