package com.java.model.enums;

import lombok.Getter;

@Getter
public enum EntityType {
    AV_DATA("Data"),
    AV_HANDBOOK("AvHandbook");

    private final String displayName;

    EntityType(String displayName) {
        this.displayName = displayName;
    }

}