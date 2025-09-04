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
            
            String[] command = {
                "curl", "-L", "-s", 
                "-w", "\n---CURL-METADATA---\n%{url_effective}|%{num_redirects}|%{http_code}",
                "--connect-timeout", "5",
                "--max-time", String.valueOf(timeoutMs / 1000),
                "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "--max-redirs", String.valueOf(maxRedirects),
                "--insecure",
                url
            };
            
            log.debug("Выполняем команду curl: {}", String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
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
                return buildErrorResult(url, startTime, "Превышен таймаут выполнения curl");
            }
            
            int exitCode = process.exitValue();
            String result = output.toString().trim();
            
            log.debug("Curl завершен с кодом: {}, результат: {}", exitCode, result);
            
            if (exitCode != 0) {
                return buildErrorResult(url, startTime, "Curl завершился с ошибкой: " + result);
            }
            
            return parseResult(url, result, startTime);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Обработка URL прервана: {}", url, e);
            return buildErrorResult(url, startTime, "Обработка прервана");
        } catch (Exception e) {
            log.error("Ошибка выполнения curl для URL: {}", url, e);
            return buildErrorResult(url, startTime, "Ошибка curl: " + e.getMessage());
        }
    }
    
    private RedirectResult parseResult(String originalUrl, String curlOutput, long startTime) {
        try {
            String content = "";
            String metadata = "";
            
            // Разделяем контент и метаданные по маркеру
            int metadataIndex = curlOutput.indexOf("---CURL-METADATA---");
            if (metadataIndex != -1) {
                content = curlOutput.substring(0, metadataIndex).trim();
                metadata = curlOutput.substring(metadataIndex + "---CURL-METADATA---".length()).trim();
            } else {
                // Fallback: пробуем найти последнюю строку с тремя частями
                String[] lines = curlOutput.split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].split("\\|").length == 3) {
                        metadata = lines[i];
                        content = String.join("\n", java.util.Arrays.copyOf(lines, i));
                        break;
                    }
                }
            }
            
            if (metadata.isEmpty()) {
                log.warn("Не найдены метаданные curl для URL: {}", originalUrl);
                return buildErrorResult(originalUrl, startTime, "Не найдены метаданные curl");
            }
            
            // Парсим метаданные: finalUrl|redirectCount|httpCode
            String[] parts = metadata.split("\\|");
            if (parts.length != 3) {
                log.warn("Неожиданный формат метаданных curl: {}", metadata);
                return buildErrorResult(originalUrl, startTime, "Неожиданный формат метаданных");
            }
            
            String finalUrl = parts[0].trim();
            int redirectCount = Integer.parseInt(parts[1].trim());
            int httpCode = Integer.parseInt(parts[2].trim());
            
            long endTime = System.currentTimeMillis();
            
            log.info("URL: {} → {} (редиректов: {}, HTTP: {}, время: {}ms, стратегия: curl)", 
                    originalUrl, finalUrl, redirectCount, httpCode, endTime - startTime);
            
            PageStatus status = determineStatus(httpCode, redirectCount, content, finalUrl, originalUrl);
            
            return RedirectResult.builder()
                    .originalUrl(originalUrl)
                    .finalUrl(finalUrl)
                    .redirectCount(redirectCount)
                    .status(status)
                    .httpCode(httpCode)
                    .startTime(startTime)
                    .endTime(endTime)
                    .strategy(getStrategyName())
                    .build();
                    
        } catch (Exception e) {
            log.error("Ошибка парсинга результата curl для URL: {}", originalUrl, e);
            return buildErrorResult(originalUrl, startTime, "Ошибка парсинга: " + e.getMessage());
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