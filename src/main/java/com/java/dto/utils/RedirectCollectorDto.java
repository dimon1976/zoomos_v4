package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * DTO для утилиты сбора финальных URL после редиректов
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedirectCollectorDto {
    
    /**
     * Номер колонки с исходными URL (индекс с 0)
     */
    @NotNull(message = "Необходимо выбрать колонку с URL")
    private Integer urlColumn;
    
    /**
     * Максимальное количество редиректов для следования
     */
    @Min(value = 1, message = "Минимальное количество редиректов: 1")
    @Max(value = 20, message = "Максимальное количество редиректов: 20")
    @Builder.Default
    private Integer maxRedirects = 5;
    
    /**
     * Таймаут для HTTP запросов в секундах
     */
    @Min(value = 1, message = "Минимальный таймаут: 1 секунда")
    @Max(value = 60, message = "Максимальный таймаут: 60 секунд")
    @Builder.Default
    private Integer timeoutSeconds = 10;
    
    /**
     * Формат выходного файла
     */
    @Builder.Default
    private String outputFormat = "CSV";
    
    /**
     * Разделитель для CSV
     */
    @Builder.Default
    private String csvDelimiter = ";";
    
    /**
     * Кодировка для CSV
     */
    @Builder.Default
    private String csvEncoding = "UTF-8";
    
    /**
     * Образец данных из файла для отображения в интерфейсе
     */
    private List<List<String>> sampleData;
    
    /**
     * Путь к временному файлу
     */
    private String tempFilePath;
    
    /**
     * Имя оригинального файла
     */
    private String originalFilename;
}