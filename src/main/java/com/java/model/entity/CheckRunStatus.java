package com.java.model.entity;

/**
 * Статус выполнения проверки выкачки (ZoomosCheckRun).
 * Хранится в БД как строка (EnumType.STRING).
 */
public enum CheckRunStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
