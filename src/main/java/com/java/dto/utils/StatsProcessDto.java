package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
    @NotBlank(message = "Формат вывода не может быть пустым")
    @Pattern(regexp = "^(xlsx|csv)$", message = "Поддерживаются только форматы xlsx и csv")
    private String outputFormat = "xlsx";      // xlsx или csv
    
    @NotBlank(message = "Разделитель CSV не может быть пустым")
    @Pattern(regexp = "^[;,|\\t]$", message = "Поддерживаются только разделители: ; , | или табуляция")
    private String csvDelimiter = ";";         // разделитель для CSV
    
    @NotBlank(message = "Кодировка не может быть пустой")
    @Pattern(regexp = "^(UTF-8|ISO-8859-1)$", message = "Поддерживаются только кодировки UTF-8 и ISO-8859-1")
    private String csvEncoding = "UTF-8";      // кодировка для CSV
}