package com.java.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация кэширования для улучшения производительности
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Настраивает кэш-менеджер для статистики и других данных
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Предварительно создаем кэши для статистики
        cacheManager.setCacheNames(java.util.Arrays.asList(
            // Statistics caches
            "aggregatedStats",      // Агрегированная статистика
            "topGroups",           // Топ группы
            "dailyTrends",         // Дневные тренды
            "periodStats",         // Статистика за период
            "uniqueGroupValues",   // Уникальные значения групп
            "uniqueCountFields",   // Уникальные поля подсчета
            
            // Field values caches (уже существующие)
            "fieldValues",         // Значения полей
            "fieldValuesCount",    // Количество значений полей
            "availableFields"      // Доступные поля
        ));
        
        // Разрешаем создание кэшей на лету
        cacheManager.setAllowNullValues(false);
        
        return cacheManager;
    }
}