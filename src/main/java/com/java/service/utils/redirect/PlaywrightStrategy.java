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
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080));
                
            Page page = context.newPage();
            
            // Настройка таймаутов из интерфейса
            page.setDefaultTimeout(timeoutMs);
            page.setDefaultNavigationTimeout(timeoutMs);

            log.debug("Установлены таймауты: default={}, navigation={}", timeoutMs, timeoutMs);
            
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
                log.debug("Playwright: навигация к URL: {} с таймаутом {}мс", url, timeoutMs);
                Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));
                
                // Ждем загрузки страницы и возможных JavaScript-редиректов
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
                } catch (Exception e) {
                    log.debug("Не удалось дождаться NETWORKIDLE, продолжаем: {}", e.getMessage());
                    // Попробуем дождаться хотя бы базовой загрузки
                    try {
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs / 2));
                    } catch (Exception ex) {
                        log.debug("Не удалось дождаться DOMCONTENTLOADED: {}", ex.getMessage());
                    }
                }

                // Специальная обработка для маркетплейсов (Яндекс.Маркет, Wildberries и др.)
                if (url.contains("market.yandex") || url.contains("wildberries") || url.contains("ozon")) {
                    log.debug("Обнаружен маркетплейс, применяем специальную стратегию ожидания");
                    // Дополнительное ожидание для маркетплейсов
                    try {
                        // Для Яндекс.Маркета важно дождаться инициализации JavaScript и редиректов
                        if (url.contains("market.yandex")) {
                            // Ждем изменения URL или стабилизации страницы
                            page.waitForTimeout(timeoutMs / 5); // Время для начальной загрузки

                            // Пробуем дождаться конкретных элементов товара
                            try {
                                page.locator("h1").first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs / 2));
                            } catch (Exception ex) {
                                log.debug("Заголовок товара не найден, продолжаем");
                            }
                        } else {
                            page.waitForTimeout(timeoutMs / 3); // Даем время для инициализации JS
                            page.locator("body").first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs / 2));
                        }

                        // Еще одно ожидание на случай ленивой загрузки
                        page.waitForTimeout(timeoutMs / 5);
                    } catch (Exception e) {
                        log.debug("Ошибка при специальном ожидании маркетплейса: {}", e.getMessage());
                    }
                }
                
                // Улучшенное отслеживание JavaScript-навигации для маркетплейсов
                String currentUrl = page.url();
                int unchangedCount = 0;
                boolean urlChanged = false;

                // Сохраняем изначальный URL для сравнения
                String baselineUrl = initialUrl;
                log.info("=== НАЧАЛО ОТСЛЕЖИВАНИЯ ===");
                log.info("Изначальный URL (baselineUrl): {}", baselineUrl);
                log.info("Текущий URL после навигации: {}", currentUrl);
                log.info("URLs одинаковые? {}", currentUrl.equals(baselineUrl));

                // ВАЖНО: Проверяем изменение URL сразу после навигации
                if (!currentUrl.equals(baselineUrl)) {
                    log.info("🎯 РЕДИРЕКТ ОБНАРУЖЕН СРАЗУ ПОСЛЕ НАВИГАЦИИ: {} -> {}", baselineUrl, currentUrl);
                    finalUrl = currentUrl;
                    redirectCount = 1;
                    urlChanged = true;
                }

                // Ожидание изменений URL в соответствии с пользовательским таймаутом
                int maxIterations = timeoutMs / 500; // Количество итераций на основе таймаута
                for (int i = 0; i < maxIterations; i++) {
                    page.waitForTimeout(500); // Ждем 500мс между проверками

                    String newUrl = page.url();

                    // Проверяем изменение URL относительно начального
                    if (!newUrl.equals(currentUrl)) {
                        log.debug("URL изменился: {} -> {}", currentUrl, newUrl);

                        // Проверяем, является ли это значимым изменением относительно изначального URL
                        if (!newUrl.equals(baselineUrl) && !urlChanged) {
                            redirectCount++;
                            finalUrl = newUrl;
                            urlChanged = true;
                            log.info("🎯 РЕДИРЕКТ ЗАФИКСИРОВАН В ЦИКЛЕ: {} -> {} (редирект #{})", baselineUrl, newUrl, redirectCount);
                        }

                        currentUrl = newUrl;
                        unchangedCount = 0; // Сбрасываем счетчик неизменности
                    } else {
                        unchangedCount++;
                        // Если URL не менялся 12 раз подряд (6 секунд), считаем навигацию завершенной
                        if (unchangedCount >= 12) {
                            log.debug("URL стабилизировался после {} проверок", unchangedCount);
                            break;
                        }
                    }

                    // Дополнительная проверка готовности страницы
                    if (i % 4 == 0) { // Каждые 2 секунды
                        try {
                            String pageTitle = page.title();
                            String currentCheck = page.url();

                            if (pageTitle != null && !pageTitle.isEmpty() &&
                                !pageTitle.equals("Loading...") && !pageTitle.contains("Загрузка")) {

                                // Если URL изменился относительно базового, это редирект
                                if (!currentCheck.equals(baselineUrl) && !urlChanged) {
                                    log.debug("Обнаружен финальный URL после загрузки страницы: {}", currentCheck);
                                    finalUrl = currentCheck;
                                    redirectCount = 1;
                                    urlChanged = true;
                                }

                                // Если страница загружена и URL стабилен, можем завершать
                                if (unchangedCount >= 6) {
                                    log.debug("Страница полностью загружена, завершаем отслеживание");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Ошибка при проверке готовности страницы: {}", e.getMessage());
                        }
                    }
                }

                // Финальная проверка для случаев, когда изменение было незамечено
                String finalCheck = page.url();
                if (!finalCheck.equals(baselineUrl) && !urlChanged) {
                    log.info("🔍 ФИНАЛЬНАЯ ПРОВЕРКА: обнаружен пропущенный редирект {} -> {}", baselineUrl, finalCheck);
                    finalUrl = finalCheck;
                    redirectCount = 1;
                    urlChanged = true;
                }


                // Финальное обновление URL, если редиректы не были зафиксированы ранее
                if (redirectCount == 0) {
                    String lastUrl = page.url();
                    if (!lastUrl.equals(initialUrl)) {
                        finalUrl = lastUrl;
                        redirectCount = 1;
                        log.debug("Обнаружен редирект при финальной проверке: {} -> {}", initialUrl, lastUrl);
                    } else {
                        finalUrl = lastUrl;
                    }
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

    /**
     * Определяет, является ли изменение URL значимым редиректом
     * (не только изменение параметров запроса)
     */
    private boolean isSignificantUrlChange(String oldUrl, String newUrl) {
        if (oldUrl == null || newUrl == null) {
            return false;
        }

        try {
            // Простое сравнение базовых частей URL без параметров
            String oldBase = oldUrl.split("\\?")[0];
            String newBase = newUrl.split("\\?")[0];

            // Удаляем trailing slash для корректного сравнения
            oldBase = oldBase.endsWith("/") ? oldBase.substring(0, oldBase.length() - 1) : oldBase;
            newBase = newBase.endsWith("/") ? newBase.substring(0, newBase.length() - 1) : newBase;

            // Если базовые части разные - это значимое изменение
            if (!oldBase.equals(newBase)) {
                return true;
            }

            // Дополнительная проверка для маркетплейсов: значимые изменения параметров
            if (oldUrl.contains("market.yandex") || oldUrl.contains("wildberries") || oldUrl.contains("ozon")) {
                // Для маркетплейсов проверяем ключевые параметры продукта
                return !extractProductParams(oldUrl).equals(extractProductParams(newUrl));
            }

            return false;

        } catch (Exception e) {
            log.debug("Ошибка при анализе изменения URL: {}", e.getMessage());
            return !oldUrl.equals(newUrl); // Fallback - любое изменение считается значимым
        }
    }

    /**
     * Извлекает ключевые параметры продукта для маркетплейсов
     */
    private String extractProductParams(String url) {
        if (url == null) return "";

        StringBuilder keyParams = new StringBuilder();

        // Извлекаем ключевые параметры для разных маркетплейсов
        if (url.contains("market.yandex")) {
            if (url.contains("/product")) keyParams.append("product-");
            if (url.contains("sku=")) {
                String sku = extractParam(url, "sku");
                keyParams.append("sku:").append(sku).append("-");
            }
        } else if (url.contains("wildberries")) {
            if (url.contains("/catalog/")) {
                String[] parts = url.split("/catalog/");
                if (parts.length > 1) {
                    keyParams.append("catalog:").append(parts[1].split("/")[0]).append("-");
                }
            }
        }

        return keyParams.toString();
    }

    /**
     * Извлекает значение параметра из URL
     */
    private String extractParam(String url, String paramName) {
        try {
            String[] parts = url.split(paramName + "=");
            if (parts.length > 1) {
                return parts[1].split("&")[0];
            }
        } catch (Exception e) {
            log.debug("Ошибка извлечения параметра {}: {}", paramName, e.getMessage());
        }
        return "";
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