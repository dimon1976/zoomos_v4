package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * DTO для утилиты обработки файлов статистики
 * Аналог старой утилиты с чекбоксами для выбора колонок
 */
@Data
public class StatsProcessDto {
    
    // Чекбоксы для выбора дополнительных колонок (аналог старой версии)
    private boolean showSource = false;        // "Кто прописал"
    private boolean sourceReplace = false;     // "Заменить источник для клиента" 
    private boolean showCompetitorUrl = false; // "URL конкурента"
    private boolean showDateAdd = false;       // "Дата добавления"
    
    // Настройки формата вывода
    private String outputFormat = "xlsx";      // xlsx или csv
    private String csvDelimiter = ";";         // разделитель для CSV
    private String csvEncoding = "UTF-8";      // кодировка для CSV
}