package com.java.exception;

/**
 * Исключение превышения квоты импорта
 */
public class ImportQuotaExceededException extends ImportException {

    private final int limit;
    private final int current;

    public ImportQuotaExceededException(String message, int limit, int current) {
        super("import.quota.exceeded", message, limit, current);
        this.limit = limit;
        this.current = current;
    }

    public int getLimit() {
        return limit;
    }

    public int getCurrent() {
        return current;
    }
}