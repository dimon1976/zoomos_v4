package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для утилиты очистки URL
 */
@Data
public class UrlCleanerDto {
    
    // Основные колонки
    @NotNull(message = "Колонка URL обязательна")
    private Integer urlColumn;
    
    private Integer idColumn;      // необязательная
    private Integer modelColumn;   // необязательная  
    private Integer barcodeColumn; // необязательная
    
    // Настройки очистки
    private boolean removeUtmParams = true;        // удалить UTM параметры
    private boolean removeReferralParams = true;   // удалить реферальные параметры
    private boolean removeTrackingParams = true;   // удалить отслеживающие параметры
    private boolean preserveYandexSku = true;      // сохранить sku для Yandex Market
    
    // Настройки формата вывода
    private String outputFormat = "csv";     // csv или excel
    private String csvDelimiter = ";";       // разделитель для CSV
    private String csvEncoding = "UTF-8";    // кодировка для CSV
}