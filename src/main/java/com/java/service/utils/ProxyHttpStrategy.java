package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP стратегия с прокси поддержкой для обхода региональных блокировок
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "antiblock.strategies.proxy-http.enabled", havingValue = "true", matchIfMissing = false)
public class ProxyHttpStrategy implements AntiBlockStrategy {
    
    private final AntiBlockConfig antiBlockConfig;
    private final AtomicInteger proxyIndex = new AtomicInteger(0);
    
    @Override
    public String getStrategyName() {
        return "ProxyHttp";
    }
    
    @Override
    public int getPriority() {
        return 3; // После SimpleHttp(1) и EnhancedHttp(2), до браузеров(4,5)
    }
    
    @Override
    public boolean isAvailable() {
        try {
            List<String> proxies = antiBlockConfig.getStrategies().getProxyHttp().getProxies();
            if (proxies == null || proxies.isEmpty()) {
                return false;
            }
            
            // Валидация формата прокси
            for (String proxyString : proxies) {
                if (!isValidProxyFormat(proxyString)) {
                    log.warn("Неверный формат прокси: {}", proxyString);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.debug("ProxyHttp недоступна: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        
        try {
            Proxy proxy = selectProxy();
            String userAgent = selectUserAgent();
            
            return followRedirectsWithProxy(originalUrl, proxy, userAgent, maxRedirects, timeoutSeconds, startTime);
            
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.warn("ProxyHttp ошибка для URL {}: {} (время: {}ms)", originalUrl, e.getMessage(), elapsedTime);
            
            RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
            result.setOriginalUrl(originalUrl);
            result.setFinalUrl(originalUrl);
            result.setStatus("ERROR");
            result.setRedirectCount(0);
            return result;
        }
    }
    
    /**
     * Следование редиректам с прокси используя HttpURLConnection
     */
    private RedirectCollectorService.RedirectResult followRedirectsWithProxy(
            String originalUrl, Proxy proxy, String userAgent, int maxRedirects, int timeoutSeconds, long startTime) throws IOException {
        
        String currentUrl = originalUrl;
        int redirectCount = 0;
        
        for (int i = 0; i <= maxRedirects; i++) {
            URL url = new URL(currentUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            
            try {
                // Настройка соединения
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(timeoutSeconds * 1000);
                connection.setReadTimeout(timeoutSeconds * 1000);
                connection.setRequestMethod("GET");
                
                // Заголовки
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                connection.setRequestProperty("Connection", "keep-alive");
                
                // Выполнение запроса
                int responseCode = connection.getResponseCode();
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                log.info("URL: {} | Strategy: ProxyHttp | Status: {} | Time: {}ms", 
                        currentUrl, responseCode, elapsedTime);
                
                // Проверка на блокировку
                if (responseCode == 403 || responseCode == 429) {
                    RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
                    result.setOriginalUrl(originalUrl);
                    result.setFinalUrl(currentUrl);
                    result.setStatus("BLOCKED_HTTP_" + responseCode);
                    result.setRedirectCount(redirectCount);
                    return result;
                }
                
                // Успешный ответ без редиректа
                if (responseCode >= 200 && responseCode < 300) {
                    RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
                    result.setOriginalUrl(originalUrl);
                    result.setFinalUrl(currentUrl);
                    result.setStatus("SUCCESS");
                    result.setRedirectCount(redirectCount);
                    return result;
                }
                
                // Обработка редиректа
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        break;
                    }
                    
                    // Абсолютный или относительный URL
                    String nextUrl = location.startsWith("http") ? location : 
                        currentUrl.substring(0, currentUrl.indexOf('/', 8)) + location;
                    
                    log.debug("ProxyHttp редирект {} -> {}", currentUrl, nextUrl);
                    
                    currentUrl = nextUrl;
                    redirectCount++;
                    
                    continue;
                }
                
                // Другие коды ошибок
                RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
                result.setOriginalUrl(originalUrl);
                result.setFinalUrl(currentUrl);
                result.setStatus("HTTP_" + responseCode);
                result.setRedirectCount(redirectCount);
                return result;
                    
            } finally {
                connection.disconnect();
            }
        }
        
        // Превышение лимита редиректов
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setFinalUrl(currentUrl);
        result.setStatus("TOO_MANY_REDIRECTS");
        result.setRedirectCount(redirectCount);
        return result;
    }
    
    /**
     * Выбор прокси из пула с ротацией
     */
    private Proxy selectProxy() {
        List<String> proxies = antiBlockConfig.getStrategies().getProxyHttp().getProxies();
        
        if (proxies == null || proxies.isEmpty()) {
            return Proxy.NO_PROXY;
        }
        
        // Round-robin выбор прокси
        int index = proxyIndex.getAndIncrement() % proxies.size();
        String proxyString = proxies.get(index);
        
        try {
            // Парсинг прокси формата "http://host:port" или "socks5://host:port"
            String[] parts = proxyString.split("://");
            if (parts.length != 2) {
                log.warn("Неверный формат прокси: {}", proxyString);
                return Proxy.NO_PROXY;
            }
            
            String protocol = parts[0].toLowerCase();
            String[] hostPort = parts[1].split(":");
            if (hostPort.length != 2) {
                log.warn("Неверный формат host:port в прокси: {}", proxyString);
                return Proxy.NO_PROXY;
            }
            
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            
            Proxy.Type proxyType = protocol.equals("socks5") ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            
            return new Proxy(proxyType, new InetSocketAddress(host, port));
            
        } catch (Exception e) {
            log.warn("Ошибка парсинга прокси {}: {}", proxyString, e.getMessage());
            return Proxy.NO_PROXY;
        }
    }
    
    /**
     * Выбор User-Agent из конфигурации
     */
    private String selectUserAgent() {
        List<String> userAgents = antiBlockConfig.getStrategies().getEnhancedHttp().getUserAgents();
        
        if (userAgents == null || userAgents.isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        
        int randomIndex = ThreadLocalRandom.current().nextInt(userAgents.size());
        return userAgents.get(randomIndex);
    }
    
    /**
     * Валидация формата прокси
     */
    private boolean isValidProxyFormat(String proxyString) {
        if (proxyString == null || proxyString.trim().isEmpty()) {
            return false;
        }
        
        try {
            String[] parts = proxyString.split("://");
            if (parts.length != 2) {
                return false;
            }
            
            String protocol = parts[0].toLowerCase();
            if (!protocol.equals("http") && !protocol.equals("socks5")) {
                log.warn("Неподдерживаемый протокол прокси: {}", protocol);
                return false;
            }
            
            String[] hostPort = parts[1].split(":");
            if (hostPort.length != 2) {
                return false;
            }
            
            String host = hostPort[0];
            if (host.isEmpty()) {
                return false;
            }
            
            int port = Integer.parseInt(hostPort[1]);
            if (port <= 0 || port > 65535) {
                log.warn("Неверный порт прокси: {}", port);
                return false;
            }
            
            return true;
        } catch (NumberFormatException e) {
            log.warn("Неверный формат порта в прокси: {}", proxyString);
            return false;
        } catch (Exception e) {
            log.warn("Ошибка валидации прокси {}: {}", proxyString, e.getMessage());
            return false;
        }
    }
    
    /**
     * Проверка работоспособности прокси
     */
    public boolean healthCheckProxy(String proxyString) {
        if (!isValidProxyFormat(proxyString)) {
            return false;
        }
        
        try {
            Proxy proxy = parseProxy(proxyString);
            if (proxy == null) {
                return false;
            }
            
            // Тестовый URL для проверки прокси
            String testUrl = "http://httpbin.org/ip";
            URL url = new URL(testUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 секунд таймаут для проверки
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "ProxyHealthCheck/1.0");
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            boolean isHealthy = responseCode == 200;
            log.debug("Healthcheck прокси {}: {} (код: {})", proxyString, isHealthy ? "OK" : "FAIL", responseCode);
            
            return isHealthy;
            
        } catch (Exception e) {
            log.debug("Healthcheck прокси {} провалился: {}", proxyString, e.getMessage());
            return false;
        }
    }
    
    /**
     * Парсинг строки прокси в объект Proxy
     */
    private Proxy parseProxy(String proxyString) {
        try {
            String[] parts = proxyString.split("://");
            if (parts.length != 2) {
                return null;
            }
            
            String protocol = parts[0].toLowerCase();
            String[] hostPort = parts[1].split(":");
            if (hostPort.length != 2) {
                return null;
            }
            
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            
            Proxy.Type proxyType = protocol.equals("socks5") ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(proxyType, new InetSocketAddress(host, port));
            
        } catch (Exception e) {
            log.warn("Ошибка парсинга прокси {}: {}", proxyString, e.getMessage());
            return null;
        }
    }
    
    /**
     * Проверка всех прокси при старте приложения
     */
    @jakarta.annotation.PostConstruct
    public void validateProxiesOnStartup() {
        if (!antiBlockConfig.getStrategies().getProxyHttp().isEnabled()) {
            return;
        }
        
        List<String> proxies = antiBlockConfig.getStrategies().getProxyHttp().getProxies();
        if (proxies == null || proxies.isEmpty()) {
            log.info("ProxyHttp включен, но список прокси пуст");
            return;
        }
        
        log.info("Валидация {} прокси серверов...", proxies.size());
        
        int validCount = 0;
        for (String proxyString : proxies) {
            if (isValidProxyFormat(proxyString)) {
                validCount++;
                log.debug("✅ Прокси валиден: {}", maskProxyCredentials(proxyString));
            } else {
                log.warn("❌ Прокси невалиден: {}", maskProxyCredentials(proxyString));
            }
        }
        
        log.info("Валидация завершена: {}/{} прокси прошли валидацию формата", validCount, proxies.size());
        
        if (validCount == 0) {
            log.warn("⚠️ Ни один прокси не прошел валидацию! ProxyHttpStrategy может работать некорректно");
        }
    }
    
    /**
     * Маскировка учетных данных прокси для логирования
     */
    private String maskProxyCredentials(String proxyString) {
        if (proxyString == null) return "null";
        
        // Простая маскировка - показываем только протокол и хост, скрываем порт
        try {
            String[] parts = proxyString.split("://");
            if (parts.length == 2) {
                String[] hostPort = parts[1].split(":");
                if (hostPort.length == 2) {
                    return parts[0] + "://" + hostPort[0] + ":****";
                }
            }
        } catch (Exception e) {
            // Если парсинг не удался, возвращаем замаскированную строку
        }
        
        return "****://***:****";
    }
}