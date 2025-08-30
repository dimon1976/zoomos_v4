package com.java.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ArchiveResultDto {
    private int archivedFiles;
    private long totalArchivedSizeBytes;
    private String archivePath;
    private boolean success;
    private String errorMessage;
    private LocalDateTime archiveTime;
    private String formattedArchivedSize;
}