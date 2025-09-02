package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Браузерная стратегия на основе Selenium для обхода блокировок
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrowserStrategy implements AntiBlockStrategy {
    
    private final BrowserService browserService;
    private final AntiBlockConfig antiBlockConfig;
    
    @Override
    public String getStrategyName() {
        return "SeleniumBrowser";
    }
    
    @Override
    public int getPriority() {
        return 3; // Третья по приоритету после HTTP стратегий
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Сначала проверим доступность Apache HttpClient для WebDriverManager
            Class.forName("org.apache.hc.client5.http.ssl.TlsSocketStrategy");
            
            // Затем проверим доступность браузера
            return browserService.isBrowserAvailable();
        } catch (ClassNotFoundException e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("BrowserStrategy недоступна: отсутствуют зависимости Apache HttpClient для WebDriverManager - {}", e.getMessage());
            }
            return false;
        } catch (Exception e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("BrowserStrategy недоступна: {}", e.getMessage());
            }
            return false;
        }
    }
    
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        try {
            if (antiBlockConfig.isLogStrategies()) {
                log.info("🌐 SeleniumBrowser: Запуск браузера для URL: {}", originalUrl);
            }
            
            long startTime = System.currentTimeMillis();
            
            BrowserService.BrowserResult browserResult = browserService.getUrlWithBrowser(originalUrl, timeoutSeconds);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            if (browserResult != null) {
                result.setFinalUrl(browserResult.getFinalUrl() != null ? browserResult.getFinalUrl() : originalUrl);
                result.setStatus(mapBrowserStatusToRedirectStatus(browserResult.getStatus()));
                result.setRedirectCount(browserResult.getRedirectCount());
                
                if (antiBlockConfig.isLogStrategies()) {
                    log.info("🌐 SeleniumBrowser: URL: {} | Status: {} | Time: {}ms | Final: {} | Redirects: {}", 
                            originalUrl, result.getStatus(), elapsedTime, 
                            result.getFinalUrl(), result.getRedirectCount());
                }
            } else {
                result.setFinalUrl(originalUrl);
                result.setStatus("BROWSER_ERROR");
                result.setRedirectCount(0);
                
                if (antiBlockConfig.isLogStrategies()) {
                    log.warn("🌐 SeleniumBrowser: URL: {} | Null result | Time: {}ms", originalUrl, elapsedTime);
                }
            }
            
        } catch (Exception e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.error("🌐 SeleniumBrowser: Ошибка для URL {}: {}", originalUrl, e.getMessage());
            }
            
            result.setFinalUrl(originalUrl);
            result.setStatus("BROWSER_EXCEPTION");
            result.setRedirectCount(0);
        }
        
        return result;
    }
    
    /**
     * Маппинг статусов браузера в статусы RedirectResult
     */
    private String mapBrowserStatusToRedirectStatus(String browserStatus) {
        if (browserStatus == null) return "BROWSER_ERROR";
        
        switch (browserStatus.toUpperCase()) {
            case "SUCCESS":
                return "SUCCESS";
            case "TIMEOUT":
                return "BROWSER_TIMEOUT";
            case "ERROR":
                return "BROWSER_ERROR";
            case "BLOCKED":
                return "HTTP_403"; // Маппим в HTTP_403 для корректной обработки в AntiBlockService
            default:
                return "BROWSER_" + browserStatus;
        }
    }
}