package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DatabaseStatsDto {
    private long totalSizeBytes;
    private String formattedTotalSize;
    private Map<String, Long> tableSizes;
    private Map<String, String> formattedTableSizes;
    private int totalTables;
    private int totalIndexes;
    private LocalDateTime lastVacuum;
    private LocalDateTime lastAnalyze;
    private long activeConnections;
    private double cacheHitRatio;
}