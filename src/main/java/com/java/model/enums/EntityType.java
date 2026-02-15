package com.java.model.enums;

import lombok.Getter;

@Getter
public enum EntityType {
    AV_DATA("Data"),
    AV_HANDBOOK("AvHandbook"),
    BH_BARCODE_NAME("Справочник: штрихкод+наименование"),
    BH_NAME_URL("Справочник: наименование+URL");

    private final String displayName;

    EntityType(String displayName) {
        this.displayName = displayName;
    }

}