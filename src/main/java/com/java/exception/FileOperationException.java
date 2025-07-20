package com.java.exception;


import lombok.Getter;

/**
 * Пользовательское исключение для операций с файлами.
 * Используется для обработки ошибок, связанных с загрузкой, чтением,
 * обработкой и другими операциями над файлами.
 */
@Getter
public class FileOperationException extends RuntimeException {

    private final String redirectUrl;
    private final String operationId;

    /**
     * Создает исключение с указанным сообщением.
     *
     * @param message сообщение об ошибке
     */
    public FileOperationException(String message) {
        this(message, null, null);
    }

    /**
     * Создает исключение с указанным сообщением и причиной.
     *
     * @param message сообщение об ошибке
     * @param cause причина исключения
     */
    public FileOperationException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * Создает исключение с указанным сообщением и URL для перенаправления.
     *
     * @param message сообщение об ошибке
     * @param redirectUrl URL для перенаправления после обработки ошибки
     */
    public FileOperationException(String message, String redirectUrl) {
        this(message, null, redirectUrl, null);
    }

    /**
     * Создает исключение с указанным сообщением, идентификатором операции и URL для перенаправления.
     *
     * @param message сообщение об ошибке
     * @param redirectUrl URL для перенаправления после обработки ошибки
     * @param operationId идентификатор операции
     */
    public FileOperationException(String message, String redirectUrl, String operationId) {
        super(message);
        this.redirectUrl = redirectUrl;
        this.operationId = operationId;
    }

    /**
     * Создает исключение с указанными параметрами.
     *
     * @param message сообщение об ошибке
     * @param cause причина исключения
     * @param redirectUrl URL для перенаправления после обработки ошибки
     * @param operationId идентификатор операции
     */
    public FileOperationException(String message, Throwable cause, String redirectUrl, String operationId) {
        super(message, cause);
        this.redirectUrl = redirectUrl;
        this.operationId = operationId;
    }
}