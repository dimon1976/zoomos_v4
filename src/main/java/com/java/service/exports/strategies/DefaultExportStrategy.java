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
        if (!data.isEmpty()) {
            log.debug("Первая исходная строка: {}", data.get(0));
        }

        // Получаем список полей для экспорта
        List<ExportTemplateField> includedFields = template.getFields().stream()
                .filter(ExportTemplateField::getIsIncluded)
                .sorted(Comparator.comparing(ExportTemplateField::getFieldOrder))
                .toList();

        // Обрабатываем каждую запись
        List<Map<String, Object>> processedData = new ArrayList<>();

        for (Map<String, Object> row : data) {
            Map<String, Object> processedRow = new LinkedHashMap<>();

            for (ExportTemplateField field : includedFields) {
                String entityFieldName = field.getEntityFieldName();
                String exportColumnName = field.getExportColumnName();

                // Пытаемся получить значение по имени поля как в шаблоне,
                // а при отсутствии пробуем его в формате snake_case
                Object value = row.get(entityFieldName);
                if (value == null) {
                    value = row.get(toSnakeCase(entityFieldName));
                }

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
        if (!processedData.isEmpty()) {
            log.debug("Первая обработанная строка: {}", processedData.get(0));
        }
        return processedData;
    }

    /**
     * Преобразует camelCase в snake_case для соответствия именам колонок БД
     */
    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
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