package com.java.model.enums;

public enum EntityType {
    PRODUCT("Продукт"),
    CUSTOMER("Клиент");

    private final String displayName;

    EntityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

