package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DatabaseHealthDto {
    private boolean connectionActive;
    private String connectionStatus;
    private long connectionTimeMs;
    private int activeConnections;
    private int maxConnections;
    private double connectionPoolUsage;
    private LocalDateTime lastSuccessfulQuery;
    private boolean transactionManagerHealthy;
    private String databaseVersion;
    private long databaseSizeMb;
}