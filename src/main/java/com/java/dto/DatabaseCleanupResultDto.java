package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DatabaseCleanupResultDto {
    private int deletedImportSessions;
    private int deletedExportSessions;
    private int deletedFileOperations;
    private int deletedOrphanedRecords;
    private long freedSpaceBytes;
    private boolean success;
    private String errorMessage;
    private LocalDateTime cleanupTime;
    private String formattedFreedSpace;
}