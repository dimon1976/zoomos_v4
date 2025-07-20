package com.java.model.enums;

public enum ErrorStrategy {
    STOP_ON_ERROR("Остановить при ошибке"),
    CONTINUE_ON_ERROR("Продолжить при ошибке");

    private final String displayName;

    ErrorStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
