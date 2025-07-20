package com.java.service.imports.validation;

import com.java.dto.ImportTemplateDto;
import com.java.dto.ImportTemplateFieldDto;
import com.java.model.enums.EntityType;
import com.java.model.enums.FieldType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис валидации шаблонов импорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateValidationService {

    // Маппинг допустимых полей для каждого типа сущности
    private static final Map<EntityType, Set<String>> ENTITY_FIELDS = new HashMap<>();

    static {
        // Поля для азбуки
        ENTITY_FIELDS.put(EntityType.AV_DATA, Set.of(
                "data_source", "operation_id", "client_id", "product_id", "product_name", "product_brand", "product_bar", "product_description", "product_url", "product_category1",
                "product_category2", "product_category3", "product_price", "product_analog", "product_additional1", "product_additional2", "product_additional3", "product_additional4",
                "product_additional5", "region", "region_Address", "competitor_Name", "competitor_Price", "competitor_Promotional_Price", "competitor_time", "competitor_date",
                "competitor_local_date_time", "competitor_stock_status", "competitor_additional_price", "competitor_commentary", "competitor_product_name", "competitor_additional",
                "competitor_additional2", "competitor_url", "competitor_web_cache_url"
        ));

        // Поля для справочника
        ENTITY_FIELDS.put(EntityType.AV_HANDBOOK, Set.of(
                "handbookRetailNetworkCode", "handbookRetailNetwork", "handbookPhysicalAddress", "handbookPriceZoneCode", "handbookWebSite",
                "handbookRegionCode", "handbookRegionName"
        ));
    }

    /**
     * Валидирует шаблон импорта
     */
    public void validateTemplate(ImportTemplateDto template) {
        List<String> errors = new ArrayList<>();

        // Проверка основных полей
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            errors.add("Название шаблона не может быть пустым");
        }

        if (template.getEntityType() == null) {
            errors.add("Тип сущности должен быть указан");
        }

        if (template.getFields() == null || template.getFields().isEmpty()) {
            errors.add("Шаблон должен содержать хотя бы одно поле");
        }

        // Проверка полей
        if (template.getFields() != null) {
            validateTemplateFields(template.getFields(), template.getEntityType());
        }

        // Проверка настроек CSV
        if ("CSV".equalsIgnoreCase(template.getFileType())) {
            if (template.getDelimiter() == null || template.getDelimiter().isEmpty()) {
                errors.add("Разделитель должен быть указан для CSV файлов");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Ошибки валидации шаблона", errors);
        }
    }

    /**
     * Валидирует поля шаблона
     */
    public void validateTemplateFields(List<ImportTemplateFieldDto> fields, EntityType entityType) {
        List<String> errors = new ArrayList<>();
        Set<String> entityFieldNames = new HashSet<>();
        Set<String> columnNames = new HashSet<>();
        Set<Integer> columnIndexes = new HashSet<>();

        // Получаем допустимые поля для типа сущности
        Set<String> allowedFields = ENTITY_FIELDS.getOrDefault(entityType, new HashSet<>());

        for (int i = 0; i < fields.size(); i++) {
            ImportTemplateFieldDto field = fields.get(i);
            String prefix = "Поле " + (i + 1) + ": ";

            // Проверка имени поля сущности
            if (field.getEntityFieldName() == null || field.getEntityFieldName().trim().isEmpty()) {
                errors.add(prefix + "Имя поля сущности обязательно");
            } else {
                // Проверка допустимости поля для типа сущности
                if (!allowedFields.isEmpty() && !allowedFields.contains(field.getEntityFieldName())) {
                    errors.add(prefix + "Поле '" + field.getEntityFieldName() +
                            "' недопустимо для типа " + entityType);
                }

                // Проверка дубликатов
                if (!entityFieldNames.add(field.getEntityFieldName())) {
                    errors.add(prefix + "Дублирование поля сущности '" +
                            field.getEntityFieldName() + "'");
                }
            }

            // Проверка имени колонки или индекса
            if (field.getColumnName() == null && field.getColumnIndex() == null) {
                errors.add(prefix + "Должно быть указано имя колонки или её индекс");
            }

            // Проверка дубликатов колонок
            if (field.getColumnName() != null && !columnNames.add(field.getColumnName())) {
                errors.add(prefix + "Дублирование колонки '" + field.getColumnName() + "'");
            }

            if (field.getColumnIndex() != null && !columnIndexes.add(field.getColumnIndex())) {
                errors.add(prefix + "Дублирование индекса колонки " + field.getColumnIndex());
            }

            // Валидация формата даты
            if (field.getFieldType() == FieldType.DATE || field.getFieldType() == FieldType.DATETIME) {
                if (field.getDateFormat() == null || field.getDateFormat().trim().isEmpty()) {
                    errors.add(prefix + "Формат даты обязателен для полей типа DATE/DATETIME");
                } else {
                    validateDateFormat(field.getDateFormat(), errors, prefix);
                }
            }

            // Валидация регулярного выражения
            if (field.getValidationRegex() != null && !field.getValidationRegex().isEmpty()) {
                validateRegex(field.getValidationRegex(), errors, prefix);
            }
        }

        // Проверка наличия обязательных полей для типа сущности
        validateRequiredFields(entityType, entityFieldNames, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException("Ошибки валидации полей шаблона", errors);
        }
    }

    /**
     * Проверяет наличие обязательных полей для типа сущности
     */
    private void validateRequiredFields(EntityType entityType, Set<String> fields, List<String> errors) {
        Set<String> requiredFields = new HashSet<>();

        switch (entityType) {
            case AV_DATA:
                requiredFields.add("productId");
                break;
            case AV_HANDBOOK:
                requiredFields.add("handbookRetailNetworkCode");
                requiredFields.add("handbookRetailNetwork");
                requiredFields.add("handbookWebSite");
                break;
        }

        Set<String> missingFields = requiredFields.stream()
                .filter(f -> !fields.contains(f))
                .collect(Collectors.toSet());

        if (!missingFields.isEmpty()) {
            errors.add("Отсутствуют обязательные поля: " + String.join(", ", missingFields));
        }
    }

    /**
     * Валидирует формат даты
     */
    private void validateDateFormat(String format, List<String> errors, String prefix) {
        try {
            // Проверяем, что формат корректный
            java.time.format.DateTimeFormatter.ofPattern(format);
        } catch (IllegalArgumentException e) {
            errors.add(prefix + "Некорректный формат даты: " + format);
        }
    }

    /**
     * Валидирует регулярное выражение
     */
    private void validateRegex(String regex, List<String> errors, String prefix) {
        try {
            java.util.regex.Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            errors.add(prefix + "Некорректное регулярное выражение: " + e.getMessage());
        }
    }

    /**
     * Исключение валидации
     */
    @Getter
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(String message, List<String> errors) {
            super(message + ": " + String.join("; ", errors));
            this.errors = errors;
        }

    }
}