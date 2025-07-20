package com.java.exception;

/**
 * Константы сообщений об ошибках
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Утилитный класс
    }

    // Общие ошибки
    public static final String GENERAL_ERROR = "Произошла ошибка при выполнении операции";
    public static final String RESOURCE_NOT_FOUND = "Запрашиваемый ресурс не найден";
    public static final String ACCESS_DENIED = "Доступ запрещен";

    // Ошибки импорта
    public static final String IMPORT_FAILED = "Ошибка импорта данных";
    public static final String FILE_NOT_FOUND = "Файл не найден";
    public static final String INVALID_FILE_FORMAT = "Неверный формат файла";
    public static final String FILE_TOO_LARGE = "Размер файла превышает допустимый";
    public static final String ENCODING_NOT_DETECTED = "Не удалось определить кодировку файла";

    // Ошибки шаблонов
    public static final String TEMPLATE_NOT_FOUND = "Шаблон импорта не найден";
    public static final String TEMPLATE_NAME_EXISTS = "Шаблон с таким именем уже существует";
    public static final String TEMPLATE_VALIDATION_FAILED = "Ошибка валидации шаблона";
    public static final String TEMPLATE_FIELDS_REQUIRED = "Необходимо указать хотя бы одно поле";

    // Ошибки валидации
    public static final String REQUIRED_FIELD_EMPTY = "Обязательное поле не заполнено";
    public static final String INVALID_DATE_FORMAT = "Неверный формат даты";
    public static final String INVALID_NUMBER_FORMAT = "Неверный формат числа";
    public static final String DUPLICATE_RECORD = "Найден дубликат записи";

    // Ошибки обработки
    public static final String PROCESSING_FAILED = "Ошибка обработки данных";
    public static final String TRANSFORMATION_FAILED = "Ошибка преобразования данных";
    public static final String PERSISTENCE_FAILED = "Ошибка сохранения данных";
    public static final String ROLLBACK_FAILED = "Ошибка отката изменений";

    // Системные ошибки
    public static final String OUT_OF_MEMORY = "Недостаточно памяти для выполнения операции";
    public static final String OPERATION_TIMEOUT = "Превышено время выполнения операции";
    public static final String CONCURRENT_MODIFICATION = "Данные были изменены другим пользователем";

    /**
     * Форматирует сообщение об ошибке с параметрами
     */
    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
}