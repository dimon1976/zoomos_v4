package com.java.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastVacuum;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastAnalyze;

    private long activeConnections;
    private double cacheHitRatio;
}