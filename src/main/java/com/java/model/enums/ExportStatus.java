package com.java.model.enums;

public enum ExportStatus {
    INITIALIZING("Инициализация"),
    PROCESSING("Обработка"),
    COMPLETED("Завершено"),
    FAILED("Ошибка"),
    CANCELLED("Отменено");

    private final String displayName;

    ExportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}