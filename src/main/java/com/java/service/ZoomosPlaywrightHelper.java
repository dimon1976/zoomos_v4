package com.java.service;

import com.java.config.ZoomosConfig;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ZoomosPlaywrightHelper {

    private final ZoomosConfig config;

    /**
     * Навигация по URL с retry при timeout. Ждёт NETWORKIDLE после загрузки.
     * Использует config.retryAttempts и config.retryDelaySeconds.
     */
    public void navigateWithRetry(Page page, String url) {
        int maxAttempts = config.getRetryAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                return;
            } catch (Exception e) {
                if (!isTimeoutException(e)) throw e;
                if (attempt == maxAttempts) {
                    log.warn("Playwright timeout для {} — все {} попытки исчерпаны", url, maxAttempts);
                    throw e;
                }
                log.warn("Playwright timeout (попытка {}/{}), повтор через {}с... URL: {}",
                        attempt, maxAttempts, config.getRetryDelaySeconds(), url);
                try {
                    Thread.sleep(config.getRetryDelaySeconds() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    public boolean isTimeoutException(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("Timeout") || msg.contains("timeout"))) return true;
        if (e.getCause() != null) {
            String cause = e.getCause().getMessage();
            return cause != null && (cause.contains("Timeout") || cause.contains("timeout"));
        }
        return false;
    }

    /**
     * Извлекает имя аккаунта из строки вида "[ID] название@аккаунта" или просто "название@аккаунта".
     * Возвращает null если строка пустая или null.
     */
    public String parseAccountName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        if (raw.startsWith("[")) {
            int closeBracket = raw.indexOf(']');
            if (closeBracket != -1 && closeBracket + 1 < raw.length()) {
                return raw.substring(closeBracket + 1).trim();
            }
        }
        return raw;
    }
}
