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
     * Настройки стратегий
     */
    private Strategies strategies = new Strategies();
    
    /**
     * Настройки статистики
     */
    private Statistics statistics = new Statistics();
    
    // Обратная совместимость - оставляем старые поля но помечаем deprecated
    @Deprecated
    private List<String> userAgents;
    @Deprecated
    private Boolean statisticsEnabled;
    @Deprecated  
    private Boolean logStrategies;
    @Deprecated
    private Integer simpleHttpTimeout;
    @Deprecated
    private Integer enhancedHttpTimeout;
    @Deprecated
    private Integer maxRetries;
    
    @Data
    public static class Strategies {
        private SimpleHttp simpleHttp = new SimpleHttp();
        private EnhancedHttp enhancedHttp = new EnhancedHttp();
        private ProxyHttp proxyHttp = new ProxyHttp();
        private Playwright playwright = new Playwright();
        private Selenium selenium = new Selenium();
    }
    
    @Data
    public static class SimpleHttp {
        private boolean enabled = true;
        private int timeout = 10;
        private int maxRetries = 2;
    }
    
    @Data
    public static class EnhancedHttp {
        private boolean enabled = true;
        private int timeout = 15;
        private List<String> userAgents = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:119.0) Gecko/20100101 Firefox/119.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:119.0) Gecko/20100101 Firefox/119.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );
    }
    
    @Data
    public static class ProxyHttp {
        private boolean enabled = false; // По умолчанию отключено
        private int timeout = 20;
        private List<String> proxies = Arrays.asList(); // Пустой список по умолчанию
        private boolean rotation = true;
    }
    
    @Data
    public static class Playwright {
        private boolean enabled = false; // Отключено из-за размера библиотеки
        private List<String> browsers = Arrays.asList("chromium", "firefox");
        private boolean headless = true;
        private int timeout = 30;
    }
    
    @Data  
    public static class Selenium {
        private boolean enabled = true;
        private int timeout = 60;
        private boolean headless = true;
    }
    
    @Data
    public static class Statistics {
        private boolean saveToDb = true;
        private boolean logStrategies = true;
    }
    
    // Методы для обратной совместимости
    public boolean isLogStrategies() {
        // Сначала проверяем новую структуру, потом старую
        if (statistics != null) {
            return statistics.isLogStrategies();
        }
        return logStrategies != null ? logStrategies : true;
    }
    
    public boolean isStatisticsEnabled() {
        if (statistics != null) {
            return statistics.isSaveToDb();
        }
        return statisticsEnabled != null ? statisticsEnabled : true;
    }
}