package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CurlStrategy implements RedirectStrategy {
    
    private final UrlSecurityValidator urlSecurityValidator;
    
    public CurlStrategy(UrlSecurityValidator urlSecurityValidator) {
        this.urlSecurityValidator = urlSecurityValidator;
    }
    
    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "captcha", "recaptcha", "cloudflare", "access denied", 
        "blocked", "forbidden", "защита", "antibot",
        "доступ ограничен", "проверка безопасности", 
        "rate limit", "too many requests"
    );
    
    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        log.debug("Начинаем обработку URL: {} с помощью CurlStrategy", url);
        
        try {
            if (url == null || url.trim().isEmpty()) {
                return buildErrorResult(url, startTime, "URL не может быть пустым");
            }
            
            // Валидация URL на безопасность (SSRF защита)
            try {
                urlSecurityValidator.validateUrl(url);
            } catch (SecurityException e) {
                log.warn("URL заблокирован по соображениям безопасности: {} - {}", url, e.getMessage());
                return buildErrorResult(url, startTime, "Заблокирован: " + e.getMessage());
            }
            
            return followRedirectsManually(url, maxRedirects, timeoutMs, startTime);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Обработка URL прервана: {}", url, e);
            return buildErrorResult(url, startTime, "Обработка прервана");
        } catch (Exception e) {
            log.error("Ошибка выполнения curl для URL: {}", url, e);
            return buildErrorResult(url, startTime, "Ошибка curl: " + e.getMessage());
        }
    }
    
    private RedirectResult followRedirectsManually(String currentUrl, int maxRedirects, int timeoutMs, long startTime) throws Exception {
        String originalUrl = currentUrl;
        int redirectCount = 0;
        Integer initialRedirectCode = null; // Сохраняем первоначальный HTTP код редиректа
        
        for (int i = 0; i <= maxRedirects; i++) {
            log.debug("Проверяем URL (попытка {}): {}", i + 1, currentUrl);
            
            // Валидация URL на каждом редиректе для безопасности
            try {
                urlSecurityValidator.validateUrl(currentUrl);
            } catch (SecurityException e) {
                log.warn("Редирект заблокирован: {} - {}", currentUrl, e.getMessage());
                return buildErrorResult(originalUrl, startTime, "Редирект заблокирован: " + e.getMessage());
            }
            
            // Получаем заголовки (без User-Agent для обхода блокировок)
            // URL уже валидирован, безопасен для передачи в команду
            String[] headerCommand = {
                "curl", "-I", "-s", 
                "--connect-timeout", "5",
                "--max-time", String.valueOf(Math.max(1, timeoutMs / 1000)),
                currentUrl
            };
            
            ProcessBuilder pb = new ProcessBuilder(headerCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(timeoutMs + 5000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return buildErrorResult(originalUrl, startTime, "Превышен таймаут выполнения curl");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return buildErrorResult(originalUrl, startTime, "Curl завершился с ошибкой: " + output.toString());
            }
            
            String result = output.toString().trim();
            log.debug("Curl заголовки: {}", result);
            
            // Парсим HTTP код
            int httpCode = extractHttpCode(result);
            
            // Проверяем, есть ли редирект
            if (httpCode >= 300 && httpCode < 400) {
                String location = extractLocation(result);
                if (location != null && !location.isEmpty()) {
                    // Сохраняем первоначальный HTTP код редиректа
                    if (initialRedirectCode == null) {
                        initialRedirectCode = httpCode;
                    }
                    redirectCount++;
                    String nextUrl = resolveUrl(currentUrl, location);
                    log.info("Редирект {}: {} -> {} (HTTP {})", redirectCount, currentUrl, nextUrl, httpCode);
                    currentUrl = nextUrl;
                    continue;
                }
            }
            
            // Нет редиректа - это финальный URL
            long endTime = System.currentTimeMillis();
            
            log.info("URL: {} → {} (редиректов: {}, HTTP: {}, время: {}ms, стратегия: curl)", 
                    originalUrl, currentUrl, redirectCount, httpCode, endTime - startTime);
            
            PageStatus status = determineStatus(httpCode, redirectCount, "", currentUrl, originalUrl);
            
            // Используем первоначальный HTTP код редиректа, если он был, иначе финальный
            int reportHttpCode = (initialRedirectCode != null) ? initialRedirectCode : httpCode;
            
            return RedirectResult.builder()
                    .originalUrl(originalUrl)
                    .finalUrl(currentUrl)
                    .redirectCount(redirectCount)
                    .status(status)
                    .httpCode(reportHttpCode)
                    .startTime(startTime)
                    .endTime(endTime)
                    .strategy(getStrategyName())
                    .build();
        }
        
        // Слишком много редиректов
        return buildErrorResult(originalUrl, startTime, "Превышено максимальное количество редиректов: " + maxRedirects);
    }
    
    private int extractHttpCode(String curlOutput) {
        try {
            // Ищем первую строку с HTTP статусом
            String[] lines = curlOutput.split("\n");
            for (String line : lines) {
                if (line.startsWith("HTTP/") && line.contains(" ")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 2) {
                        log.debug("Найден HTTP статус: {}", parts[1]);
                        return Integer.parseInt(parts[1]);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось извлечь HTTP код из: {}", curlOutput, e);
        }
        log.warn("HTTP код не найден в выводе curl");
        return 0;
    }
    
    private String extractLocation(String curlOutput) {
        String[] lines = curlOutput.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("location:")) {
                return line.substring("location:".length()).trim();
            }
        }
        return null;
    }
    
    private String resolveUrl(String baseUrl, String location) {
        try {
            if (location.startsWith("http://") || location.startsWith("https://")) {
                return location;
            }
            
            java.net.URL base = new java.net.URL(baseUrl);
            if (location.startsWith("/")) {
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 && base.getPort() != 80 && base.getPort() != 443 ? ":" + base.getPort() : "") + 
                       location;
            } else {
                String path = base.getPath();
                if (!path.endsWith("/")) {
                    int lastSlash = path.lastIndexOf("/");
                    path = path.substring(0, lastSlash + 1);
                }
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 && base.getPort() != 80 && base.getPort() != 443 ? ":" + base.getPort() : "") + 
                       path + location;
            }
        } catch (Exception e) {
            log.error("Ошибка резолва URL: base={}, location={}", baseUrl, location, e);
            return location;
        }
    }
    
    
    private PageStatus determineStatus(int httpCode, int redirectCount, String content, 
                                     String finalUrl, String originalUrl) {
        
        // Проверяем блокировки по содержимому
        if (detectBlocking(content)) {
            log.warn("URL заблокирован антиботом: {} (обнаружены ключевые слова блокировки)", originalUrl);
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
        return true; // Primary strategy - handles everything
    }
    
    @Override
    public int getPriority() {
        return 1; // Highest priority
    }
    
    @Override
    public String getStrategyName() {
        return "curl";
    }
}