package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileOperationStatsDto {
    private int totalImports;
    private int totalExports;
    private long averageFileSizeBytes;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String formattedAverageSize;
    private int totalOperations;
}