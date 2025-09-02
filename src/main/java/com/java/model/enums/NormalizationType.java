package com.java.model.enums;

/**
 * Типы нормализации данных при экспорте
 */
public enum NormalizationType {
    /**
     * Нормализация объемов: 0.7л → 0.7
     */
    VOLUME("Объемы"),
    
    /**
     * Нормализация брендов: Macallan, Edition №5 → Macallan
     */
    BRAND("Бренды"),
    
    /**
     * Нормализация валют: $100, 100USD → 100
     */
    CURRENCY("Валюты"),
    
    /**
     * Пользовательские правила нормализации
     */
    CUSTOM("Пользовательские");
    
    private final String displayName;
    
    NormalizationType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}