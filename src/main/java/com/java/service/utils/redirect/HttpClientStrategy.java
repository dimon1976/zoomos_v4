package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Fallback стратегия обработки редиректов через Java HttpClient
 * Используется когда curl и playwright недоступны
 */
@Component
@Slf4j
public class HttpClientStrategy implements RedirectStrategy {
    
    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "captcha", "recaptcha", "cloudflare", "access denied", 
        "blocked", "forbidden", "защита", "antibot",
        "доступ ограничен", "проверка безопасности"
    );
    
    private final HttpClient httpClient;
    
    public HttpClientStrategy() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        log.debug("Начинаем обработку URL: {} с помощью HttpClientStrategy", url);
        
        try {
            if (url == null || url.trim().isEmpty()) {
                return buildErrorResult(url, startTime, "URL не может быть пустым");
            }
            
            URI uri = URI.create(url.trim());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            String finalUrl = response.uri().toString();
            int httpCode = response.statusCode();
            String responseBody = response.body();
            
            // HttpClient автоматически следует редиректам, поэтому считаем их количество приблизительно
            int redirectCount = finalUrl.equals(url) ? 0 : 1;
            
            long endTime = System.currentTimeMillis();
            
            log.info("URL: {} → {} (HTTP: {}, время: {}ms, стратегия: HttpClientStrategy)", 
                    url, finalUrl, httpCode, endTime - startTime);
            
            PageStatus status = determineStatus(httpCode, redirectCount, responseBody);
            
            return RedirectResult.builder()
                    .originalUrl(url)
                    .finalUrl(finalUrl)
                    .redirectCount(redirectCount)
                    .status(status)
                    .httpCode(httpCode)
                    .startTime(startTime)
                    .endTime(endTime)
                    .strategy(getStrategyName())
                    .build();
                    
        } catch (IOException e) {
            log.error("Ошибка HTTP соединения для URL: {}", url, e);
            return buildErrorResult(url, startTime, "Ошибка соединения: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Обработка URL прервана: {}", url, e);
            return buildErrorResult(url, startTime, "Обработка прервана");
        } catch (Exception e) {
            log.error("Ошибка HttpClient для URL: {}", url, e);
            return buildErrorResult(url, startTime, "Ошибка HttpClient: " + e.getMessage());
        }
    }
    
    private PageStatus determineStatus(int httpCode, int redirectCount, String responseBody) {
        // Проверяем блокировки по содержимому
        if (detectBlocking(responseBody)) {
            log.warn("Обнаружены признаки блокировки в ответе");
            return PageStatus.BLOCKED;
        }
        
        // Определяем статус по HTTP коду
        if (httpCode >= 200 && httpCode < 300) {
            return redirectCount > 0 ? PageStatus.REDIRECT : PageStatus.OK;
        } else if (httpCode == 403 || httpCode == 429) {
            return PageStatus.BLOCKED;
        } else if (httpCode == 404 || httpCode == 410) {
            return PageStatus.NOT_FOUND;
        } else {
            return PageStatus.ERROR;
        }
    }
    
    private boolean detectBlocking(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String lowerContent = content.toLowerCase();
        return BLOCK_KEYWORDS.stream()
                .anyMatch(lowerContent::contains);
    }
    
    private RedirectResult buildErrorResult(String originalUrl, long startTime, String errorMessage) {
        long endTime = System.currentTimeMillis();
        log.error("Ошибка обработки URL {}: {}", originalUrl, errorMessage);
        
        return RedirectResult.builder()
                .originalUrl(originalUrl)
                .finalUrl(originalUrl)
                .redirectCount(0)
                .status(PageStatus.ERROR)
                .errorMessage(errorMessage)
                .startTime(startTime)
                .endTime(endTime)
                .strategy(getStrategyName())
                .build();
    }
    
    @Override
    public boolean canHandle(String url, PageStatus previousStatus) {
        return true; // Fallback strategy - handles everything as last resort
    }
    
    @Override
    public int getPriority() {
        return 3; // Lowest priority - fallback only
    }
    
    @Override
    public String getStrategyName() {
        return "HttpClientStrategy";
    }
}