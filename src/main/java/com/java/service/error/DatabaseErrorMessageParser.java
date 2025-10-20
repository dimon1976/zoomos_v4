package com.java.service.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для парсинга технических сообщений об ошибках БД в структурированный формат.
 * Использует regex для извлечения деталей из PostgreSQL exception messages.
 */
@Service
@Slf4j
public class DatabaseErrorMessageParser {

    // Regex patterns для извлечения данных
    private static final Pattern MAX_LENGTH_PATTERN = Pattern.compile("character varying\\((\\d+)\\)");
    private static final Pattern BATCH_ENTRY_PATTERN = Pattern.compile("Batch entry (\\d+)");
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("column \"([^\"]+)\"");
    private static final Pattern CONSTRAINT_NAME_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    /**
     * Главный метод - парсит любое исключение.
     * Fail Fast стратегия: возвращает fallback вместо генерации ошибок.
     *
     * @param exception исключение для парсинга
     * @return структурированная информация об ошибке
     */
    public ParsedDatabaseError parse(Exception exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return parseDataIntegrityViolation((DataIntegrityViolationException) exception);
        }

        // Fallback для других типов исключений
        log.debug("Unrecognized exception type: {}", exception.getClass().getSimpleName());
        return ParsedDatabaseError.builder()
                .type(DatabaseErrorType.UNKNOWN)
                .originalMessage(exception.getMessage())
                .build();
    }

    /**
     * Парсинг DataIntegrityViolationException.
     * Определяет тип ошибки и извлекает релевантные данные.
     *
     * @param ex DataIntegrityViolationException
     * @return структурированная информация об ошибке
     */
    private ParsedDatabaseError parseDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage();
        if (message == null) {
            message = "";
        }

        // Парсим "значение не умещается в тип character varying(255)"
        if (message.contains("значение не умещается в тип character varying") ||
                message.contains("value too long for type character varying")) {

            Integer maxLength = extractMaxLength(message);
            String columnName = extractColumnName(message);
            Long rowNumber = extractBatchEntryNumber(message);

            log.debug("Parsed VALUE_TOO_LONG error - column: {}, maxLength: {}, rowNumber: {}",
                    columnName, maxLength, rowNumber);

            return ParsedDatabaseError.builder()
                    .type(DatabaseErrorType.VALUE_TOO_LONG)
                    .columnName(columnName)
                    .maxLength(maxLength)
                    .rowNumber(rowNumber)
                    .originalMessage(message)
                    .build();
        }

        // Парсим unique constraint violation
        if (message.contains("duplicate key value violates unique constraint") ||
                message.contains("нарушает ограничение уникальности")) {

            String constraintName = extractConstraintName(message);
            String columnName = extractColumnName(message);
            Long rowNumber = extractBatchEntryNumber(message);

            log.debug("Parsed UNIQUE_VIOLATION error - constraint: {}, column: {}, rowNumber: {}",
                    constraintName, columnName, rowNumber);

            return ParsedDatabaseError.builder()
                    .type(DatabaseErrorType.UNIQUE_VIOLATION)
                    .constraintName(constraintName)
                    .columnName(columnName)
                    .rowNumber(rowNumber)
                    .originalMessage(message)
                    .build();
        }

        // Парсим foreign key violation
        if (message.contains("violates foreign key constraint") ||
                message.contains("нарушает ограничение внешнего ключа")) {

            String constraintName = extractConstraintName(message);
            String columnName = extractColumnName(message);
            Long rowNumber = extractBatchEntryNumber(message);

            log.debug("Parsed FOREIGN_KEY_VIOLATION error - constraint: {}, column: {}, rowNumber: {}",
                    constraintName, columnName, rowNumber);

            return ParsedDatabaseError.builder()
                    .type(DatabaseErrorType.FOREIGN_KEY_VIOLATION)
                    .constraintName(constraintName)
                    .columnName(columnName)
                    .rowNumber(rowNumber)
                    .originalMessage(message)
                    .build();
        }

        // Парсим not null violation
        if (message.contains("null value in column") ||
                message.contains("значение NULL в столбце")) {

            String columnName = extractColumnName(message);
            Long rowNumber = extractBatchEntryNumber(message);

            log.debug("Parsed NOT_NULL_VIOLATION error - column: {}, rowNumber: {}",
                    columnName, rowNumber);

            return ParsedDatabaseError.builder()
                    .type(DatabaseErrorType.NOT_NULL_VIOLATION)
                    .columnName(columnName)
                    .rowNumber(rowNumber)
                    .originalMessage(message)
                    .build();
        }

        // Общее constraint violation (fallback)
        log.debug("Unrecognized DataIntegrityViolationException pattern: {}",
                message.length() > 100 ? message.substring(0, 100) + "..." : message);

        return ParsedDatabaseError.builder()
                .type(DatabaseErrorType.CONSTRAINT_VIOLATION)
                .originalMessage(message)
                .build();
    }

    /**
     * Извлекает максимальную длину поля из сообщения.
     * Пример: "character varying(255)" -> 255
     *
     * @param message сообщение об ошибке
     * @return максимальная длина или null если не найдено
     */
    private Integer extractMaxLength(String message) {
        try {
            Matcher matcher = MAX_LENGTH_PATTERN.matcher(message);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse max length from message", e);
        }
        return null;
    }

    /**
     * Извлекает имя колонки из сообщения.
     * Пример: 'column "product_name"' -> "product_name"
     *
     * @param message сообщение об ошибке
     * @return имя колонки или null если не найдено
     */
    private String extractColumnName(String message) {
        try {
            Matcher matcher = COLUMN_NAME_PATTERN.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse column name from message", e);
        }
        return null;
    }

    /**
     * Извлекает номер строки в батче из сообщения.
     * Пример: "Batch entry 132" -> 132
     *
     * @param message сообщение об ошибке
     * @return номер строки или null если не найдено
     */
    private Long extractBatchEntryNumber(String message) {
        try {
            Matcher matcher = BATCH_ENTRY_PATTERN.matcher(message);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse batch entry number from message", e);
        }
        return null;
    }

    /**
     * Извлекает имя constraint из сообщения.
     * Пример: 'constraint "unique_product_barcode"' -> "unique_product_barcode"
     *
     * @param message сообщение об ошибке
     * @return имя constraint или null если не найдено
     */
    private String extractConstraintName(String message) {
        try {
            Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.debug("Failed to parse constraint name from message", e);
        }
        return null;
    }
}
