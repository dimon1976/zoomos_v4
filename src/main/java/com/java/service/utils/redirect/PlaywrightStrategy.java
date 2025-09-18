package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Стратегия обработки редиректов через Playwright
 * Используется для обхода антиботных систем когда CurlStrategy заблокирован
 */
@Component
@Slf4j
public class PlaywrightStrategy implements RedirectStrategy {
    
    private final UrlSecurityValidator urlSecurityValidator;
    
    public PlaywrightStrategy(UrlSecurityValidator urlSecurityValidator) {
        this.urlSecurityValidator = urlSecurityValidator;
    }
    
    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "captcha", "recaptcha", "cloudflare", "access denied", 
        "доступ ограничен", "доступ запрещен", "проверка безопасности",
        "security check", "bot detection", "too many requests",
        "rate limit", "temporarily unavailable"
    );
    
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        String originalUrl = url;
        
        log.debug("Начинаем обработку URL: {} с помощью PlaywrightStrategy", url);
        
        // Валидация URL на безопасность (SSRF защита)
        try {
            urlSecurityValidator.validateUrl(url);
        } catch (SecurityException e) {
            log.warn("URL заблокирован по соображениям безопасности: {} - {}", url, e.getMessage());
            return buildErrorResult(originalUrl, startTime, "Заблокирован: " + e.getMessage());
        }
        
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
            );
            
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080));
                
            Page page = context.newPage();
            
            // Настройка таймаутов
            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);
            
            // Переменные для отслеживания редиректов
            int redirectCount = 0;
            String initialUrl = url;
            String finalUrl = url;
            AtomicReference<Integer> initialRedirectCode = new AtomicReference<>(); // Для сохранения первоначального HTTP кода редиректа
            
            // Слушатель для перехвата запросов и отслеживания редиректов
            page.onResponse(response -> {
                int statusCode = response.status();
                String responseUrl = response.url();
                log.debug("Response: {} -> HTTP {}", responseUrl, statusCode);
                
                // Сохраняем первый код редиректа
                if ((statusCode >= 300 && statusCode < 400) && initialRedirectCode.get() == null) {
                    initialRedirectCode.set(statusCode);
                    log.debug("Сохранен первоначальный код редиректа: {}", statusCode);
                }
            });
            
            try {
                log.debug("Playwright: навигация к URL: {}", url);
                Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(Math.max(timeoutMs, 30000)));
                
                // Ждем загрузки страницы и возможных JavaScript-редиректов
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));

                // Специальная обработка для маркетплейсов (Яндекс.Маркет, Wildberries и др.)
                if (url.contains("market.yandex") || url.contains("wildberries") || url.contains("ozon")) {
                    log.debug("Обнаружен маркетплейс, применяем специальную стратегию ожидания");
                    // Дополнительное ожидание для маркетплейсов
                    try {
                        page.waitForTimeout(3000); // Даем время для инициализации JS

                        // Ждем появления контента (любой из элементов)
                        page.locator("body").first().waitFor(new Locator.WaitForOptions().setTimeout(5000));

                        // Еще одно ожидание на случай ленивой загрузки
                        page.waitForTimeout(2000);
                    } catch (Exception e) {
                        log.debug("Ошибка при специальном ожидании маркетплейса: {}", e.getMessage());
                    }
                }
                
                // Дополнительное ожидание для JavaScript-редиректов (специально для маркетплейсов)
                String currentUrl = page.url();
                String previousUrl = currentUrl;
                int unchangedCount = 0;

                // Увеличиваем время ожидания для сложных сайтов типа Яндекс.Маркет
                for (int i = 0; i < 60; i++) { // Увеличено до 60 итераций (30 сек)
                    page.waitForTimeout(500); // Ждем 500мс между проверками
                    String newUrl = page.url();

                    if (!newUrl.equals(currentUrl)) {
                        log.debug("JavaScript редирект обнаружен: {} -> {}", currentUrl, newUrl);
                        currentUrl = newUrl;
                        redirectCount++;
                        finalUrl = newUrl;
                        unchangedCount = 0; // Сбрасываем счетчик неизменности
                    } else {
                        unchangedCount++;
                        // Если URL не менялся 10 раз подряд (5 секунд), считаем загрузку завершенной
                        if (unchangedCount >= 10) {
                            log.debug("URL стабилизировался: {}", currentUrl);
                            break;
                        }
                    }

                    // Дополнительная проверка: если страница полностью загружена и URL изменился
                    try {
                        String pageTitle = page.title();
                        if (pageTitle != null && !pageTitle.isEmpty() && !pageTitle.equals("Loading...")) {
                            // Страница загружена, проверяем еще раз URL
                            String finalCheckUrl = page.url();
                            if (!finalCheckUrl.equals(previousUrl)) {
                                finalUrl = finalCheckUrl;
                                log.debug("Финальный URL после загрузки страницы: {}", finalUrl);
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки получения заголовка
                    }
                }
                
                // Обновляем finalUrl только если редиректы не были обнаружены в цикле
                if (redirectCount == 0) {
                    finalUrl = page.url();
                }
                
                // Подсчет редиректов по изменению URL
                if (!initialUrl.equals(finalUrl) && redirectCount == 0) {
                    redirectCount = 1; // Если URL изменился, но мы не отследили через цикл
                    log.debug("Обнаружен редирект: {} -> {}", initialUrl, finalUrl);
                }
                
                // Получаем финальный статус HTTP
                int finalStatusCode = response != null ? response.status() : 200;
                log.debug("Финальный HTTP статус: {}", finalStatusCode);
                
                // Используем первоначальный код редиректа, если он был, иначе финальный
                int reportHttpCode = (initialRedirectCode.get() != null) ? initialRedirectCode.get() : finalStatusCode;
                
                // Получение содержимого страницы для проверки блокировки
                String pageContent = page.content();
                
                // Проверка на блокировку
                if (isBlocked(pageContent)) {
                    log.warn("Playwright: обнаружена блокировка на URL: {}", finalUrl);
                    return RedirectResult.builder()
                            .originalUrl(originalUrl)
                            .finalUrl(finalUrl)
                            .redirectCount(redirectCount)
                            .status(PageStatus.BLOCKED)
                            .errorMessage("Страница заблокирована антиботной системой")
                            .startTime(startTime)
                            .endTime(System.currentTimeMillis())
                            .strategy(getStrategyName())
                            .build();
                }
                
                // Определение статуса результата
                PageStatus status;
                if (finalStatusCode >= 400) {
                    status = PageStatus.NOT_FOUND;
                } else if (redirectCount > 0) {
                    status = PageStatus.REDIRECT;
                } else {
                    status = PageStatus.OK;
                }
                
                log.info("Playwright: успешная обработка URL: {} -> {} (редиректов: {}, HTTP: {})", 
                        originalUrl, finalUrl, redirectCount, reportHttpCode);
                
                return RedirectResult.builder()
                        .originalUrl(originalUrl)
                        .finalUrl(finalUrl)
                        .redirectCount(redirectCount)
                        .status(status)
                        .httpCode(reportHttpCode)  // Используем правильный HTTP код
                        .errorMessage(null)
                        .startTime(startTime)
                        .endTime(System.currentTimeMillis())
                        .strategy(getStrategyName())
                        .build();
                        
            } catch (TimeoutError e) {
                log.warn("Playwright: таймаут при обработке URL: {} ({}ms)", url, timeoutMs);
                return RedirectResult.builder()
                        .originalUrl(originalUrl)
                        .finalUrl(finalUrl)
                        .redirectCount(0)
                        .status(PageStatus.ERROR)
                        .errorMessage("Таймаут при загрузке страницы: " + timeoutMs + "ms")
                        .startTime(startTime)
                        .endTime(System.currentTimeMillis())
                        .strategy(getStrategyName())
                        .build();
            }
            
        } catch (PlaywrightException e) {
            log.error("Playwright: ошибка при обработке URL: {}", url, e);
            return RedirectResult.builder()
                    .originalUrl(originalUrl)
                    .finalUrl(url)
                    .redirectCount(0)
                    .status(PageStatus.ERROR)
                    .errorMessage("Ошибка Playwright: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(System.currentTimeMillis())
                    .strategy(getStrategyName())
                    .build();
        } catch (Exception e) {
            log.error("Неожиданная ошибка в PlaywrightStrategy для URL: {}", url, e);
            return RedirectResult.builder()
                    .originalUrl(originalUrl)
                    .finalUrl(url)
                    .redirectCount(0)
                    .status(PageStatus.ERROR)
                    .errorMessage("Неожиданная ошибка: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(System.currentTimeMillis())
                    .strategy(getStrategyName())
                    .build();
        }
    }
    
    /**
     * Проверка содержимого страницы на признаки блокировки
     */
    private boolean isBlocked(String content) {
        if (content == null || content.isEmpty()) {
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
        // Используется при блокировке основной стратегии или принудительно
        return previousStatus == PageStatus.BLOCKED || 
               previousStatus == null || 
               previousStatus == PageStatus.OK ||
               previousStatus == PageStatus.ERROR;
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