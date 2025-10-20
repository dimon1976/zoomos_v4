package com.java.service.error;

import com.java.service.i18n.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис для форматирования понятных сообщений об ошибках БД для пользователей.
 * Преобразует технические детали в локализованные user-friendly сообщения.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorMessageFormatter {

    private final MessageService messageService;

    // Маппинг технических имен колонок из av_data таблицы на понятные названия
    private static final Map<String, String> COLUMN_NAME_MAP = Map.ofEntries(
            Map.entry("product_name", "Название товара"),
            Map.entry("product_description", "Описание товара"),
            Map.entry("product_brand", "Бренд"),
            Map.entry("product_barcode", "Штрих-код"),
            Map.entry("product_article", "Артикул"),
            Map.entry("product_id", "ID товара"),
            Map.entry("competitor_name", "Название конкурента"),
            Map.entry("competitor_url", "URL конкурента"),
            Map.entry("competitor_price", "Цена конкурента"),
            Map.entry("competitor_promotional_price", "Промо-цена конкурента"),
            Map.entry("competitor_stock_status", "Статус наличия у конкурента"),
            Map.entry("product_category", "Категория товара"),
            Map.entry("product_additional1", "Доп. поле 1"),
            Map.entry("product_additional2", "Доп. поле 2"),
            Map.entry("product_additional3", "Доп. поле 3"),
            Map.entry("product_additional4", "Доп. поле 4"),
            Map.entry("product_additional5", "Доп. поле 5")
    );

    /**
     * Форматирование понятного сообщения об ошибке БД.
     * Использует локализованные шаблоны из messages.properties.
     *
     * @param error распарсенная информация об ошибке
     * @return понятное сообщение для пользователя
     */
    public String formatDatabaseError(ParsedDatabaseError error) {
        switch (error.getType()) {
            case VALUE_TOO_LONG:
                String fieldName = translateColumnName(error.getColumnName());
                Integer actualLength = error.getActualLength();
                Integer maxLength = error.getMaxLength();

                // Fallback если не удалось извлечь длины
                if (actualLength == null && maxLength != null) {
                    return messageService.getMessageOrDefault(
                            "import.error.db.value.too.long.no.actual",
                            String.format("Значение слишком длинное для поля \"%s\" (максимум %d символов)",
                                    fieldName, maxLength)
                    );
                }

                if (maxLength == null) {
                    return messageService.getMessageOrDefault(
                            "import.error.db.value.too.long.no.limit",
                            String.format("Значение слишком длинное для поля \"%s\"", fieldName)
                    );
                }

                return messageService.getMessageOrDefault(
                        "import.error.db.value.too.long",
                        String.format("Значение слишком длинное для поля \"%s\": %d символов (максимум %d)",
                                fieldName, actualLength, maxLength)
                );

            case UNIQUE_VIOLATION:
                String uniqueFieldName = translateColumnName(error.getColumnName());
                return messageService.getMessageOrDefault(
                        "import.error.db.unique.violation",
                        String.format("Дубликат значения в поле \"%s\": такое значение уже существует в базе данных",
                                uniqueFieldName)
                );

            case NOT_NULL_VIOLATION:
                String notNullFieldName = translateColumnName(error.getColumnName());
                return messageService.getMessageOrDefault(
                        "import.error.db.not.null",
                        String.format("Отсутствует обязательное значение в поле \"%s\"", notNullFieldName)
                );

            case FOREIGN_KEY_VIOLATION:
                String fkFieldName = translateColumnName(error.getColumnName());
                String fkConstraint = error.getConstraintName() != null ? error.getConstraintName() : "неизвестное";
                return messageService.getMessageOrDefault(
                        "import.error.db.foreign.key.violation",
                        String.format("Ошибка ссылки на связанную запись в поле \"%s\" (constraint: %s)",
                                fkFieldName, fkConstraint)
                );

            case CONSTRAINT_VIOLATION:
                String constraintName = error.getConstraintName() != null ? error.getConstraintName() : "неизвестное";
                return messageService.getMessageOrDefault(
                        "import.error.db.constraint.violation",
                        String.format("Нарушение ограничения базы данных: %s", constraintName)
                );

            case UNKNOWN:
            default:
                // Fallback: показываем общее сообщение
                log.debug("Formatting UNKNOWN error type: {}", error.getOriginalMessage());
                return messageService.getMessageOrDefault(
                        "import.error.db.general",
                        "Ошибка базы данных при сохранении данных"
                );
        }
    }

    /**
     * Добавляет номер строки к сообщению об ошибке.
     * Пример: "Строка 132: Значение слишком длинное..."
     *
     * @param rowNumber номер строки в батче
     * @param errorMessage сообщение об ошибке
     * @return сообщение с номером строки или без него
     */
    public String formatWithRowNumber(Long rowNumber, String errorMessage) {
        if (rowNumber != null) {
            return messageService.getMessageOrDefault(
                    "import.error.batch.row",
                    String.format("Строка %d: %s", rowNumber, errorMessage)
            );
        }
        return errorMessage;
    }

    /**
     * Форматирует полное сообщение об ошибке с номером строки.
     * Комбинирует formatDatabaseError() и formatWithRowNumber().
     *
     * @param error распарсенная информация об ошибке
     * @return полное понятное сообщение с номером строки
     */
    public String formatFullError(ParsedDatabaseError error) {
        String errorMessage = formatDatabaseError(error);
        return formatWithRowNumber(error.getRowNumber(), errorMessage);
    }

    /**
     * Переводит техническое имя колонки БД в понятное название поля.
     * Использует статический маппинг из COLUMN_NAME_MAP.
     *
     * @param dbColumnName техническое имя колонки (например, "product_name")
     * @return понятное название поля (например, "Название товара")
     */
    private String translateColumnName(String dbColumnName) {
        if (dbColumnName == null) {
            return "неизвестное поле";
        }

        return COLUMN_NAME_MAP.getOrDefault(dbColumnName, dbColumnName);
    }
}
