package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для управления стратегиями обхода блокировок
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AntiBlockService {
    
    private final AntiBlockConfig antiBlockConfig;
    private final List<AntiBlockStrategy> strategies;
    
    /**
     * Инициализация с логированием доступных стратегий
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("🚀 Инициализация AntiBlockService");
        log.info("📋 Всего стратегий: {}", strategies.size());
        
        for (int i = 0; i < strategies.size(); i++) {
            AntiBlockStrategy strategy = strategies.get(i);
            try {
                boolean available = strategy.isAvailable();
                log.info("{}. {} - приоритет: {}, доступна: {}", 
                        i + 1, strategy.getStrategyName(), strategy.getPriority(), available);
            } catch (Exception e) {
                log.warn("{}. {} - ОШИБКА при проверке: {}", 
                        i + 1, strategy.getStrategyName(), e.getMessage());
            }
        }
    }
    
    /**
     * Обработка URL с автоматическим переключением стратегий при блокировках
     */
    public RedirectCollectorService.RedirectResult processUrlWithFallback(String originalUrl, int maxRedirects, int timeoutSeconds) {
        return processUrlWithFallback(originalUrl, maxRedirects, timeoutSeconds, 0, 0);
    }
    
    /**
     * Обработка URL с отображением прогресса
     */
    public RedirectCollectorService.RedirectResult processUrlWithFallback(String originalUrl, int maxRedirects, int timeoutSeconds, int current, int total) {
        String progressInfo = total > 0 ? String.format(" [%d/%d - %.1f%%]", current + 1, total, ((current + 1) * 100.0) / total) : "";
        
        if (antiBlockConfig.isLogStrategies()) {
            log.info("🔄 Начинается обработка URL{}: {} с использованием {} стратегий", progressInfo, originalUrl, strategies.size());
        }
        
        // Сортируем стратегии по приоритету с безопасной проверкой доступности
        List<AntiBlockStrategy> sortedStrategies = new ArrayList<>();
        for (AntiBlockStrategy strategy : strategies) {
            try {
                if (strategy.isAvailable()) {
                    sortedStrategies.add(strategy);
                }
            } catch (Exception e) {
                log.warn("Ошибка при проверке доступности стратегии {}: {}", 
                        strategy.getClass().getSimpleName(), e.getMessage());
            }
        }
        
        // Сортируем по приоритету
        sortedStrategies.sort((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()));
        
        if (sortedStrategies.isEmpty()) {
            log.error("Нет доступных стратегий для обработки URL: {}", originalUrl);
            return createErrorResult(originalUrl, "NO_STRATEGIES");
        }
        
        RedirectCollectorService.RedirectResult lastResult = null;
        
        for (AntiBlockStrategy strategy : sortedStrategies) {
            long startTime = System.currentTimeMillis();
            
            try {
                if (antiBlockConfig.isLogStrategies()) {
                    log.info("🔧 {}| Попытка стратегии: {}", progressInfo.isEmpty() ? originalUrl : progressInfo + " " + originalUrl, strategy.getStrategyName());
                }
                
                RedirectCollectorService.RedirectResult result = strategy.processUrl(originalUrl, maxRedirects, timeoutSeconds);
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                if (isSuccessfulResult(result)) {
                    if (antiBlockConfig.isLogStrategies()) {
                        log.info("✅ {}| Strategy: {} | Status: SUCCESS | Time: {}ms | Final: {} | Redirects: {}", 
                                progressInfo.isEmpty() ? originalUrl : progressInfo + " " + originalUrl, 
                                strategy.getStrategyName(), elapsedTime, 
                                result.getFinalUrl(), result.getRedirectCount());
                    }
                    return result;
                }
                
                // Проверяем на блокировку
                if (isBlockedResult(result)) {
                    if (antiBlockConfig.isLogStrategies()) {
                        log.warn("🚫 {}| Strategy: {} | Status: BLOCKED_{} | Time: {}ms | Fallback к следующей стратегии", 
                                progressInfo.isEmpty() ? originalUrl : progressInfo + " " + originalUrl, 
                                strategy.getStrategyName(), result.getStatus(), elapsedTime);
                    }
                    lastResult = result;
                    continue;
                }
                
                // Другие ошибки
                if (antiBlockConfig.isLogStrategies()) {
                    log.warn("⚠️ {}| Strategy: {} | Status: {} | Time: {}ms | Пробуем следующую стратегию", 
                            progressInfo.isEmpty() ? originalUrl : progressInfo + " " + originalUrl,
                            strategy.getStrategyName(), result.getStatus(), elapsedTime);
                }
                lastResult = result;
                
            } catch (Exception e) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                log.error("URL: {} | Strategy: {} | Exception: {} | Time: {}ms", 
                        originalUrl, strategy.getStrategyName(), e.getMessage(), elapsedTime);
                
                if (lastResult == null) {
                    lastResult = createErrorResult(originalUrl, "STRATEGY_ERROR");
                }
            }
            
            // Небольшая пауза между стратегиями
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Все стратегии не сработали
        log.error("❌ {}| Все стратегии провалились | Final status: {}", 
                progressInfo.isEmpty() ? originalUrl : progressInfo + " " + originalUrl, 
                lastResult != null ? lastResult.getStatus() : "UNAVAILABLE");
        
        return lastResult != null ? lastResult : createErrorResult(originalUrl, "ALL_STRATEGIES_FAILED");
    }
    
    /**
     * Проверка на успешный результат
     */
    private boolean isSuccessfulResult(RedirectCollectorService.RedirectResult result) {
        if (result == null) return false;
        
        String status = result.getStatus();
        return "SUCCESS".equals(status) || 
               "HTTP_200".equals(status) ||
               (status != null && status.startsWith("HTTP_2"));
    }
    
    /**
     * Проверка на блокировку
     */
    private boolean isBlockedResult(RedirectCollectorService.RedirectResult result) {
        if (result == null) return false;
        
        String status = result.getStatus();
        return "HTTP_403".equals(status) || 
               "HTTP_401".equals(status) ||
               "HTTP_429".equals(status) ||
               "BLOCKED".equals(status) ||
               "TIMEOUT".equals(status);
    }
    
    /**
     * Создание результата с ошибкой
     */
    private RedirectCollectorService.RedirectResult createErrorResult(String originalUrl, String status) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setFinalUrl(originalUrl);
        result.setStatus(status);
        result.setRedirectCount(0);
        return result;
    }
}