package com.java.exception;

/**
 * Исключение для ошибок утилиты Data Merger
 */
public class DataMergerException extends RuntimeException {

    public DataMergerException(String message) {
        super(message);
    }

    public DataMergerException(String message, Throwable cause) {
        super(message, cause);
    }
}