package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Стратегия обработки редиректов через Playwright
 * Используется для обхода антиботных систем когда CurlStrategy заблокирован
 */
@Component
@Slf4j
public class PlaywrightStrategy implements RedirectStrategy {
    
    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        log.debug("Начинаем обработку URL: {} с помощью PlaywrightStrategy", url);
        log.warn("PlaywrightStrategy еще не реализован - возвращаем fallback результат");
        
        // TODO: Временная заглушка - будет реализована при необходимости
        // Для MVP достаточно CurlStrategy + HttpClientStrategy
        
        return RedirectResult.builder()
                .originalUrl(url)
                .finalUrl(url)
                .redirectCount(0)
                .status(PageStatus.ERROR)
                .errorMessage("PlaywrightStrategy пока не реализован")
                .startTime(startTime)
                .endTime(System.currentTimeMillis())
                .strategy(getStrategyName())
                .build();
    }
    
    @Override
    public boolean canHandle(String url, PageStatus previousStatus) {
        // Используется только при блокировке основной стратегии
        return previousStatus == PageStatus.BLOCKED;
    }
    
    @Override
    public int getPriority() {
        return 2; // Вторая по приоритету после curl
    }
    
    @Override
    public String getStrategyName() {
        return "playwright";
    }
}