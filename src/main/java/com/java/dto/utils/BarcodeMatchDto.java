package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для утилиты сопоставления штрихкодов
 */
@Data
public class BarcodeMatchDto {
    
    @NotNull(message = "Колонка исходного ID обязательна")
    private Integer sourceIdColumn;
    
    @NotNull(message = "Колонка исходных штрихкодов обязательна")
    private Integer sourceBarcodesColumn;
    
    @NotNull(message = "Колонка справочных штрихкодов обязательна")
    private Integer lookupBarcodesColumn;
    
    @NotNull(message = "Колонка справочных URL обязательна")
    private Integer lookupUrlColumn;
    
    // Настройки формата вывода
    private String outputFormat = "csv";     // csv или excel
    private String csvDelimiter = ";";       // разделитель для CSV
    private String csvEncoding = "UTF-8";    // кодировка для CSV
}