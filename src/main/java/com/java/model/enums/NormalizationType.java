package com.java.model.enums;

/**
 * Типы нормализации данных при экспорте
 */
public enum NormalizationType {
    /**
     * Нормализация объемов: 0.7л → 0.7
     */
    VOLUME,
    
    /**
     * Нормализация брендов: Macallan, Edition №5 → Macallan
     */
    BRAND,
    
    /**
     * Нормализация валют: $100, 100USD → 100
     */
    CURRENCY,
    
    /**
     * Пользовательские правила нормализации
     */
    CUSTOM
}