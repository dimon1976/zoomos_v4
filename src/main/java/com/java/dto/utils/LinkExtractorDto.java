package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для утилиты извлечения ссылок
 */
@Data
public class LinkExtractorDto {
    
    // Основные колонки
    @NotNull(message = "Колонка ID обязательна")
    private Integer idColumn;
    
    // Настройки формата вывода
    private String outputFormat = "csv";     // csv или excel
    private String csvDelimiter = ";";       // разделитель для CSV
    private String csvEncoding = "UTF-8";    // кодировка для CSV
}