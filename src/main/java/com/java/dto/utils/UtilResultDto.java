package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для результата обработки утилиты
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilResultDto {

    private String utilityName; // Название утилиты
    private String resultFileName; // Имя файла с результатами
    private String resultFileFormat; // Формат результата (xlsx, csv)
    private Long resultFileSize; // Размер файла результата в байтах
    private Integer processedRows; // Количество обработанных строк
    private Integer resultRows; // Количество строк в результате
    private LocalDateTime processedAt; // Время обработки
    private Long processingTimeMs; // Время обработки в миллисекундах
    private String status; // success, error, warning
    private String message; // Сообщение о результате или ошибке
    
    // Дополнительные метаданные
    private String originalFileName; // Имя исходного файла
    private Long originalFileSize; // Размер исходного файла
    
    // Формат метаданных для отображения
    public String getFormattedResultFileSize() {
        if (resultFileSize == null) return "N/A";
        return formatFileSize(resultFileSize);
    }
    
    public String getFormattedOriginalFileSize() {
        if (originalFileSize == null) return "N/A";
        return formatFileSize(originalFileSize);
    }
    
    public String getFormattedProcessingTime() {
        if (processingTimeMs == null) return "N/A";
        if (processingTimeMs < 1000) {
            return processingTimeMs + " мс";
        } else if (processingTimeMs < 60000) {
            return String.format("%.1f сек", processingTimeMs / 1000.0);
        } else {
            return String.format("%.1f мин", processingTimeMs / 60000.0);
        }
    }
    
    private String formatFileSize(Long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format("%.1f КБ", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f МБ", bytes / (1024.0 * 1024.0));
        return String.format("%.1f ГБ", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}