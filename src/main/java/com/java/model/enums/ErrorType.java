package com.java.model.enums;

public enum ErrorType {
    VALIDATION_ERROR("Ошибка валидации"),
    PARSING_ERROR("Ошибка парсинга"),
    DUPLICATE_ERROR("Дубликат"),
    CONSTRAINT_ERROR("Нарушение ограничений"),
    TRANSFORMATION_ERROR("Ошибка трансформации"),
    SYSTEM_ERROR("Системная ошибка");

    private final String displayName;

    ErrorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
