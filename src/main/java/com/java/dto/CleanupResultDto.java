package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CleanupResultDto {
    private int deletedFiles;
    private long freedSpaceBytes;
    private boolean success;
    private String errorMessage;
    private LocalDateTime cleanupTime;
    private String formattedFreedSpace;
    private int deletedDirectories;
}