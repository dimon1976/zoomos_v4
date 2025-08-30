package com.java.dto;

import lombok.Data;

@Data
public class DataIntegrityIssueDto {
    private String tableName;
    private String issueType;
    private String description;
    private int affectedRows;
    private String suggestedFix;
    private String severity;
    private boolean canAutoFix;
    private String sqlQuery;
}