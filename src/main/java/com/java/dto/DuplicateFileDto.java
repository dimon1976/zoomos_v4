package com.java.dto;

import lombok.Data;
import java.nio.file.Path;
import java.util.List;

@Data
public class DuplicateFileDto {
    private String hash;
    private List<String> filePaths;
    private long fileSizeBytes;
    private int duplicateCount;
    private String formattedFileSize;
    private String fileName;
}