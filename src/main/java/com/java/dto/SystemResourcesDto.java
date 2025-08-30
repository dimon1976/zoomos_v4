package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemResourcesDto {
    private double cpuUsagePercent;
    private long totalMemoryMb;
    private long usedMemoryMb;
    private long freeMemoryMb;
    private double memoryUsagePercent;
    private long totalDiskSpaceMb;
    private long usedDiskSpaceMb;
    private long freeDiskSpaceMb;
    private double diskUsagePercent;
    private LocalDateTime measurementTime;
    private String formattedTotalMemory;
    private String formattedUsedMemory;
    private String formattedTotalDisk;
    private String formattedFreeDisk;
}