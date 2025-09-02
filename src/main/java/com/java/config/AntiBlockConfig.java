package com.java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Конфигурация для системы обхода блокировок
 */
@Configuration
@ConfigurationProperties(prefix = "antiblock")
@Data
public class AntiBlockConfig {
    
    /**
     * Список User-Agent для ротации
     */
    private List<String> userAgents = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:119.0) Gecko/20100101 Firefox/119.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:119.0) Gecko/20100101 Firefox/119.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    );
    
    /**
     * Включить сохранение статистики
     */
    private boolean statisticsEnabled = true;
    
    /**
     * Включить логирование стратегий
     */
    private boolean logStrategies = true;
    
    /**
     * Таймаут для простых HTTP запросов (секунды)
     */
    private int simpleHttpTimeout = 10;
    
    /**
     * Таймаут для улучшенных HTTP запросов (секунды)
     */
    private int enhancedHttpTimeout = 15;
    
    /**
     * Максимальное количество попыток для одной стратегии
     */
    private int maxRetries = 2;
}