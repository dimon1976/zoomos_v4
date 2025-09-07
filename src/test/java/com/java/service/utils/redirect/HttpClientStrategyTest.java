package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.java.service.utils.redirect.UrlSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.TimeUnit;

/**
 * Тесты для HttpClientStrategy - резервной стратегии обработки редиректов
 */
class HttpClientStrategyTest {

    private HttpClientStrategy strategy;
    private UrlSecurityValidator urlSecurityValidator;

    @BeforeEach
    void setUp() {
        urlSecurityValidator = new UrlSecurityValidator();
        strategy = new HttpClientStrategy(urlSecurityValidator);
    }

    @Test
    void testFollowRedirects_WithValidUrl_Success() {
        // Given
        String url = "https://www.google.com";
        int maxRedirects = 5;
        int timeoutMs = 5000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getFinalUrl()).isNotNull().isNotEmpty();
        assertThat(result.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        assertThat(result.getProcessingTimeMs()).isPositive();
        assertThat(result.getStrategy()).isEqualTo("HttpClientStrategy");
        assertThat(result.getRedirectCount()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void testFollowRedirects_WithRedirectUrl_FollowsCorrectly() {
        // Given - используем URL который точно даёт редирект
        String url = "http://httpbin.org/redirect/2"; // Этот сервис даёт 2 редиректа
        int maxRedirects = 5;
        int timeoutMs = 8000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getFinalUrl()).isNotNull();
        assertThat(result.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        assertThat(result.getProcessingTimeMs()).isPositive();
        
        // HttpClient автоматически следует редиректам, поэтому результат может быть разным
        if (result.getStatus() == PageStatus.REDIRECT) {
            assertThat(result.getRedirectCount()).isGreaterThan(0);
        }
    }

    @Test
    void testFollowRedirects_WithNonExistentDomain_ReturnsError() {
        // Given
        String url = "http://nonexistent-domain-12345.com";
        int maxRedirects = 5;
        int timeoutMs = 3000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getFinalUrl()).isEqualTo(url); // Остается исходный URL
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).isNotNull().isNotEmpty();
        assertThat(result.getProcessingTimeMs()).isPositive();
        assertThat(result.getStrategy()).isEqualTo("HttpClientStrategy");
    }

    @Test
    void testFollowRedirects_WithInvalidUrl_ReturnsError() {
        // Given
        String url = "invalid-url-format";
        int maxRedirects = 5;
        int timeoutMs = 3000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).isNotNull()
            .containsAnyOf("URI", "Invalid", "url", "схема");
        assertThat(result.getStrategy()).isEqualTo("HttpClientStrategy");
    }

    @Test
    void testFollowRedirects_WithEmptyUrl_ReturnsError() {
        // Given
        String url = "";
        int maxRedirects = 5;
        int timeoutMs = 3000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).contains("не может быть пустым");
        assertThat(result.getStrategy()).isEqualTo("HttpClientStrategy");
    }

    @Test
    void testFollowRedirects_WithNullUrl_ReturnsError() {
        // Given
        String url = null;
        int maxRedirects = 5;
        int timeoutMs = 3000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).contains("не может быть пустым");
        assertThat(result.getStrategy()).isEqualTo("HttpClientStrategy");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFollowRedirects_WithTimeout_CompletesWithinLimit() {
        // Given
        String url = "https://httpbin.org/delay/3"; // Задержка 3 секунды
        int maxRedirects = 5;
        int timeoutMs = 5000; // 5 секунд таймаут

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getProcessingTimeMs()).isLessThan(timeoutMs + 1000); // +1сек на погрешность
        // Результат может быть успешным или с ошибкой таймаута
        assertThat(result.getStatus()).isIn(PageStatus.OK, PageStatus.ERROR);
    }

    @Test
    void testFollowRedirects_WithHttpsUrl_HandlesSecureConnection() {
        // Given
        String url = "https://github.com";
        int maxRedirects = 5;
        int timeoutMs = 5000;

        // When
        RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getFinalUrl()).isNotNull().startsWith("https://");
        assertThat(result.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        assertThat(result.getProcessingTimeMs()).isPositive();
    }

    @Test
    void testCanHandle_WithValidUrl_ReturnsTrue() {
        // Given
        String url = "https://example.com";
        PageStatus previousStatus = null;

        // When
        boolean canHandle = strategy.canHandle(url, previousStatus);

        // Then
        assertThat(canHandle).isTrue();
    }

    @Test
    void testCanHandle_WithBlockedStatus_StillReturnsTrue() {
        // Given - HttpClient является fallback стратегией и должен пытаться обработать любой URL
        String url = "https://example.com";
        PageStatus previousStatus = PageStatus.BLOCKED;

        // When
        boolean canHandle = strategy.canHandle(url, previousStatus);

        // Then
        assertThat(canHandle).isTrue();
    }

    @Test
    void testGetPriority_ReturnsCorrectPriority() {
        // When
        int priority = strategy.getPriority();

        // Then
        assertThat(priority).isEqualTo(3); // HttpClient имеет самый низкий приоритет (fallback)
    }

    @Test
    void testFollowRedirects_WithMultipleUrls_ProcessesSequentially() {
        // Given
        String[] urls = {
            "https://www.google.com",
            "https://github.com",
            "http://httpbin.org/status/200"
        };

        // When & Then
        for (String url : urls) {
            RedirectResult result = strategy.followRedirects(url, 5, 5000);
            
            assertThat(result).isNotNull();
            assertThat(result.getOriginalUrl()).isEqualTo(url);
            assertThat(result.getStatus()).isIn(
                PageStatus.OK, 
                PageStatus.REDIRECT, 
                PageStatus.ERROR
            );
            assertThat(result.getProcessingTimeMs()).isPositive();
        }
    }

    @Test
    void testFollowRedirects_WithDifferentStatusCodes_HandlesCorrectly() {
        // Given
        String[] testUrls = {
            "http://httpbin.org/status/200",  // OK
            "http://httpbin.org/status/404",  // NOT_FOUND
            "http://httpbin.org/status/500"   // Server error
        };

        // When & Then
        for (String url : testUrls) {
            RedirectResult result = strategy.followRedirects(url, 5, 5000);
            
            assertThat(result).isNotNull();
            assertThat(result.getOriginalUrl()).isEqualTo(url);
            // HttpClient может обработать эти статусы по-разному
            assertThat(result.getStatus()).isIn(
                PageStatus.OK,
                PageStatus.NOT_FOUND,
                PageStatus.ERROR
            );
        }
    }

    @Test
    void testFollowRedirects_CompareToBaseline_ReturnsConsistentResults() {
        // Given
        String url = "https://www.google.com";
        
        // When - делаем несколько запросов к одному URL
        RedirectResult result1 = strategy.followRedirects(url, 5, 5000);
        RedirectResult result2 = strategy.followRedirects(url, 5, 5000);
        
        // Then - результаты должны быть схожими
        assertThat(result1.getStatus()).isEqualTo(result2.getStatus());
        assertThat(result1.getOriginalUrl()).isEqualTo(result2.getOriginalUrl());
        // finalUrl может немного отличаться (www vs без www), но статус должен совпадать
        
        if (result1.getStatus() == PageStatus.OK || result1.getStatus() == PageStatus.REDIRECT) {
            assertThat(result2.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        }
    }
}