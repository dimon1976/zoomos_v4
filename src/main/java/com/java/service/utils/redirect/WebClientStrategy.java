package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Стратегия с browser-like заголовками через Spring WebClient (Reactor Netty).
 * Priority 2: после curl, перед Playwright.
 * Обходит сайты, которые проверяют заголовки, но не TLS/JS fingerprint.
 * Следует редиректам вручную (followRedirect=false) для точного подсчёта.
 */
@Component
@Slf4j
public class WebClientStrategy implements RedirectStrategy {

    private final UrlSecurityValidator urlSecurityValidator;
    private final WebClient webClient;

    private static final Set<String> BLOCK_KEYWORDS = Set.of(
        "captcha", "recaptcha", "cloudflare", "access denied",
        "blocked", "forbidden", "защита", "antibot",
        "доступ ограничен", "проверка безопасности", "rate limit", "too many requests"
    );

    public WebClientStrategy(UrlSecurityValidator urlSecurityValidator) {
        this.urlSecurityValidator = urlSecurityValidator;

        HttpClient httpClient = HttpClient.create().followRedirect(false);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT,
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Sec-Fetch-Dest", "document")
                .defaultHeader("Sec-Fetch-Mode", "navigate")
                .defaultHeader("Sec-Fetch-Site", "none")
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .build();
    }

    @Override
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        String originalUrl = url;

        log.debug("Начинаем обработку URL: {} с помощью WebClientStrategy", url);

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

                String requestUrl = currentUrl;
                ResponseEntity<String> resp = webClient.get()
                        .uri(URI.create(requestUrl))
                        .exchangeToMono(response -> response.toEntity(String.class))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .block();

                if (resp == null) {
                    return buildErrorResult(originalUrl, startTime, "Пустой ответ от сервера");
                }

                int httpCode = resp.getStatusCode().value();
                String body = resp.getBody();

                // Редирект → следуем по Location
                if (httpCode >= 300 && httpCode < 400) {
                    String location = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location != null && !location.isEmpty()) {
                        if (initialRedirectCode == null) {
                            initialRedirectCode = httpCode;
                        }
                        redirectCount++;
                        String nextUrl = resolveUrl(currentUrl, location);
                        log.info("WebClient редирект {}: {} -> {} (HTTP {})", redirectCount, currentUrl, nextUrl, httpCode);
                        currentUrl = nextUrl;
                        continue;
                    }
                }

                // Финальный ответ
                long endTime = System.currentTimeMillis();
                int reportCode = initialRedirectCode != null ? initialRedirectCode : httpCode;
                PageStatus status = determineStatus(httpCode, redirectCount, body);

                log.info("WebClient: {} → {} (редиректов: {}, HTTP: {}, время: {}ms)",
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
            log.warn("WebClientStrategy ошибка для URL {}: {}", url, e.getMessage());
            return buildErrorResult(originalUrl, startTime, "Ошибка WebClient: " + e.getMessage());
        }
    }

    private PageStatus determineStatus(int httpCode, int redirectCount, String body) {
        if (body != null && detectBlocking(body)) {
            return PageStatus.BLOCKED;
        }
        if (httpCode >= 200 && httpCode < 300) {
            return redirectCount > 0 ? PageStatus.REDIRECT : PageStatus.OK;
        } else if (httpCode == 403 || httpCode == 429) {
            return PageStatus.BLOCKED;
        } else if (httpCode == 404 || httpCode == 410) {
            return PageStatus.NOT_FOUND;
        }
        return PageStatus.ERROR;
    }

    private boolean detectBlocking(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        String lower = content.toLowerCase();
        return BLOCK_KEYWORDS.stream().anyMatch(lower::contains);
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
        return previousStatus == PageStatus.BLOCKED ||
               previousStatus == null ||
               previousStatus == PageStatus.OK ||
               previousStatus == PageStatus.ERROR;
    }

    @Override
    public int getPriority() {
        return 2; // Priority 2: после curl, перед Playwright
    }

    @Override
    public String getStrategyName() {
        return "webclient";
    }
}
