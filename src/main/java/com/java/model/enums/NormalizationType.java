package com.java.model.enums;

/**
 * Типы нормализации данных при экспорте
 */
public enum NormalizationType {
    
    /**
     * Нормализация объемов: "0.7л", "0,7 л.", "0.7" → "0.7"
     */
    VOLUME("Объем"),
    
    /**
     * Нормализация брендов: "The Macallan", "Macallan, Edition №5" → "Macallan"
     */
    BRAND("Бренд"),
    
    /**
     * Нормализация валют: "$100", "100USD" → "100"
     */
    CURRENCY("Валюта"),
    
    /**
     * Пользовательские правила через справочник
     */
    CUSTOM("Пользовательские правила");
    
    private final String displayName;
    
    NormalizationType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}