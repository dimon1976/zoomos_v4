package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SystemHealthDto {
    private String overallStatus;
    private LocalDateTime checkTime;
    private Map<String, String> componentStatuses;
    private boolean databaseHealthy;
    private boolean systemResourcesNormal;
    private double systemScore;
    private String recommendation;
    private long uptimeSeconds;
    private String formattedUptime;
}