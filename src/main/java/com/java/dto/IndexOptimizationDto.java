package com.java.dto;

import lombok.Data;

@Data
public class IndexOptimizationDto {
    private String tableName;
    private String indexName;
    private String recommendationType;
    private String description;
    private long sizeMb;
    private double usagePercent;
    private boolean isDuplicate;
    private String suggestedAction;
    private String formattedSize;
}