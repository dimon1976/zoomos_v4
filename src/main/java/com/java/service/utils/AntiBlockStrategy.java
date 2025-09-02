package com.java.service.utils;

/**
 * Стратегия для обхода блокировок при сборе редиректов
 */
public interface AntiBlockStrategy {
    
    /**
     * Имя стратегии для логирования
     */
    String getStrategyName();
    
    /**
     * Приоритет стратегии (чем меньше число, тем выше приоритет)
     */
    int getPriority();
    
    /**
     * Проверка доступности стратегии
     */
    boolean isAvailable();
    
    /**
     * Обработка URL с попыткой обхода блокировок
     */
    RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds);
}