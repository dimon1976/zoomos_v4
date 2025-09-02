package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DirectoryStatsDto {
    private String directoryName;
    private long totalSizeBytes;
    private int fileCount;
    private double usagePercentage;
    private LocalDateTime lastModified;
    private String formattedSize;
    private String relativePath;
}