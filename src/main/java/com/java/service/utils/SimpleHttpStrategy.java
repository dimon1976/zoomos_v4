package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
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
        
        // Глобально отключаем автоматические редиректы
        boolean originalFollowRedirects = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);
        
        try {
            String currentUrl = originalUrl;
            int redirectCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (redirectCount <= maxRedirects) {
                URL url = new URL(currentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Настраиваем параметры для более надежной проверки
                connection.setRequestMethod("GET"); // Используем GET для полной проверки
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
                    
                    // 1. РЕДИРЕКТЫ - проверяем статус-коды 3xx
                    if (isRedirectStatus(responseCode)) {
                        String location = connection.getHeaderField("Location");
                        if (location != null && !location.isEmpty()) {
                            String resolvedUrl = resolveUrl(currentUrl, location);
                            redirectCount++;
                            
                            if (antiBlockConfig.isLogStrategies()) {
                                log.info("SimpleHttp redirect [{}]: {} -> {}", redirectCount, currentUrl, resolvedUrl);
                            }
                            
                            currentUrl = resolvedUrl;
                            continue;
                        }
                    }
                    
                    // 2. БЛОКИРОВКИ - явные коды
                    if (responseCode == 403 || responseCode == 401 || responseCode == 429) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.FORBIDDEN.toString());
                        result.setRedirectCount(redirectCount);
                        return result;
                    }
                    
                    // 3. НЕ НАЙДЕНО
                    if (responseCode == 404) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.NOT_FOUND.toString());
                        result.setRedirectCount(redirectCount);
                        return result;
                    }
                    
                    // 4. УСПЕХ - читаем содержимое для дополнительной проверки
                    if (responseCode >= 200 && responseCode < 300) {
                        String contentStatus = checkContentForBlocking(connection);
                        
                        result.setFinalUrl(currentUrl);
                        result.setStatus(contentStatus.equals("SUCCESS") ? PageStatus.SUCCESS.toString() : PageStatus.ERROR.toString());
                        result.setRedirectCount(redirectCount);
                        return result;
                    }
                    
                    // 5. ВСЕ ОСТАЛЬНОЕ
                    result.setFinalUrl(currentUrl);
                    result.setStatus(PageStatus.ERROR.toString());
                    result.setRedirectCount(redirectCount);
                    return result;
                    
                } finally {
                    connection.disconnect();
                }
            }
            
            // Достигнут лимит редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus(PageStatus.MAX_REDIRECTS.toString());
            result.setRedirectCount(redirectCount);
            
        } catch (SocketTimeoutException e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("SimpleHttp timeout for URL {}: {}", originalUrl, e.getMessage());
            }
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.TIMEOUT.toString());
            result.setRedirectCount(0);
        } catch (IOException e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("SimpleHttp error for URL {}: {}", originalUrl, e.getMessage());
            }
            
            // Определяем тип ошибки
            String errorMessage = e.getMessage().toLowerCase();
            String status = PageStatus.ERROR.toString();
            
            if (errorMessage.contains("timeout")) {
                status = PageStatus.TIMEOUT.toString();
            } else if (errorMessage.contains("refused") || errorMessage.contains("unreachable")) {
                status = PageStatus.IO_ERROR.toString();
            } else if (errorMessage.contains("unknown host")) {
                status = PageStatus.UNKNOWN_HOST.toString();
            }
            
            result.setFinalUrl(originalUrl);
            result.setStatus(status);
            result.setRedirectCount(0);
        } catch (Exception e) {
            log.error("SimpleHttp critical error for URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.ERROR.toString());
            result.setRedirectCount(0);
        } finally {
            // Восстанавливаем глобальную настройку
            HttpURLConnection.setFollowRedirects(originalFollowRedirects);
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
     * Проверка содержимого страницы на признаки блокировок
     */
    private String checkContentForBlocking(HttpURLConnection connection) {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                int linesRead = 0;
                // Читаем только начало страницы для быстроты
                while ((line = reader.readLine()) != null && linesRead < 50) {
                    content.append(line.toLowerCase()).append(" ");
                    linesRead++;
                    if (content.length() > 5000) break; // Ограничиваем размер
                }
            }
            
            String pageContent = content.toString();
            
            // Ищем признаки блокировок и капч
            if (pageContent.contains("captcha") ||
                pageContent.contains("access denied") ||
                pageContent.contains("cloudflare") ||
                pageContent.contains("blocked") ||
                pageContent.contains("forbidden") ||
                pageContent.contains("ray id") ||
                pageContent.contains("ddos protection") ||
                pageContent.contains("security check")) {
                
                if (antiBlockConfig.isLogStrategies()) {
                    log.warn("Detected potential blocking in content for URL");
                }
                return "BLOCKED";
            }
            
            return "SUCCESS";
            
        } catch (IOException e) {
            // Если не можем прочитать содержимое, считаем успешным
            if (antiBlockConfig.isLogStrategies()) {
                log.debug("Cannot read content for blocking check: {}", e.getMessage());
            }
            return "SUCCESS";
        }
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