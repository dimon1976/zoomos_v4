package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PerformanceMetricsDto {
    private double avgResponseTimeMs;
    private double maxResponseTimeMs;
    private long totalRequests;
    private long errorCount;
    private double errorRatePercent;
    private double throughputRequestsPerSecond;
    private Map<String, Double> endpointResponseTimes;
    private Map<String, Long> endpointRequestCounts;
    private LocalDateTime measurementStart;
    private LocalDateTime measurementEnd;
    private long measurementDurationSeconds;
    private int activeThreads;
    private double threadPoolUsagePercent;
}