package com.java.model.entity;

/**
 * Тип проверки выкачки (ZoomosParsingStats).
 * API — полная выкачка через API, ITEM — по карточкам из кабинета.
 * Хранится в БД как строка (EnumType.STRING).
 */
public enum ZoomosCheckType {
    API,
    ITEM
}
