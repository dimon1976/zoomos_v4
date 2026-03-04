package com.java.service.utils.redirect;

import com.java.config.ProxyConfig;
import com.java.constants.ApplicationConstants;
import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Стратегия обработки редиректов через headless Firefox (Playwright).
 * Priority 4: используется после Chrome Playwright.
 * Firefox имеет другой TLS fingerprint (JA3) — обходит WAF (Imperva, DataDome),
 * которые блокируют именно Chromium-headless.
 */
@Component
@Slf4j
public class FirefoxPlaywrightStrategy implements RedirectStrategy {

    private final UrlSecurityValidator urlSecurityValidator;
    private final ProxyConfig proxyConfig;
    private final ProxyPoolManager proxyPoolManager;

    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "captcha", "recaptcha", "cloudflare", "access denied",
        "доступ ограничен", "доступ запрещен", "временно ограничен",
        "проверка безопасности", "security check", "bot detection",
        "too many requests", "rate limit", "temporarily unavailable"
    );

    private static final String USER_AGENT = ApplicationConstants.Playwright.DEFAULT_USER_AGENT;

    public FirefoxPlaywrightStrategy(
            UrlSecurityValidator urlSecurityValidator,
            ProxyConfig proxyConfig,
            ProxyPoolManager proxyPoolManager) {
        this.urlSecurityValidator = urlSecurityValidator;
        this.proxyConfig = proxyConfig;
        this.proxyPoolManager = proxyPoolManager;
    }

    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        String originalUrl = url;

        log.debug("Начинаем обработку URL: {} с помощью FirefoxPlaywrightStrategy", url);

        try {
            urlSecurityValidator.validateUrl(url);
        } catch (SecurityException e) {
            log.warn("URL заблокирован по соображениям безопасности: {} - {}", url, e.getMessage());
            return buildErrorResult(originalUrl, startTime, "Заблокирован: " + e.getMessage());
        }

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true);

            // Добавить proxy если включен
            ProxyPoolManager.ProxyServer proxyServer = getProxyForUrl(url);
            if (proxyServer != null) {
                launchOptions.setProxy(proxyServer.getHost() + ":" + proxyServer.getPort());
                log.debug("Firefox: используется proxy: {}:{}", proxyServer.getHost(), proxyServer.getPort());
            }

            // Firefox вместо Chromium — другой TLS fingerprint (JA3)
            Browser browser = playwright.firefox().launch(launchOptions);

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(USER_AGENT)
                    .setViewportSize(
                            ApplicationConstants.Playwright.DEFAULT_VIEWPORT_WIDTH,
                            ApplicationConstants.Playwright.DEFAULT_VIEWPORT_HEIGHT
                    ));

            Page page = context.newPage();

            // Скрываем признаки автоматизации
            page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);

            String initialUrl = url;
            String finalUrl = url;
            int redirectCount = 0;
            AtomicReference<Integer> initialRedirectCode = new AtomicReference<>();

            page.onResponse(response -> {
                int statusCode = response.status();
                if ((statusCode >= 300 && statusCode < 400) && initialRedirectCode.get() == null) {
                    initialRedirectCode.set(statusCode);
                    log.debug("Firefox: сохранен первоначальный код редиректа: {}", statusCode);
                }
            });

            try {
                log.debug("Firefox: навигация к URL: {} с таймаутом {}мс", url, timeoutMs);
                Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));

                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
                } catch (Exception e) {
                    log.debug("Firefox: не удалось дождаться NETWORKIDLE, продолжаем: {}", e.getMessage());
                    try {
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs / 2));
                    } catch (Exception ex) {
                        log.debug("Firefox: не удалось дождаться DOMCONTENTLOADED: {}", ex.getMessage());
                    }
                }

                // Отслеживаем изменение URL (JS-редиректы)
                String currentUrl = page.url();

                if (!currentUrl.equals(initialUrl)) {
                    log.info("Firefox: редирект обнаружен после навигации: {} -> {}", initialUrl, currentUrl);
                    finalUrl = currentUrl;
                    redirectCount = 1;
                }

                // Дополнительное ожидание для JS-редиректов
                int maxIterations = timeoutMs / 500;
                int unchangedCount = 0;
                for (int i = 0; i < maxIterations; i++) {
                    page.waitForTimeout(500);
                    String newUrl = page.url();
                    if (!newUrl.equals(currentUrl)) {
                        log.debug("Firefox: URL изменился: {} -> {}", currentUrl, newUrl);
                        if (!newUrl.equals(initialUrl)) {
                            finalUrl = newUrl;
                            redirectCount = 1;
                        }
                        currentUrl = newUrl;
                        unchangedCount = 0;
                    } else {
                        unchangedCount++;
                        if (unchangedCount >= 12) {
                            log.debug("Firefox: URL стабилизировался");
                            break;
                        }
                    }
                }

                // Финальная проверка
                String finalCheck = page.url();
                if (!finalCheck.equals(initialUrl) && redirectCount == 0) {
                    finalUrl = finalCheck;
                    redirectCount = 1;
                }

                int finalStatusCode = response != null ? response.status() : 200;
                int reportHttpCode = initialRedirectCode.get() != null ? initialRedirectCode.get() : finalStatusCode;

                String pageContent = page.content();
                if (isBlocked(pageContent)) {
                    log.warn("Firefox: обнаружена блокировка на URL: {}", finalUrl);
                    return RedirectResult.builder()
                            .originalUrl(originalUrl)
                            .finalUrl(finalUrl)
                            .redirectCount(redirectCount)
                            .status(PageStatus.BLOCKED)
                            .errorMessage("Страница заблокирована антиботной системой (Firefox)")
                            .startTime(startTime)
                            .endTime(System.currentTimeMillis())
                            .strategy(getStrategyName())
                            .build();
                }

                PageStatus status;
                if (finalStatusCode >= 400) {
                    status = PageStatus.NOT_FOUND;
                } else if (redirectCount > 0) {
                    status = PageStatus.REDIRECT;
                } else {
                    status = PageStatus.OK;
                }

                log.info("Firefox: успешная обработка URL: {} -> {} (редиректов: {}, HTTP: {})",
                        originalUrl, finalUrl, redirectCount, reportHttpCode);

                return RedirectResult.builder()
                        .originalUrl(originalUrl)
                        .finalUrl(finalUrl)
                        .redirectCount(redirectCount)
                        .status(status)
                        .httpCode(reportHttpCode)
                        .errorMessage(null)
                        .startTime(startTime)
                        .endTime(System.currentTimeMillis())
                        .strategy(getStrategyName())
                        .build();

            } catch (TimeoutError e) {
                log.warn("Firefox: таймаут при обработке URL: {} ({}ms)", url, timeoutMs);
                return RedirectResult.builder()
                        .originalUrl(originalUrl)
                        .finalUrl(finalUrl)
                        .redirectCount(0)
                        .status(PageStatus.ERROR)
                        .errorMessage("Таймаут Firefox при загрузке страницы: " + timeoutMs + "ms")
                        .startTime(startTime)
                        .endTime(System.currentTimeMillis())
                        .strategy(getStrategyName())
                        .build();
            }

        } catch (PlaywrightException e) {
            log.error("Firefox Playwright ошибка для URL: {}", url, e);
            return buildErrorResult(originalUrl, startTime, "Ошибка Firefox Playwright: " + e.getMessage());
        } catch (Exception e) {
            log.error("Неожиданная ошибка в FirefoxPlaywrightStrategy для URL: {}", url, e);
            return buildErrorResult(originalUrl, startTime, "Неожиданная ошибка: " + e.getMessage());
        }
    }

    private boolean isBlocked(String content) {
        if (content == null || content.isEmpty()) return false;
        String lowerContent = content.toLowerCase();
        return BLOCK_KEYWORDS.stream().anyMatch(lowerContent::contains);
    }

    private ProxyPoolManager.ProxyServer getProxyForUrl(String url) {
        if (!proxyConfig.isEnabled()) return null;
        if (proxyConfig.getRotating().isEnabled()) {
            return proxyPoolManager.getNextProxy();
        }
        return createStaticProxy();
    }

    private ProxyPoolManager.ProxyServer createStaticProxy() {
        String server = proxyConfig.getServer();
        if (server == null || server.trim().isEmpty()) {
            log.warn("Proxy включен, но адрес сервера не указан");
            return null;
        }
        String[] parts = server.split(":");
        if (parts.length < 2) {
            log.warn("Некорректный формат proxy сервера: {}", server);
            return null;
        }
        try {
            return new ProxyPoolManager.ProxyServer(
                    parts[0].trim(),
                    Integer.parseInt(parts[1].trim()),
                    proxyConfig.getUsername(),
                    proxyConfig.getPassword()
            );
        } catch (NumberFormatException e) {
            log.warn("Некорректный порт в proxy сервере: {}", server);
            return null;
        }
    }

    private RedirectResult buildErrorResult(String originalUrl, long startTime, String errorMessage) {
        return RedirectResult.builder()
                .originalUrl(originalUrl)
                .finalUrl(originalUrl)
                .redirectCount(0)
                .status(PageStatus.ERROR)
                .errorMessage(errorMessage)
                .startTime(startTime)
                .endTime(System.currentTimeMillis())
                .strategy(getStrategyName())
                .build();
    }

    @Override
    public boolean canHandle(String url, PageStatus previousStatus) {
        return previousStatus == PageStatus.BLOCKED ||
               previousStatus == null ||
               previousStatus == PageStatus.OK ||
               previousStatus == PageStatus.ERROR;
    }

    @Override
    public boolean isBrowserBased() {
        return true;
    }

    @Override
    public int getPriority() {
        return 4; // Priority 4: после Chrome Playwright, другой TLS fingerprint
    }

    @Override
    public String getStrategyName() {
        return "firefox";
    }
}
