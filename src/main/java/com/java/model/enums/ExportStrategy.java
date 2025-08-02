package com.java.model.enums;

public enum ExportStrategy {
    DEFAULT("По умолчанию"),
    TASK_REPORT("Задание-Отчет");

    private final String displayName;

    ExportStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}