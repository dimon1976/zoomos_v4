package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Безопасная HTTP стратегия на базе OkHttp с рефлексией для избежания проблем компиляции
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SafeOkHttpStrategy implements AntiBlockStrategy {
    
    private final AntiBlockConfig antiBlockConfig;
    private Object okHttpClient;
    private boolean initialized = false;
    
    @Override
    public String getStrategyName() {
        return "SafeOkHttp";
    }
    
    @Override
    public int getPriority() {
        return 0; // Наивысший приоритет
    }
    
    @Override
    public boolean isAvailable() {
        if (!initialized) {
            initializeClient();
        }
        return okHttpClient != null;
    }
    
    /**
     * Безопасная инициализация через рефлексию
     */
    private void initializeClient() {
        try {
            // Проверяем наличие OkHttp классов
            Class<?> okHttpClientClass = Class.forName("okhttp3.OkHttpClient");
            Class<?> builderClass = Class.forName("okhttp3.OkHttpClient$Builder");
            
            // Создаем OkHttpClient через Builder
            Object builder = okHttpClientClass.getMethod("newBuilder").invoke(null);
            
            // Отключаем автоматические редиректы
            Method followRedirects = builderClass.getMethod("followRedirects", boolean.class);
            followRedirects.invoke(builder, false);
            
            Method followSslRedirects = builderClass.getMethod("followSslRedirects", boolean.class);
            followSslRedirects.invoke(builder, false);
            
            // Устанавливаем таймауты
            Class<?> durationClass = Class.forName("java.time.Duration");
            Object timeout30s = durationClass.getMethod("ofSeconds", long.class).invoke(null, 30L);
            
            Method connectTimeout = builderClass.getMethod("connectTimeout", durationClass);
            connectTimeout.invoke(builder, timeout30s);
            
            Method readTimeout = builderClass.getMethod("readTimeout", durationClass);
            readTimeout.invoke(builder, timeout30s);
            
            // Строим клиент
            Method build = builderClass.getMethod("build");
            this.okHttpClient = build.invoke(builder);
            
            initialized = true;
            
            if (antiBlockConfig.isLogStrategies()) {
                log.info("SafeOkHttpStrategy успешно инициализирована");
            }
            
        } catch (Exception e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("SafeOkHttpStrategy недоступна: {}", e.getMessage());
            }
            this.okHttpClient = null;
            initialized = true;
        }
    }
    
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        if (!isAvailable()) {
            // Fallback на простую Java реализацию
            return processUrlWithJavaHttp(originalUrl, maxRedirects, timeoutSeconds);
        }
        
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        try {
            String currentUrl = originalUrl;
            int redirectCount = 0;
            long startTime = System.currentTimeMillis();
            
            while (redirectCount <= maxRedirects) {
                // Используем рефлексию для создания Request
                Class<?> requestClass = Class.forName("okhttp3.Request");
                Class<?> requestBuilderClass = Class.forName("okhttp3.Request$Builder");
                
                Object requestBuilder = requestBuilderClass.getConstructor().newInstance();
                Method url = requestBuilderClass.getMethod("url", String.class);
                url.invoke(requestBuilder, currentUrl);
                
                // Добавляем заголовки
                Method header = requestBuilderClass.getMethod("header", String.class, String.class);
                header.invoke(requestBuilder, "User-Agent", getRandomUserAgent());
                header.invoke(requestBuilder, "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                header.invoke(requestBuilder, "Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
                
                Method build = requestBuilderClass.getMethod("build");
                Object request = build.invoke(requestBuilder);
                
                // Выполняем запрос
                Class<?> callClass = Class.forName("okhttp3.Call");
                Method newCall = okHttpClient.getClass().getMethod("newCall", requestClass);
                Object call = newCall.invoke(okHttpClient, request);
                
                Method execute = callClass.getMethod("execute");
                Object response = execute.invoke(call);
                
                try {
                    // Получаем код статуса
                    Class<?> responseClass = Class.forName("okhttp3.Response");
                    Method code = responseClass.getMethod("code");
                    int statusCode = (Integer) code.invoke(response);
                    
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    
                    if (antiBlockConfig.isLogStrategies()) {
                        log.info("URL: {} | Strategy: SafeOkHttp | Status: {} | Time: {}ms", 
                                currentUrl, statusCode, elapsedTime);
                    }
                    
                    // Проверяем редиректы
                    if (isRedirectStatus(statusCode)) {
                        Method header_get = responseClass.getMethod("header", String.class);
                        String location = (String) header_get.invoke(response, "Location");
                        
                        if (location != null && !location.isEmpty()) {
                            currentUrl = resolveUrl(currentUrl, location);
                            redirectCount++;
                            
                            if (antiBlockConfig.isLogStrategies()) {
                                log.info("SafeOkHttp redirect [{}]: {} -> {}", redirectCount, originalUrl, currentUrl);
                            }
                            continue;
                        }
                    }
                    
                    // Обрабатываем финальный результат
                    if (statusCode >= 200 && statusCode < 300) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.SUCCESS.toString());
                    } else if (statusCode == 404) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.NOT_FOUND.toString());
                    } else if (statusCode == 403 || statusCode == 401 || statusCode == 429) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.FORBIDDEN.toString());
                    } else {
                        result.setFinalUrl(currentUrl);
                        result.setStatus(PageStatus.ERROR.toString());
                    }
                    
                    result.setRedirectCount(redirectCount);
                    return result;
                    
                } finally {
                    // Закрываем response
                    try {
                        Method close = response.getClass().getMethod("close");
                        close.invoke(response);
                    } catch (Exception e) {
                        // Игнорируем ошибки закрытия
                    }
                }
            }
            
            // Достигнут лимит редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus(PageStatus.MAX_REDIRECTS.toString());
            result.setRedirectCount(redirectCount);
            
        } catch (Exception e) {
            if (antiBlockConfig.isLogStrategies()) {
                log.warn("SafeOkHttp error for URL {}: {}", originalUrl, e.getMessage());
            }
            
            result.setFinalUrl(originalUrl);
            if (e.getMessage() != null) {
                String errorMessage = e.getMessage().toLowerCase();
                if (errorMessage.contains("timeout")) {
                    result.setStatus(PageStatus.TIMEOUT.toString());
                } else if (errorMessage.contains("unknown host")) {
                    result.setStatus(PageStatus.UNKNOWN_HOST.toString());
                } else {
                    result.setStatus(PageStatus.IO_ERROR.toString());
                }
            } else {
                result.setStatus(PageStatus.ERROR.toString());
            }
            result.setRedirectCount(0);
        }
        
        return result;
    }
    
    /**
     * Fallback реализация на чистом Java HTTP
     */
    private RedirectCollectorService.RedirectResult processUrlWithJavaHttp(String originalUrl, int maxRedirects, int timeoutSeconds) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        try {
            java.net.URL url = new java.net.URL(originalUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("HEAD"); // Используем HEAD для экономии трафика
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            
            // Реалистичные заголовки
            connection.setRequestProperty("User-Agent", getRandomUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            
            int statusCode = connection.getResponseCode();
            
            if (isRedirectStatus(statusCode)) {
                String location = connection.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    result.setFinalUrl(resolveUrl(originalUrl, location));
                    result.setStatus(PageStatus.SUCCESS.toString());
                    result.setRedirectCount(1);
                } else {
                    result.setFinalUrl(originalUrl);
                    result.setStatus(PageStatus.SUCCESS.toString());
                }
            } else if (statusCode >= 200 && statusCode < 300) {
                result.setFinalUrl(originalUrl);
                result.setStatus(PageStatus.SUCCESS.toString());
            } else if (statusCode == 404) {
                result.setFinalUrl(originalUrl);
                result.setStatus(PageStatus.NOT_FOUND.toString());
            } else if (statusCode == 403 || statusCode == 401 || statusCode == 429) {
                result.setFinalUrl(originalUrl);
                result.setStatus(PageStatus.FORBIDDEN.toString());
            } else {
                result.setFinalUrl(originalUrl);
                result.setStatus(PageStatus.ERROR.toString());
            }
            
            connection.disconnect();
            
        } catch (java.net.SocketTimeoutException e) {
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.TIMEOUT.toString());
        } catch (java.net.UnknownHostException e) {
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.UNKNOWN_HOST.toString());
        } catch (Exception e) {
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.ERROR.toString());
        }
        
        return result;
    }
    
    /**
     * Получение случайного User-Agent
     */
    private String getRandomUserAgent() {
        var userAgents = antiBlockConfig.getUserAgents();
        if (userAgents.isEmpty()) {
            String[] fallbackUserAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            };
            return fallbackUserAgents[ThreadLocalRandom.current().nextInt(fallbackUserAgents.length)];
        }
        return userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size()));
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
    private String resolveUrl(String baseUrl, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            if (location.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 ? ":" + base.getPort() : "") + location;
            } else {
                java.net.URL resolved = new java.net.URL(base, location);
                return resolved.toString();
            }
        } catch (Exception e) {
            log.warn("Error resolving URL: base={}, location={}", baseUrl, location);
            return location;
        }
    }
}