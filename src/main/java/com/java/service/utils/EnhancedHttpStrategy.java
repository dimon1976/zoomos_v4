package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Улучшенная HTTP стратегия с Apache HttpClient для обхода блокировок
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedHttpStrategy implements AntiBlockStrategy {
    
    private final AntiBlockConfig antiBlockConfig;
    
    @Override
    public String getStrategyName() {
        return "EnhancedHttp";
    }
    
    @Override
    public int getPriority() {
        return 2; // Вторая по приоритету
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Проверяем доступность Apache HttpClient классов
            Class.forName("org.apache.hc.client5.http.impl.classic.HttpClients");
            Class.forName("org.apache.hc.client5.http.ssl.TlsSocketStrategy");
            return true;
        } catch (ClassNotFoundException e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("EnhancedHttpStrategy недоступна: отсутствуют зависимости Apache HttpClient - {}", e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Обработка URL с улучшенными HTTP заголовками и cookie management
     */
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        // Создаем cookie store для сессии
        CookieStore cookieStore = new BasicCookieStore();
        
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build()) {
                
            String currentUrl = originalUrl;
            int redirectCount = 0;
            long startTime = System.currentTimeMillis();
            String selectedUserAgent = getRandomUserAgent();
            
            while (redirectCount <= maxRedirects) {
                HttpGet request = new HttpGet(currentUrl);
                
                // Настраиваем улучшенные заголовки
                setupEnhancedHeaders(request, selectedUserAgent);
                
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    
                    // Логирование
                    if (antiBlockConfig.isLogStrategies()) {
                        log.info("URL: {} | Strategy: EnhancedHttp | Status: {} | Time: {}ms | Cookies: {}", 
                                currentUrl, statusCode, elapsedTime, cookieStore.getCookies().size());
                    }
                    
                    // Проверяем редиректы
                    if (isRedirectStatus(statusCode)) {
                        String location = response.getFirstHeader("Location") != null ? 
                                         response.getFirstHeader("Location").getValue() : null;
                        
                        if (location != null && !location.isEmpty()) {
                            currentUrl = resolveUrl(currentUrl, location);
                            redirectCount++;
                            
                            if (antiBlockConfig.isLogStrategies()) {
                                log.info("EnhancedHttp redirect: {} -> {}", originalUrl, currentUrl);
                            }
                            continue;
                        }
                    }
                    
                    // Успешно получили финальный URL
                    result.setFinalUrl(currentUrl);
                    result.setStatus(statusCode == HttpStatus.SC_SUCCESS ? PageStatus.SUCCESS.toString() : PageStatus.ERROR.toString());
                    result.setRedirectCount(redirectCount);
                    return result;
                    
                } catch (IOException e) {
                    if (antiBlockConfig.isLogStrategies()) {
                        log.warn("EnhancedHttp error for URL {}: {}", currentUrl, e.getMessage());
                    }
                    break;
                }
            }
            
            // Достигнут лимит редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus(PageStatus.MAX_REDIRECTS.toString());
            result.setRedirectCount(redirectCount);
            
        } catch (Exception e) {
            log.error("EnhancedHttp critical error for URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.ERROR.toString());
            result.setRedirectCount(0);
        }
        
        return result;
    }
    
    /**
     * Настройка улучшенных HTTP заголовков
     */
    private void setupEnhancedHeaders(HttpGet request, String userAgent) {
        request.setHeader("User-Agent", userAgent);
        request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        request.setHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
        request.setHeader("Accept-Encoding", "gzip, deflate, br");
        request.setHeader("DNT", "1");
        request.setHeader("Connection", "keep-alive");
        request.setHeader("Upgrade-Insecure-Requests", "1");
        request.setHeader("Sec-Fetch-Dest", "document");
        request.setHeader("Sec-Fetch-Mode", "navigate");
        request.setHeader("Sec-Fetch-Site", "none");
        request.setHeader("Sec-Fetch-User", "?1");
        request.setHeader("Cache-Control", "max-age=0");
        
        // Добавляем случайный Referer
        request.setHeader("Referer", getRandomReferer());
        
        // Chrome-specific headers
        if (userAgent.contains("Chrome")) {
            request.setHeader("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
            request.setHeader("sec-ch-ua-mobile", "?0");
            request.setHeader("sec-ch-ua-platform", "\"Windows\"");
        }
    }
    
    /**
     * Получение случайного User-Agent
     */
    private String getRandomUserAgent() {
        return antiBlockConfig.getUserAgents().get(
            ThreadLocalRandom.current().nextInt(antiBlockConfig.getUserAgents().size())
        );
    }
    
    /**
     * Получение случайного Referer
     */
    private String getRandomReferer() {
        String[] referers = {
            "https://www.google.com/",
            "https://www.google.ru/",
            "https://yandex.ru/",
            "https://www.bing.com/",
            "https://duckduckgo.com/"
        };
        return referers[ThreadLocalRandom.current().nextInt(referers.length)];
    }
    
    /**
     * Проверка статуса на редирект
     */
    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || 
               statusCode == 307 || statusCode == 308;
    }
    
    /**
     * Разрешение относительных URL (упрощенная версия)
     */
    private String resolveUrl(String baseUrl, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            if (url.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 ? ":" + base.getPort() : "") + url;
            } else {
                java.net.URL resolved = new java.net.URL(base, url);
                return resolved.toString();
            }
        } catch (Exception e) {
            log.warn("Error resolving URL: base={}, url={}", baseUrl, url);
            return url;
        }
    }
}