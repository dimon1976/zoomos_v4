package com.java.model.entity;

public enum ConfigIssueType {
    WRONG_CITY_IDS("Неверные city_ids / address_ids"),
    WRONG_PARSER("Неверные настройки парсера"),
    SITE_CHANGED("Изменение на стороне сайта"),
    KNOWN_ISSUE("Известная проблема, в работе"),
    NOT_RELEVANT("Данные не актуальны"),
    OTHER("Другое");

    public final String label;

    ConfigIssueType(String label) {
        this.label = label;
    }
}
