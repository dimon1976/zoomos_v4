package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * DTO для выбора колонок в утилитах
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilColumnSelectionDto {

    @NotNull(message = "Имя файла обязательно")
    private String fileName;
    
    private String fileFormat; // xlsx, csv
    private Long fileSize;
    private List<String> availableColumns; // Доступные колонки
    private List<Map<String, String>> sampleData; // Образец данных для предварительного просмотра
    private Boolean hasHeader; // Есть ли заголовки в файле
    
    // Выбранные колонки для разных целей
    private Integer selectedIdColumn; // Индекс колонки с ID (для утилит где нужен ID)
    private List<Integer> selectedDataColumns; // Индексы колонок с данными
    private Map<String, Integer> namedColumns; // Именованные колонки (например, "barcode" -> 2)
    
    // Дополнительные настройки
    private String delimiter; // Разделитель для CSV
    private String encoding; // Кодировка файла
}