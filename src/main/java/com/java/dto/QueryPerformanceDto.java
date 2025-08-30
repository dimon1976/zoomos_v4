package com.java.dto;

import lombok.Data;

@Data
public class QueryPerformanceDto {
    private String query;
    private String queryHash;
    private long avgExecutionTimeMs;
    private long maxExecutionTimeMs;
    private int callCount;
    private String recommendation;
    private boolean slowQuery;
    private String tableName;
    private double cpuUsagePercent;
}