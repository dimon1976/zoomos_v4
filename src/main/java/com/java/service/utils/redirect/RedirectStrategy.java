package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;

public interface RedirectStrategy {
    RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs);
    boolean canHandle(String url, PageStatus previousStatus);
    int getPriority();
    String getStrategyName();

    /**
     * Возвращает true если стратегия использует headless-браузер (Playwright/Firefox).
     * Используется в fallback-логике: non-browser стратегии с OK+same URL
     * продолжают цепочку в надежде на JS-редирект в браузере.
     */
    default boolean isBrowserBased() {
        return false;
    }
}