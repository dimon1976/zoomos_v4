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
        Map<String, Object> transformedData = new HashMap<>();

        for (ImportTemplateField field : template.getFields()) {
            try {
                // Получаем значение из строки
                String rawValue = getFieldValue(rowData, field);

                // Применяем значение по умолчанию если пусто
                if ((rawValue == null || rawValue.trim().isEmpty()) && field.getDefaultValue() != null) {
                    rawValue = field.getDefaultValue();
                }

                // Проверяем обязательность поля
                if (field.getIsRequired() && (rawValue == null || rawValue.trim().isEmpty())) {
                    throw new ValidationException("Обязательное поле пустое");
                }

                // Пропускаем пустые необязательные поля
                if (!field.getIsRequired() && (rawValue == null || rawValue.trim().isEmpty())) {
                    continue;
                }

                // Валидируем по регулярному выражению
                if (field.getValidationRegex() != null && !field.getValidationRegex().isEmpty()) {
                    if (!Pattern.matches(field.getValidationRegex(), rawValue)) {
                        throw new ValidationException(field.getValidationMessage() != null ?
                                field.getValidationMessage() : "Значение не соответствует формату");
                    }
                }

                // Трансформируем значение согласно типу
                Object transformedValue = transformValue(rawValue, field);

                // Добавляем в результат
                transformedData.put(field.getEntityFieldName(), transformedValue);

            } catch (Exception e) {
                String message = String.format("Ошибка в поле '%s': %s",
                        field.getEntityFieldName(), e.getMessage());
                throw new TransformationException(message, field.getColumnName(), rowNumber, e);
            }
        }

        return transformedData;
    }

    /**
     * Получает значение поля из данных строки
     */
    private String getFieldValue(Map<String, String> rowData, ImportTemplateField field) {
        // Сначала пробуем по имени колонки
        if (field.getColumnName() != null) {
            String value = rowData.get(field.getColumnName());
            if (value != null) return value;
        }

        // Затем по индексу
        if (field.getColumnIndex() != null) {
            return rowData.get(String.valueOf(field.getColumnIndex()));
        }

        return null;
    }

    /**
     * Трансформирует значение согласно типу поля
     */
    private Object transformValue(String rawValue, ImportTemplateField field) throws Exception {
        if (rawValue == null) return null;

        String trimmedValue = rawValue.trim();

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
                return trimmedValue;
        }
    }

    /**
     * Трансформация строки
     */
    private String transformString(String value, ImportTemplateField field) {
        if (field.getTransformationRule() != null) {
            // Применяем правила трансформации
            String rule = field.getTransformationRule();

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

        return value;
    }

    /**
     * Трансформация в целое число
     */
    private Integer transformInteger(String value) throws Exception {
        try {
            // Удаляем пробелы и разделители тысяч
            value = value.replaceAll("[\\s,]", "");

            // Обрабатываем дробные числа
            if (value.contains(".")) {
                double doubleValue = Double.parseDouble(value);
                return (int) Math.round(doubleValue);
            }

            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Некорректный формат числа: " + value);
        }
    }

    /**
     * Трансформация в десятичное число
     */
    private BigDecimal transformDecimal(String value) throws Exception {
        try {
            // Заменяем запятую на точку
            value = value.replace(',', '.');
            // Удаляем пробелы
            value = value.replaceAll("\\s", "");

            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Некорректный формат десятичного числа: " + value);
        }
    }

    /**
     * Трансформация в дату
     */
    private LocalDate transformDate(String value, ImportTemplateField field) throws Exception {
        if (field.getDateFormat() == null || field.getDateFormat().isEmpty()) {
            throw new ValidationException("Формат даты не указан");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(field.getDateFormat());
            return LocalDate.parse(value, formatter);
        } catch (Exception e) {
            // Пробуем альтернативные форматы
            String[] alternativeFormats = {"dd.MM.yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy"};

            for (String format : alternativeFormats) {
                try {
                    DateTimeFormatter altFormatter = DateTimeFormatter.ofPattern(format);
                    return LocalDate.parse(value, altFormatter);
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
        if (field.getDateFormat() == null || field.getDateFormat().isEmpty()) {
            throw new ValidationException("Формат даты/времени не указан");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(field.getDateFormat());
            return LocalDateTime.parse(value, formatter);
        } catch (Exception e) {
            throw new ValidationException("Некорректный формат даты/времени: " + value);
        }
    }

    /**
     * Трансформация в булево значение
     */
    private Boolean transformBoolean(String value) {
        String lowercaseValue = value.toLowerCase();

        // Истинные значения
        if ("true".equals(lowercaseValue) || "1".equals(lowercaseValue) ||
                "yes".equals(lowercaseValue) || "y".equals(lowercaseValue) ||
                "да".equals(lowercaseValue) || "истина".equals(lowercaseValue)) {
            return true;
        }

        // Ложные значения
        if ("false".equals(lowercaseValue) || "0".equals(lowercaseValue) ||
                "no".equals(lowercaseValue) || "n".equals(lowercaseValue) ||
                "нет".equals(lowercaseValue) || "ложь".equals(lowercaseValue)) {
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