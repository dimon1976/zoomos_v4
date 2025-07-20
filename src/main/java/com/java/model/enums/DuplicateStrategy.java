package com.java.model.enums;

public enum DuplicateStrategy {
    ALLOW_ALL("Разрешить все дубликаты"),
    SKIP_DUPLICATES("Пропускать дубликаты");

    private final String displayName;

    DuplicateStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
