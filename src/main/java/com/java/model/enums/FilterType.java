package com.java.model.enums;

public enum FilterType {
    EQUALS("Равно"),
    NOT_EQUALS("Не равно"),
    CONTAINS("Содержит"),
    STARTS_WITH("Начинается с"),
    ENDS_WITH("Заканчивается на"),
    BETWEEN("Между"),
    IN("В списке"),
    IS_NULL("Пусто"),
    IS_NOT_NULL("Не пусто"),
    GREATER_THAN("Больше"),
    LESS_THAN("Меньше");

    private final String displayName;

    FilterType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
