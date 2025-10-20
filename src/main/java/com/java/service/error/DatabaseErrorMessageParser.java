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

    // Pattern для извлечения имени колонки и значения из SQL statement
    // Пример: "INSERT INTO av_data (...product_name...) VALUES (...('long value')...)"
    private static final Pattern BATCH_INSERT_PATTERN = Pattern.compile(
            "Batch entry \\d+ INSERT INTO av_data \\(([^)]+)\\) VALUES \\((.+)\\) was aborted"
    );

    // Pattern для извлечения значения из VALUES с учетом escaped кавычек
    // Ищет значение в формате ('value') с учетом вложенных кавычек
    private static final Pattern VALUE_IN_QUOTES_PATTERN = Pattern.compile("\\('([^']*(?:''[^']*)*)'\\)");

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

        // Пробуем найти DataIntegrityViolationException в цепочке вложенных исключений
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException) {
                return parseDataIntegrityViolation((DataIntegrityViolationException) cause);
            }
            cause = cause.getCause();
        }

        // Проверяем сообщение на наличие характерных PostgreSQL ошибок даже без DataIntegrityViolationException
        String message = extractFullExceptionMessage(exception);
        if (message != null && (message.contains("значение не умещается в тип character varying") ||
                message.contains("value too long for type character varying"))) {
            return parseValueTooLongError(message);
        }

        // Fallback для других типов исключений
        log.debug("Unrecognized exception type: {}", exception.getClass().getSimpleName());
        return ParsedDatabaseError.builder()
                .type(DatabaseErrorType.UNKNOWN)
                .originalMessage(exception.getMessage())
                .build();
    }

    /**
     * Извлекает полное сообщение из цепочки исключений
     */
    private String extractFullExceptionMessage(Exception exception) {
        StringBuilder fullMessage = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                fullMessage.append(current.getMessage()).append(" ");
            }
            current = current.getCause();
        }
        return fullMessage.toString();
    }

    /**
     * Парсинг ошибки "значение не умещается" из строкового сообщения
     */
    private ParsedDatabaseError parseValueTooLongError(String message) {
        Integer maxLength = extractMaxLength(message);
        String columnName = extractColumnName(message);
        Long rowNumber = extractBatchEntryNumber(message);

        // Если columnName не найдено стандартным способом, пробуем извлечь из SQL statement
        if (columnName == null) {
            BatchInsertInfo batchInfo = extractBatchInsertInfo(message);
            if (batchInfo != null) {
                columnName = batchInfo.getColumnName();
                Integer actualLength = batchInfo.getValueLength();

                log.debug("Parsed VALUE_TOO_LONG error from nested exception - column: {}, actualLength: {}, maxLength: {}, rowNumber: {}",
                        columnName, actualLength, maxLength, rowNumber);

                return ParsedDatabaseError.builder()
                        .type(DatabaseErrorType.VALUE_TOO_LONG)
                        .columnName(columnName)
                        .actualLength(actualLength)
                        .maxLength(maxLength)
                        .rowNumber(rowNumber)
                        .originalMessage(message)
                        .build();
            }
        }

        log.debug("Parsed VALUE_TOO_LONG error from nested exception - column: {}, maxLength: {}, rowNumber: {}",
                columnName, maxLength, rowNumber);

        return ParsedDatabaseError.builder()
                .type(DatabaseErrorType.VALUE_TOO_LONG)
                .columnName(columnName)
                .maxLength(maxLength)
                .rowNumber(rowNumber)
                .originalMessage(message)
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
            return parseValueTooLongError(message);
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

    /**
     * Извлекает информацию о колонке и значении из SQL INSERT statement в batch error.
     * Определяет, какая именно колонка вызвала ошибку "value too long".
     *
     * @param message полное сообщение об ошибке с SQL statement
     * @return информация о проблемной колонке или null
     */
    private BatchInsertInfo extractBatchInsertInfo(String message) {
        try {
            Matcher matcher = BATCH_INSERT_PATTERN.matcher(message);
            if (!matcher.find()) {
                log.debug("Failed to match BATCH_INSERT_PATTERN");
                return null;
            }

            String columnsStr = matcher.group(1);
            String valuesStr = matcher.group(2);

            // Разбиваем колонки
            String[] columns = columnsStr.split(",\\s*");

            // Разбиваем значения, учитывая что внутри могут быть запятые
            java.util.List<String> values = extractValues(valuesStr);

            if (columns.length != values.size()) {
                log.debug("Column count ({}) != value count ({})", columns.length, values.size());
                return null;
            }

            // Ищем первое слишком длинное значение
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                // Проверяем длину (убираем обрамляющие скобки и кавычки)
                String cleanValue = cleanValue(value);
                if (cleanValue != null && cleanValue.length() > 255) {
                    String columnName = columns[i].trim();
                    log.debug("Found long value in column {}: {} chars", columnName, cleanValue.length());
                    return new BatchInsertInfo(columnName, cleanValue.length());
                }
            }

            log.debug("No value longer than 255 chars found");
            return null;

        } catch (Exception e) {
            log.debug("Failed to extract batch insert info", e);
            return null;
        }
    }

    /**
     * Извлекает значения из VALUES части SQL statement.
     * Учитывает вложенные кавычки и запятые внутри значений.
     *
     * @param valuesStr строка с VALUES
     * @return список значений
     */
    private java.util.List<String> extractValues(String valuesStr) {
        java.util.List<String> values = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inQuotes = false;

        for (int i = 0; i < valuesStr.length(); i++) {
            char c = valuesStr.charAt(i);

            if (c == '\'' && (i == 0 || valuesStr.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (c == '(' && !inQuotes) {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == ')' && !inQuotes) {
                depth--;
                if (depth == 0) {
                    values.add(valuesStr.substring(start, i + 1));
                }
            }
        }

        return values;
    }

    /**
     * Очищает значение от обрамляющих скобок, кавычек и type cast.
     * Пример: "('some text')" -> "some text"
     * Пример: "('123'::int8)" -> "123"
     *
     * @param value строка значения из SQL
     * @return очищенное значение или null
     */
    private String cleanValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Убираем внешние скобки
        String cleaned = value.trim();
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        // Убираем type cast (например, ::int8, ::numeric)
        int castIndex = cleaned.indexOf("::");
        if (castIndex > 0) {
            cleaned = cleaned.substring(0, castIndex);
        }

        // Убираем кавычки
        cleaned = cleaned.trim();
        if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        // Если значение NULL
        if ("NULL".equalsIgnoreCase(cleaned)) {
            return null;
        }

        return cleaned;
    }

    /**
     * DTO для хранения информации о проблемной колонке в batch insert.
     */
    private static class BatchInsertInfo {
        private final String columnName;
        private final Integer valueLength;

        public BatchInsertInfo(String columnName, Integer valueLength) {
            this.columnName = columnName;
            this.valueLength = valueLength;
        }

        public String getColumnName() {
            return columnName;
        }

        public Integer getValueLength() {
            return valueLength;
        }
    }
}
