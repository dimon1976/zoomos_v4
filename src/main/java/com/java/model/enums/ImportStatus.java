package com.java.model.enums;

public enum ImportStatus {
    INITIALIZING("Инициализация"),
    ANALYZING("Анализ файла"),
    VALIDATING("Валидация"),
    PROCESSING("Обработка"),
    COMPLETING("Завершение"),
    COMPLETED("Завершено"),
    FAILED("Ошибка"),
    CANCELLED("Отменено");

    private final String displayName;

    ImportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
