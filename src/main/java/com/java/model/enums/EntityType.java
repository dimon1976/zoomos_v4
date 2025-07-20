package com.java.model.enums;

public enum EntityType {
    AV_DATA("Азбука данные"),
    AV_HANDBOOK("Азбука справочник");

    private final String displayName;

    EntityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

