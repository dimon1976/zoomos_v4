package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Простая HTTP стратегия с базовыми заголовками
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleHttpStrategy implements AntiBlockStrategy {
    
    private final AntiBlockConfig antiBlockConfig;
    
    @Override
    public String getStrategyName() {
        return "SimpleHttp";
    }
    
    @Override
    public int getPriority() {
        return 1; // Наивысший приоритет - пробуем сначала
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Всегда доступна
    }
    
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        try {
            String currentUrl = originalUrl;
            int redirectCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (redirectCount <= maxRedirects) {
                URL url = new URL(currentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Настраиваем базовые параметры
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeoutSeconds * 1000);
                connection.setReadTimeout(timeoutSeconds * 1000);
                connection.setInstanceFollowRedirects(false); // Обрабатываем редиректы вручную
                
                // Базовые заголовки
                setupBasicHeaders(connection);
                
                try {
                    int responseCode = connection.getResponseCode();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    
                    if (antiBlockConfig.isLogStrategies()) {
                        log.info("URL: {} | Strategy: SimpleHttp | Status: {} | Time: {}ms", 
                                currentUrl, responseCode, elapsedTime);
                    }
                    
                    // Проверяем редиректы
                    if (isRedirectStatus(responseCode)) {
                        String location = connection.getHeaderField("Location");
                        if (location != null && !location.isEmpty()) {
                            currentUrl = resolveUrl(currentUrl, location);
                            redirectCount++;
                            
                            if (antiBlockConfig.isLogStrategies()) {
                                log.info("SimpleHttp redirect: {} -> {}", originalUrl, currentUrl);
                            }
                            continue;
                        }
                    }
                    
                    // Финальный результат
                    result.setFinalUrl(currentUrl);
                    result.setStatus(responseCode == 200 ? "SUCCESS" : "HTTP_" + responseCode);
                    result.setRedirectCount(redirectCount);
                    return result;
                    
                } finally {
                    connection.disconnect();
                }
            }
            
            // Достигнут лимит редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus("MAX_REDIRECTS");
            result.setRedirectCount(redirectCount);
            
        } catch (IOException e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("SimpleHttp error for URL {}: {}", originalUrl, e.getMessage());
            }
            
            // Определяем тип ошибки
            String errorMessage = e.getMessage().toLowerCase();
            String status = "ERROR";
            
            if (errorMessage.contains("timeout")) {
                status = "TIMEOUT";
            } else if (errorMessage.contains("refused") || errorMessage.contains("unreachable")) {
                status = "CONNECTION_REFUSED";
            } else if (errorMessage.contains("unknown host")) {
                status = "UNKNOWN_HOST";
            }
            
            result.setFinalUrl(originalUrl);
            result.setStatus(status);
            result.setRedirectCount(0);
        } catch (Exception e) {
            log.error("SimpleHttp critical error for URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus("ERROR");
            result.setRedirectCount(0);
        }
        
        return result;
    }
    
    /**
     * Настройка базовых HTTP заголовков
     */
    private void setupBasicHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", 
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
    }
    
    /**
     * Проверка статуса на редирект
     */
    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || 
               statusCode == 307 || statusCode == 308;
    }
    
    /**
     * Разрешение относительных URL
     */
    private String resolveUrl(String baseUrl, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        
        try {
            URL base = new URL(baseUrl);
            if (url.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 ? ":" + base.getPort() : "") + url;
            } else {
                URL resolved = new URL(base, url);
                return resolved.toString();
            }
        } catch (Exception e) {
            log.warn("Error resolving URL: base={}, url={}", baseUrl, url);
            return url;
        }
    }
}