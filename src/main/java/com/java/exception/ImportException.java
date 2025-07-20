package com.java.exception;

/**
 * Базовое исключение для системы импорта
 */
public class ImportException extends RuntimeException {

    private final String code;
    private final Object[] args;

    public ImportException(String message) {
        super(message);
        this.code = "import.error.general";
        this.args = null;
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
        this.code = "import.error.general";
        this.args = null;
    }

    public ImportException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }
}