package com.java.model.entity;

public enum ConfigIssueType {
    MATCHING_ERRORS("Некорректные ссылки, ведут на несуществующую страницу"),
    KNOWN_ISSUE("Известная проблема, в работе"),
    NOT_RELEVANT("Данные не актуальны"),
    OTHER("Другое");

    public final String label;

    ConfigIssueType(String label) {
        this.label = label;
    }
}
