package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Стратегия обработки редиректов через Jsoup.
 * Priority 6: последний fallback.
 * Основная ценность — обработка meta-refresh редиректов
 * (curl делает HEAD и не видит тег в HTML, все браузеры заблокированы).
 */
@Component
@Slf4j
public class JsoupStrategy implements RedirectStrategy {

    private final UrlSecurityValidator urlSecurityValidator;

    private static final Pattern META_REFRESH_PATTERN =
            Pattern.compile("\\d+\\s*;\\s*url=(.+)", Pattern.CASE_INSENSITIVE);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public JsoupStrategy(UrlSecurityValidator urlSecurityValidator) {
        this.urlSecurityValidator = urlSecurityValidator;
    }

    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        String originalUrl = url;

        log.debug("Начинаем обработку URL: {} с помощью JsoupStrategy", url);

        try {
            urlSecurityValidator.validateUrl(url);
        } catch (SecurityException e) {
            log.warn("URL заблокирован по соображениям безопасности: {} - {}", url, e.getMessage());
            return buildErrorResult(originalUrl, startTime, "Заблокирован: " + e.getMessage());
        }

        try {
            String currentUrl = url;
            int redirectCount = 0;
            Integer initialRedirectCode = null;

            for (int i = 0; i <= maxRedirects; i++) {
                try {
                    urlSecurityValidator.validateUrl(currentUrl);
                } catch (SecurityException e) {
                    return buildErrorResult(originalUrl, startTime, "Редирект заблокирован: " + e.getMessage());
                }

                Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent(USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .timeout(timeoutMs)
                        .execute();

                int httpCode = response.statusCode();

                // HTTP-редирект → следуем по Location
                if (httpCode >= 300 && httpCode < 400) {
                    String location = response.header("Location");
                    if (location != null && !location.isEmpty()) {
                        if (initialRedirectCode == null) {
                            initialRedirectCode = httpCode;
                        }
                        redirectCount++;
                        String nextUrl = resolveUrl(currentUrl, location);
                        log.info("Jsoup HTTP-редирект {}: {} -> {} (HTTP {})", redirectCount, currentUrl, nextUrl, httpCode);
                        currentUrl = nextUrl;
                        continue;
                    }
                }

                // Успешный ответ — проверяем meta-refresh в HTML
                if (httpCode >= 200 && httpCode < 300) {
                    String body = response.body();
                    String metaRefreshUrl = extractMetaRefreshUrl(body, currentUrl);

                    if (metaRefreshUrl != null) {
                        if (initialRedirectCode == null) {
                            initialRedirectCode = httpCode;
                        }
                        redirectCount++;
                        log.info("Jsoup meta-refresh редирект: {} -> {}", currentUrl, metaRefreshUrl);
                        currentUrl = metaRefreshUrl;
                        continue;
                    }
                }

                // Финальный ответ
                long endTime = System.currentTimeMillis();
                int reportCode = initialRedirectCode != null ? initialRedirectCode : httpCode;
                PageStatus status = determineStatus(httpCode, redirectCount);

                log.info("Jsoup: {} → {} (редиректов: {}, HTTP: {}, время: {}ms)",
                        originalUrl, currentUrl, redirectCount, reportCode, endTime - startTime);

                return RedirectResult.builder()
                        .originalUrl(originalUrl)
                        .finalUrl(currentUrl)
                        .redirectCount(redirectCount)
                        .status(status)
                        .httpCode(reportCode)
                        .startTime(startTime)
                        .endTime(endTime)
                        .strategy(getStrategyName())
                        .build();
            }

            return buildErrorResult(originalUrl, startTime, "Превышено максимальное количество редиректов: " + maxRedirects);

        } catch (Exception e) {
            log.warn("JsoupStrategy ошибка для URL {}: {}", url, e.getMessage());
            return buildErrorResult(originalUrl, startTime, "Ошибка Jsoup: " + e.getMessage());
        }
    }

    /**
     * Извлекает URL из тега <meta http-equiv="refresh" content="N; url=...">
     */
    private String extractMetaRefreshUrl(String html, String baseUrl) {
        if (html == null || html.isEmpty()) return null;
        try {
            Document doc = Jsoup.parse(html, baseUrl);
            Element meta = doc.select("meta[http-equiv=refresh]").first();
            if (meta == null) return null;

            String content = meta.attr("content");
            Matcher m = META_REFRESH_PATTERN.matcher(content);
            if (m.find()) {
                String rawUrl = m.group(1).trim();
                // Убираем обрамляющие кавычки если есть
                rawUrl = rawUrl.replaceAll("^['\"]|['\"]$", "");
                if (!rawUrl.isEmpty()) {
                    log.debug("Jsoup: найден meta-refresh URL: {}", rawUrl);
                    return rawUrl;
                }
            }
        } catch (Exception e) {
            log.debug("Ошибка парсинга HTML для meta-refresh: {}", e.getMessage());
        }
        return null;
    }

    private PageStatus determineStatus(int httpCode, int redirectCount) {
        if (httpCode >= 200 && httpCode < 300) {
            return redirectCount > 0 ? PageStatus.REDIRECT : PageStatus.OK;
        } else if (httpCode == 403 || httpCode == 429) {
            return PageStatus.BLOCKED;
        } else if (httpCode == 404 || httpCode == 410) {
            return PageStatus.NOT_FOUND;
        }
        return PageStatus.ERROR;
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
            }
            String path = base.getPath();
            if (!path.endsWith("/")) {
                int lastSlash = path.lastIndexOf("/");
                path = path.substring(0, lastSlash + 1);
            }
            return base.getProtocol() + "://" + base.getHost() +
                   (base.getPort() != -1 && base.getPort() != 80 && base.getPort() != 443 ? ":" + base.getPort() : "") +
                   path + location;
        } catch (Exception e) {
            log.warn("Ошибка резолва URL: base={}, location={}", baseUrl, location);
            return location;
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
        return true; // Last resort — пробуем всегда
    }

    @Override
    public int getPriority() {
        return 6; // Последний fallback с поддержкой meta-refresh
    }

    @Override
    public String getStrategyName() {
        return "jsoup";
    }
}
