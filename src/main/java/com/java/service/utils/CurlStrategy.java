package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Стратегия использующая системный curl для надежного определения HTTP редиректов.
 * Обходит ограничения Java HTTP библиотек и корректно обрабатывает все типы редиректов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurlStrategy implements AntiBlockStrategy {
    
    private final AntiBlockConfig antiBlockConfig;
    private volatile Boolean curlAvailable;
    
    @Override
    public String getStrategyName() {
        return "CurlStrategy";
    }
    
    @Override
    public int getPriority() {
        return 0; // Самый высокий приоритет - пробуем первой
    }
    
    @Override
    public boolean isAvailable() {
        if (curlAvailable == null) {
            synchronized (this) {
                if (curlAvailable == null) {
                    curlAvailable = checkCurlAvailability();
                }
            }
        }
        return curlAvailable;
    }
    
    @Override
    public RedirectCollectorService.RedirectResult processUrl(String originalUrl, int maxRedirects, int timeoutSeconds) {
        RedirectCollectorService.RedirectResult result = new RedirectCollectorService.RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        if (!isAvailable()) {
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.ERROR.name());
            return result;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            CurlResult curlResult = followRedirectsManually(originalUrl, maxRedirects, timeoutSeconds);
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            if (antiBlockConfig.isLogStrategies()) {
                log.info("URL: {} | Strategy: CurlStrategy | Status: {} | Time: {}ms | Redirects: {}", 
                        originalUrl, curlResult.httpCode, elapsedTime, curlResult.redirectCount);
            }
            
            result.setFinalUrl(curlResult.effectiveUrl != null ? curlResult.effectiveUrl : originalUrl);
            result.setRedirectCount(curlResult.redirectCount);
            result.setStatus(mapHttpStatusToPageStatus(curlResult.httpCode, curlResult.errorMessage).name());
            
        } catch (Exception e) {
            log.error("CurlStrategy critical error for URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus(PageStatus.ERROR.name());
            result.setRedirectCount(0);
        }
        
        return result;
    }
    
    /**
     * Ручное следование редиректам с подсчетом переходов
     */
    private CurlResult followRedirectsManually(String originalUrl, int maxRedirects, int timeoutSeconds) throws IOException, InterruptedException {
        String currentUrl = originalUrl;
        int redirectCount = 0;
        
        while (redirectCount <= maxRedirects) {
            CurlResult curlResult = executeCurl(currentUrl, maxRedirects, timeoutSeconds);
            
            // Если это редирект (3xx статус)
            if (isRedirectStatus(curlResult.httpCode)) {
                String location = curlResult.redirectUrl;
                if (location != null && !location.isEmpty()) {
                    // Разрешаем относительные URL
                    String nextUrl = resolveUrl(currentUrl, location);
                    redirectCount++;
                    currentUrl = nextUrl;
                    continue;
                }
            }
            
            // Не редирект - возвращаем результат с корректным количеством переходов
            curlResult.redirectCount = redirectCount;
            curlResult.effectiveUrl = currentUrl;
            return curlResult;
        }
        
        // Достигнут лимит редиректов
        CurlResult result = new CurlResult();
        result.httpCode = 0;
        result.effectiveUrl = currentUrl;
        result.redirectCount = redirectCount;
        result.errorMessage = "Max redirects exceeded";
        return result;
    }
    
    /**
     * Проверка является ли HTTP статус редиректом
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
    
    /**
     * Проверка доступности curl в системе
     */
    private boolean checkCurlAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder("curl", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            log.debug("curl не доступен в системе: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Выполнение curl команды и парсинг результата
     */
    private CurlResult executeCurl(String url, int maxRedirects, int timeoutSeconds) throws IOException, InterruptedException {
        List<String> command = buildCurlCommand(url, maxRedirects, timeoutSeconds);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();
        
        // Читаем stdout
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // Читаем stderr для ошибок
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }
        
        boolean finished = process.waitFor(timeoutSeconds + 5, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("curl процесс превысил таймаут");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("curl завершился с ошибкой: " + errorOutput.toString().trim());
        }
        
        return parseCurlOutput(output.toString());
    }
    
    /**
     * Построение команды curl с необходимыми параметрами
     * НЕ используем --max-redirs чтобы обрабатывать редиректы вручную
     */
    private List<String> buildCurlCommand(String url, int maxRedirects, int timeoutSeconds) {
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("--head"); // Только заголовки для быстроты
        command.add("--silent"); // Тихий режим
        command.add("--show-error"); // Показывать ошибки
        command.add("--max-time");
        command.add(String.valueOf(timeoutSeconds));
        // НЕ добавляем --max-redirs - будем обрабатывать вручную!
        command.add("--user-agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        command.add("--write-out");
        command.add("CURL_INFO:%{http_code}|%{url_effective}|%{redirect_url}");
        command.add(url);
        
        return command;
    }
    
    /**
     * Парсинг вывода curl и извлечение нужной информации
     */
    private CurlResult parseCurlOutput(String output) {
        CurlResult result = new CurlResult();
        
        // Ищем строку с информацией CURL_INFO
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("CURL_INFO:")) {
                String info = line.substring("CURL_INFO:".length());
                String[] parts = info.split("\\|", -1);
                
                if (parts.length >= 3) {
                    try {
                        result.httpCode = Integer.parseInt(parts[0]);
                        result.effectiveUrl = parts[1].isEmpty() ? null : parts[1];
                        result.redirectUrl = parts[2].isEmpty() ? null : parts[2]; // Здесь теперь redirect_url
                    } catch (NumberFormatException e) {
                        log.warn("Не удалось распарсить curl вывод: {}", info);
                    }
                }
                break;
            }
        }
        
        // Также ищем Location заголовок в выводе curl
        if (result.redirectUrl == null) {
            for (String line : lines) {
                if (line.toLowerCase().startsWith("location:")) {
                    result.redirectUrl = line.substring("location:".length()).trim();
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Маппинг HTTP статуса на PageStatus enum
     */
    private PageStatus mapHttpStatusToPageStatus(int httpCode, String errorMessage) {
        if (httpCode == 0) {
            if (errorMessage != null) {
                String error = errorMessage.toLowerCase();
                if (error.contains("timeout") || error.contains("timed out")) {
                    return PageStatus.BLOCKED_TIMEOUT;
                }
                if (error.contains("connection refused") || error.contains("unreachable")) {
                    return PageStatus.CONNECTION_REFUSED;
                }
                if (error.contains("could not resolve host")) {
                    return PageStatus.UNKNOWN_HOST;
                }
            }
            return PageStatus.ERROR;
        }
        
        if (httpCode >= 200 && httpCode < 300) {
            return PageStatus.SUCCESS;
        }
        
        if (httpCode == 301 || httpCode == 302 || httpCode == 303 || httpCode == 307 || httpCode == 308) {
            return PageStatus.SUCCESS; // После редиректов curl показывает финальный статус
        }
        
        if (httpCode == 401) {
            return PageStatus.BLOCKED_FORBIDDEN;
        }
        
        if (httpCode == 403) {
            return PageStatus.BLOCKED_FORBIDDEN;
        }
        
        if (httpCode == 404) {
            return PageStatus.NOT_FOUND;
        }
        
        if (httpCode == 429) {
            return PageStatus.BLOCKED_FORBIDDEN;
        }
        
        return PageStatus.ERROR;
    }
    
    /**
     * Результат выполнения curl команды
     */
    private static class CurlResult {
        int httpCode = 0;
        String effectiveUrl;
        String redirectUrl;
        int redirectCount = 0;
        String errorMessage;
    }
}