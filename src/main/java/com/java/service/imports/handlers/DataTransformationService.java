package com.java.service.imports.handlers;

import com.java.model.entity.ImportTemplate;
import com.java.model.entity.ImportTemplateField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Сервис трансформации данных согласно правилам шаблона
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataTransformationService {

    /**
     * Трансформирует строку данных согласно правилам шаблона
     */
    public Map<String, Object> transformRow(Map<String, String> rowData,
                                            ImportTemplate template,
                                            long rowNumber) throws TransformationException {
        log.debug("=== Начало трансформации строки {} ===", rowNumber);
        log.debug("Входные данные rowData: {} элементов", rowData.size());

        // Логируем первые несколько элементов для отладки
        int count = 0;
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            if (count++ < 5) {
                log.debug("rowData['{}'] = '{}'", entry.getKey(), entry.getValue());
            }
        }

        log.debug("Шаблон: {}, полей: {}", template.getName(), template.getFields().size());

        Map<String, Object> transformedData = new HashMap<>();

        for (ImportTemplateField field : template.getFields()) {
            log.debug("--- Обработка поля: {} ---", field.getEntityFieldName());
            log.debug("Поле: columnName='{}', columnIndex={}, isRequired={}",
                    field.getColumnName(), field.getColumnIndex(), field.getIsRequired());

            try {
                // Получаем значение из строки
                String rawValue = getFieldValue(rowData, field);
                log.debug("Получено значение: '{}'", rawValue);

                // Применяем значение по умолчанию если пусто
                if ((rawValue == null || rawValue.trim().isEmpty()) && field.getDefaultValue() != null) {
                    log.debug("Применяем значение по умолчанию: '{}'", field.getDefaultValue());
                    rawValue = field.getDefaultValue();
                }

                // Проверяем обязательность поля
                if (field.getIsRequired() && (rawValue == null || rawValue.trim().isEmpty())) {
                    log.warn("Обязательное поле '{}' пустое", field.getEntityFieldName());
                    throw new ValidationException("Обязательное поле пустое");
                }

                // Пропускаем пустые необязательные поля
                if (!field.getIsRequired() && (rawValue == null || rawValue.trim().isEmpty())) {
                    log.debug("Пропускаем пустое необязательное поле '{}'", field.getEntityFieldName());
                    continue;
                }

                // Валидируем по регулярному выражению
                if (field.getValidationRegex() != null && !field.getValidationRegex().isEmpty()) {
                    log.debug("Валидация по regex: {}", field.getValidationRegex());
                    if (!Pattern.matches(field.getValidationRegex(), rawValue)) {
                        log.warn("Значение '{}' не соответствует regex", rawValue);
                        throw new ValidationException(field.getValidationMessage() != null ?
                                field.getValidationMessage() : "Значение не соответствует формату");
                    }
                }

                // Трансформируем значение согласно типу
                Object transformedValue = transformValue(rawValue, field);
                log.debug("Трансформированное значение: {} (тип: {})",
                        transformedValue, transformedValue != null ? transformedValue.getClass().getSimpleName() : "null");

                // Добавляем в результат
                transformedData.put(field.getEntityFieldName(), transformedValue);
                log.debug("Добавлено в результат: {} = {}", field.getEntityFieldName(), transformedValue);

            } catch (Exception e) {
                log.error("Ошибка обработки поля '{}': {}", field.getEntityFieldName(), e.getMessage(), e);
                String message = String.format("Ошибка в поле '%s': %s",
                        field.getEntityFieldName(), e.getMessage());
                throw new TransformationException(message, field.getColumnName(), rowNumber, e);
            }
        }

        log.debug("=== Результат трансформации: {} полей ===", transformedData.size());
        if (transformedData.isEmpty()) {
            log.warn("ВНИМАНИЕ: Результат трансформации пустой!");
        } else {
            // Логируем результат
            for (Map.Entry<String, Object> entry : transformedData.entrySet()) {
                log.debug("Результат['{}'] = '{}'", entry.getKey(), entry.getValue());
            }
        }

        return transformedData;
    }

    /**
     * Получает значение поля из данных строки
     */
    private String getFieldValue(Map<String, String> rowData, ImportTemplateField field) {
        log.trace("getFieldValue: ищем значение для поля {}", field.getEntityFieldName());

        // Сначала пробуем по имени колонки
        if (field.getColumnName() != null && !field.getColumnName().isEmpty()) {
            log.trace("Поиск по имени колонки: '{}'", field.getColumnName());
            String value = rowData.get(field.getColumnName());
            if (value != null) {
                log.trace("Найдено по имени колонки: '{}'", value);
                return value;
            } else {
                log.trace("Не найдено по имени колонки '{}'", field.getColumnName());

                // Пробуем найти с игнорированием регистра
                for (Map.Entry<String, String> entry : rowData.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(field.getColumnName())) {
                        log.trace("Найдено по имени колонки (без учета регистра): '{}' -> '{}'",
                                entry.getKey(), entry.getValue());
                        return entry.getValue();
                    }
                }
            }
        }

        // Затем по индексу
        if (field.getColumnIndex() != null) {
            log.trace("Поиск по индексу: {}", field.getColumnIndex());
            String indexKey = String.valueOf(field.getColumnIndex());
            String value = rowData.get(indexKey);
            if (value != null) {
                log.trace("Найдено по индексу: '{}'", value);
                return value;
            } else {
                log.trace("Не найдено по индексу {}", field.getColumnIndex());
            }
        }

        log.warn("Значение не найдено для поля '{}' (columnName='{}', columnIndex={})",
                field.getEntityFieldName(), field.getColumnName(), field.getColumnIndex());

        // Логируем доступные ключи для отладки
        log.debug("Доступные ключи в rowData: {}", rowData.keySet());

        return null;
    }

    /**
     * Трансформирует значение согласно типу поля
     */
    private Object transformValue(String rawValue, ImportTemplateField field) throws Exception {
        if (rawValue == null) {
            log.trace("transformValue: rawValue is null");
            return null;
        }

        String trimmedValue = rawValue.trim();
        log.trace("transformValue: тип={}, значение='{}'", field.getFieldType(), trimmedValue);

        switch (field.getFieldType()) {
            case STRING:
                return transformString(trimmedValue, field);

            case INTEGER:
                return transformInteger(trimmedValue);

            case DECIMAL:
                return transformDecimal(trimmedValue);

            case DATE:
                return transformDate(trimmedValue, field);

            case DATETIME:
                return transformDateTime(trimmedValue, field);

            case BOOLEAN:
                return transformBoolean(trimmedValue);

            default:
                log.warn("Неизвестный тип поля: {}, возвращаем как строку", field.getFieldType());
                return trimmedValue;
        }
    }

    /**
     * Трансформация строки
     */
    private String transformString(String value, ImportTemplateField field) {
        log.trace("transformString: входное значение='{}'", value);

        if (field.getTransformationRule() != null) {
            // Применяем правила трансформации
            String rule = field.getTransformationRule();
            log.trace("Применяем правило трансформации: {}", rule);

            if (rule.contains("uppercase")) {
                value = value.toUpperCase();
            } else if (rule.contains("lowercase")) {
                value = value.toLowerCase();
            }

            if (rule.contains("trim")) {
                value = value.trim();
            }

            // Можно добавить больше правил
        }

        log.trace("transformString: результат='{}'", value);
        return value;
    }

    /**
     * Трансформация в целое число
     */
    private Integer transformInteger(String value) throws Exception {
        log.trace("transformInteger: входное значение='{}'", value);

        try {
            // Удаляем пробелы и разделители тысяч
            value = value.replaceAll("[\\s,]", "");

            // Обрабатываем дробные числа
            if (value.contains(".")) {
                double doubleValue = Double.parseDouble(value);
                return (int) Math.round(doubleValue);
            }

            Integer result = Integer.parseInt(value);
            log.trace("transformInteger: результат={}", result);
            return result;
        } catch (NumberFormatException e) {
            throw new ValidationException("Некорректный формат числа: " + value);
        }
    }

    /**
     * Трансформация в десятичное число
     */
    private BigDecimal transformDecimal(String value) throws Exception {
        log.trace("transformDecimal: входное значение='{}'", value);

        try {
            // Заменяем запятую на точку
            value = value.replace(',', '.');
            // Удаляем пробелы
            value = value.replaceAll("\\s", "");

            BigDecimal result = new BigDecimal(value);
            log.trace("transformDecimal: результат={}", result);
            return result;
        } catch (NumberFormatException e) {
            throw new ValidationException("Некорректный формат десятичного числа: " + value);
        }
    }

    /**
     * Трансформация в дату
     */
    private LocalDate transformDate(String value, ImportTemplateField field) throws Exception {
        log.trace("transformDate: входное значение='{}', формат='{}'", value, field.getDateFormat());

        if (field.getDateFormat() == null || field.getDateFormat().isEmpty()) {
            throw new ValidationException("Формат даты не указан");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(field.getDateFormat());
            LocalDate result = LocalDate.parse(value, formatter);
            log.trace("transformDate: результат={}", result);
            return result;
        } catch (Exception e) {
            log.debug("Не удалось распарсить дату '{}' с форматом '{}', пробуем альтернативные форматы",
                    value, field.getDateFormat());

            // Пробуем альтернативные форматы
            String[] alternativeFormats = {"dd.MM.yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy"};

            for (String format : alternativeFormats) {
                try {
                    DateTimeFormatter altFormatter = DateTimeFormatter.ofPattern(format);
                    LocalDate result = LocalDate.parse(value, altFormatter);
                    log.trace("transformDate: успешно с форматом '{}', результат={}", format, result);
                    return result;
                } catch (Exception ignored) {
                    // Пробуем следующий формат
                }
            }

            throw new ValidationException("Некорректный формат даты: " + value);
        }
    }

    /**
     * Трансформация в дату и время
     */
    private LocalDateTime transformDateTime(String value, ImportTemplateField field) throws Exception {
        log.trace("transformDateTime: входное значение='{}', формат='{}'", value, field.getDateFormat());

        if (field.getDateFormat() == null || field.getDateFormat().isEmpty()) {
            throw new ValidationException("Формат даты/времени не указан");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(field.getDateFormat());
            LocalDateTime result = LocalDateTime.parse(value, formatter);
            log.trace("transformDateTime: результат={}", result);
            return result;
        } catch (Exception e) {
            throw new ValidationException("Некорректный формат даты/времени: " + value);
        }
    }

    /**
     * Трансформация в булево значение
     */
    private Boolean transformBoolean(String value) {
        log.trace("transformBoolean: входное значение='{}'", value);

        String lowercaseValue = value.toLowerCase();

        // Истинные значения
        if ("true".equals(lowercaseValue) || "1".equals(lowercaseValue) ||
                "yes".equals(lowercaseValue) || "y".equals(lowercaseValue) ||
                "да".equals(lowercaseValue) || "истина".equals(lowercaseValue)) {
            log.trace("transformBoolean: результат=true");
            return true;
        }

        // Ложные значения
        if ("false".equals(lowercaseValue) || "0".equals(lowercaseValue) ||
                "no".equals(lowercaseValue) || "n".equals(lowercaseValue) ||
                "нет".equals(lowercaseValue) || "ложь".equals(lowercaseValue)) {
            log.trace("transformBoolean: результат=false");
            return false;
        }

        throw new ValidationException("Некорректное булево значение: " + value);
    }

    /**
     * Исключение трансформации
     */
    public static class TransformationException extends Exception {
        private final String columnName;
        private final long rowNumber;

        public TransformationException(String message, String columnName, long rowNumber, Throwable cause) {
            super(message, cause);
            this.columnName = columnName;
            this.rowNumber = rowNumber;
        }

        public String getColumnName() { return columnName; }
        public long getRowNumber() { return rowNumber; }
    }

    /**
     * Исключение валидации
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}