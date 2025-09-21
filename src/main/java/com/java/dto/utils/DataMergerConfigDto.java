package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO для конфигурации маппинга полей Data Merger
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMergerConfigDto {

    /**
     * Маппинг полей исходного файла
     * Ключ - логическое поле (id, originalModel, analogModel, coefficient)
     * Значение - индекс столбца в файле (0-based)
     */
    private Map<String, Integer> sourceFileMapping;

    /**
     * Маппинг полей файла ссылок
     * Ключ - логическое поле (analogModel, link)
     * Значение - индекс столбца в файле (0-based)
     */
    private Map<String, Integer> linksFileMapping;

    /**
     * Список выходных полей для результата
     */
    private List<String> outputFields;

    /**
     * Заголовки исходного файла (для отображения в UI)
     */
    private List<String> sourceHeaders;

    /**
     * Заголовки файла ссылок (для отображения в UI)
     */
    private List<String> linksHeaders;

    /**
     * Флаг наличия заголовков в исходном файле
     */
    @Builder.Default
    private Boolean sourceHasHeaders = true;

    /**
     * Флаг наличия заголовков в файле ссылок
     */
    @Builder.Default
    private Boolean linksHasHeaders = true;

    /**
     * Валидация конфигурации
     */
    public boolean isValid() {
        return sourceFileMapping != null && !sourceFileMapping.isEmpty() &&
               linksFileMapping != null && !linksFileMapping.isEmpty() &&
               outputFields != null && !outputFields.isEmpty();
    }

    /**
     * Проверка наличия обязательных полей для исходного файла
     */
    public boolean hasRequiredSourceFields() {
        return sourceFileMapping.containsKey("id") &&
               sourceFileMapping.containsKey("originalModel") &&
               sourceFileMapping.containsKey("analogModel") &&
               sourceFileMapping.containsKey("coefficient");
    }

    /**
     * Проверка наличия обязательных полей для файла ссылок
     */
    public boolean hasRequiredLinksFields() {
        return linksFileMapping.containsKey("analogModel") &&
               linksFileMapping.containsKey("link");
    }
}