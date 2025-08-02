package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия экспорта по умолчанию - без дополнительной обработки
 */
@Component("defaultExportStrategy")
@Slf4j
@RequiredArgsConstructor
public class DefaultExportStrategy implements ExportStrategy {

    @Override
    public String getName() {
        return "DEFAULT";
    }

    @Override
    public List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context) {

        log.debug("Применение стратегии по умолчанию для {} записей", data.size());

        // Получаем список полей для экспорта
        List<ExportTemplateField> includedFields = template.getFields().stream()
                .filter(ExportTemplateField::getIsIncluded)
                .sorted(Comparator.comparing(ExportTemplateField::getFieldOrder))
                .collect(Collectors.toList());

        // Обрабатываем каждую запись
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Map<String, Object> processedRow = new LinkedHashMap<>();

            for (ExportTemplateField field : includedFields) {
                String entityFieldName = field.getEntityFieldName();
                String exportColumnName = field.getExportColumnName();
                Object value = row.get(entityFieldName);

                // Применяем форматирование если указано
                if (value != null && field.getDataFormat() != null) {
                    value = formatValue(value, field.getDataFormat());
                }

                // Используем название колонки для экспорта
                processedRow.put(exportColumnName, value);
            }

            processedData.add(processedRow);
        }

        log.debug("Обработано {} записей", processedData.size());
        return processedData;
    }

    /**
     * Форматирование значения согласно указанному формату
     */
    private Object formatValue(Object value, String format) {
        try {
            if (value instanceof LocalDateTime) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return ((LocalDateTime) value).format(formatter);
            }
            if (value instanceof ZonedDateTime) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return ((ZonedDateTime) value).format(formatter);
            }
            if (value instanceof Number && format.contains("#")) {
                // Простое форматирование чисел
                return String.format(format.replace("#", "%"), value);
            }
        } catch (Exception e) {
            log.warn("Ошибка форматирования значения: {}", e.getMessage());
        }
        return value;
    }
}