package com.java.model.enums;

public enum ExportStrategy {
    DEFAULT("Default"),
    SIMPLE_REPORT("Simple report"),
    TASK_REPORT("AV report");

    private final String displayName;

    ExportStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}