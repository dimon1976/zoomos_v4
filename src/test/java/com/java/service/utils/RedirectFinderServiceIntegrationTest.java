package com.java.service.utils;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.java.service.utils.redirect.RedirectStrategy;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для стратегий обработки редиректов
 * Тестируют взаимодействие между стратегиями и корректность цепочки обработки
 */
@SpringBootTest
@ExtendWith(SpringExtension.class)
@Slf4j
class RedirectFinderServiceIntegrationTest {

    @Autowired
    private List<RedirectStrategy> strategies;

    private List<String> testUrls;

    @BeforeEach
    void setUp() {
        // Подготавливаем тестовые URL разных типов
        testUrls = List.of(
            "http://google.com",           // Редирект
            "https://github.com",          // Может быть OK или редирект
            "http://httpbin.org/status/200", // OK
            "http://nonexistent-domain-12345.com", // ERROR
            "https://httpstat.us/404",     // NOT_FOUND
            "http://httpbin.org/redirect/3" // Множественный редирект
        );
    }

    @Test
    void testStrategyChainConfiguration() {
        log.info("=== ТЕСТИРОВАНИЕ КОНФИГУРАЦИИ СТРАТЕГИЙ ===");
        
        // Проверяем что все стратегии загружены
        assertThat(strategies).hasSize(3);
        
        // Сортируем стратегии по приоритету
        List<RedirectStrategy> sortedStrategies = strategies.stream()
                .sorted((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()))
                .collect(Collectors.toList());
        
        // Проверяем приоритеты: curl=1, playwright=2, httpclient=3
        assertThat(sortedStrategies.get(0).getPriority()).isEqualTo(1);
        assertThat(sortedStrategies.get(1).getPriority()).isEqualTo(2);  
        assertThat(sortedStrategies.get(2).getPriority()).isEqualTo(3);

        // Проверяем имена стратегий
        assertThat(sortedStrategies.get(0).getStrategyName()).isEqualTo("curl");
        assertThat(sortedStrategies.get(1).getStrategyName()).isEqualTo("playwright");
        assertThat(sortedStrategies.get(2).getStrategyName()).isEqualTo("httpclient");

        log.info("Все стратегии корректно настроены");
    }

    @Test
    void testCurlStrategyWithVariousUrls() {
        log.info("=== ТЕСТИРОВАНИЕ CURL СТРАТЕГИИ ===");
        
        RedirectStrategy curlStrategy = strategies.stream()
                .filter(s -> "curl".equals(s.getStrategyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CurlStrategy не найдена"));

        // Тест Google редиректа
        RedirectResult googleResult = curlStrategy.followRedirects("http://google.com", 5, 15000);
        assertThat(googleResult).isNotNull();
        assertThat(googleResult.getOriginalUrl()).isEqualTo("http://google.com");
        assertThat(googleResult.getStatus()).isEqualTo(PageStatus.REDIRECT);
        assertThat(googleResult.getFinalUrl()).contains("www.google.com");
        assertThat(googleResult.getRedirectCount()).isGreaterThan(0);
        assertThat(googleResult.getStrategy()).isEqualTo("curl");

        // Тест несуществующего домена
        RedirectResult errorResult = curlStrategy.followRedirects("http://nonexistent-domain-12345.com", 5, 10000);
        assertThat(errorResult).isNotNull();
        assertThat(errorResult.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(errorResult.getErrorMessage()).isNotNull();

        log.info("CurlStrategy работает корректно");
    }

    @Test
    void testPlaywrightStrategyWithVariousUrls() {
        log.info("=== ТЕСТИРОВАНИЕ PLAYWRIGHT СТРАТЕГИИ ===");
        
        RedirectStrategy playwrightStrategy = strategies.stream()
                .filter(s -> "playwright".equals(s.getStrategyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PlaywrightStrategy не найдена"));

        // Тест Google редиректа
        RedirectResult googleResult = playwrightStrategy.followRedirects("http://google.com", 5, 15000);
        assertThat(googleResult).isNotNull();
        assertThat(googleResult.getOriginalUrl()).isEqualTo("http://google.com");
        
        // Playwright может показать более детальную информацию о редиректе
        if (googleResult.getStatus() == PageStatus.REDIRECT) {
            assertThat(googleResult.getFinalUrl()).contains("google.com");
            assertThat(googleResult.getRedirectCount()).isGreaterThan(0);
        }
        
        assertThat(googleResult.getStrategy()).isEqualTo("playwright");

        // Тест несуществующего домена
        RedirectResult errorResult = playwrightStrategy.followRedirects("http://nonexistent-domain-12345.com", 5, 10000);
        assertThat(errorResult).isNotNull();
        assertThat(errorResult.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(errorResult.getErrorMessage()).isNotNull();

        log.info("PlaywrightStrategy работает корректно");
    }

    @Test
    void testHttpClientStrategyWithVariousUrls() {
        log.info("=== ТЕСТИРОВАНИЕ HTTPCLIENT СТРАТЕГИИ ===");
        
        RedirectStrategy httpClientStrategy = strategies.stream()
                .filter(s -> "httpclient".equals(s.getStrategyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("HttpClientStrategy не найдена"));

        // Тест Google редиректа
        RedirectResult googleResult = httpClientStrategy.followRedirects("http://google.com", 5, 15000);
        assertThat(googleResult).isNotNull();
        assertThat(googleResult.getOriginalUrl()).isEqualTo("http://google.com");
        assertThat(googleResult.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK);
        assertThat(googleResult.getStrategy()).isEqualTo("httpclient");

        // Тест несуществующего домена
        RedirectResult errorResult = httpClientStrategy.followRedirects("http://nonexistent-domain-12345.com", 5, 10000);
        assertThat(errorResult).isNotNull();
        assertThat(errorResult.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(errorResult.getErrorMessage()).isNotNull();

        log.info("HttpClientStrategy работает корректно");
    }

    @Test
    void testStrategyCanHandleLogic() {
        log.info("=== ТЕСТИРОВАНИЕ ЛОГИКИ ВЫБОРА СТРАТЕГИЙ ===");
        
        RedirectStrategy curlStrategy = findStrategyByName("curl");
        RedirectStrategy playwrightStrategy = findStrategyByName("playwright");
        RedirectStrategy httpClientStrategy = findStrategyByName("httpclient");

        String testUrl = "http://example.com";

        // CurlStrategy должна обрабатывать все URL (первичная стратегия)
        assertThat(curlStrategy.canHandle(testUrl, null)).isTrue();
        assertThat(curlStrategy.canHandle(testUrl, PageStatus.OK)).isTrue();
        assertThat(curlStrategy.canHandle(testUrl, PageStatus.ERROR)).isTrue();

        // PlaywrightStrategy должна обрабатывать только заблокированные URL
        assertThat(playwrightStrategy.canHandle(testUrl, PageStatus.BLOCKED)).isTrue();
        assertThat(playwrightStrategy.canHandle(testUrl, PageStatus.OK)).isFalse();
        assertThat(playwrightStrategy.canHandle(testUrl, PageStatus.ERROR)).isFalse();

        // HttpClientStrategy должна обрабатывать все URL (fallback)
        assertThat(httpClientStrategy.canHandle(testUrl, null)).isTrue();
        assertThat(httpClientStrategy.canHandle(testUrl, PageStatus.ERROR)).isTrue();

        log.info("Логика выбора стратегий работает корректно");
    }

    @Test
    void testPerformanceWithMultipleStrategies() {
        log.info("=== ТЕСТИРОВАНИЕ ПРОИЗВОДИТЕЛЬНОСТИ ===");
        
        List<String> performanceUrls = List.of(
            "http://google.com",
            "https://github.com",
            "http://httpbin.org/status/200"
        );

        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<RedirectResult>> futures = performanceUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    RedirectStrategy curlStrategy = findStrategyByName("curl");
                    return curlStrategy.followRedirects(url, 3, 10000);
                }, executor))
                .collect(Collectors.toList());

        // Ждем завершения всех задач
        List<RedirectResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        
        long processingTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Проверяем результаты
        assertThat(results).hasSize(3);
        assertThat(processingTime).isLessThan(30000); // Менее 30 секунд для 3 URL

        results.forEach(result -> {
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isNotNull();
            assertThat(result.getStrategy()).isEqualTo("curl");
        });

        log.info("Производительность: {} URL обработано за {}ms", results.size(), processingTime);
    }

    @Test  
    void testResultConsistencyAcrossStrategies() {
        log.info("=== ТЕСТИРОВАНИЕ СОГЛАСОВАННОСТИ РЕЗУЛЬТАТОВ ===");
        
        String testUrl = "http://google.com";
        
        // Тестируем один URL разными стратегиями
        RedirectResult curlResult = findStrategyByName("curl")
                .followRedirects(testUrl, 5, 15000);
        
        RedirectResult playwrightResult = findStrategyByName("playwright")
                .followRedirects(testUrl, 5, 15000);
        
        RedirectResult httpClientResult = findStrategyByName("httpclient")
                .followRedirects(testUrl, 5, 15000);

        // Все стратегии должны вернуть результат
        assertThat(curlResult).isNotNull();
        assertThat(playwrightResult).isNotNull();
        assertThat(httpClientResult).isNotNull();

        // Все должны определить одинаковый исходный URL
        assertThat(curlResult.getOriginalUrl()).isEqualTo(testUrl);
        assertThat(playwrightResult.getOriginalUrl()).isEqualTo(testUrl);
        assertThat(httpClientResult.getOriginalUrl()).isEqualTo(testUrl);

        // Финальные URL должны содержать google.com (могут отличаться в деталях)
        if (curlResult.getStatus() != PageStatus.ERROR) {
            assertThat(curlResult.getFinalUrl()).containsIgnoringCase("google");
        }
        if (playwrightResult.getStatus() != PageStatus.ERROR) {
            assertThat(playwrightResult.getFinalUrl()).containsIgnoringCase("google");
        }
        if (httpClientResult.getStatus() != PageStatus.ERROR) {
            assertThat(httpClientResult.getFinalUrl()).containsIgnoringCase("google");
        }

        log.info("Curl: {} -> {} ({})", 
                curlResult.getOriginalUrl(), curlResult.getFinalUrl(), curlResult.getStatus());
        log.info("Playwright: {} -> {} ({})", 
                playwrightResult.getOriginalUrl(), playwrightResult.getFinalUrl(), playwrightResult.getStatus());
        log.info("HttpClient: {} -> {} ({})", 
                httpClientResult.getOriginalUrl(), httpClientResult.getFinalUrl(), httpClientResult.getStatus());
    }

    private RedirectStrategy findStrategyByName(String name) {
        return strategies.stream()
                .filter(s -> name.equals(s.getStrategyName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Стратегия не найдена: " + name));
    }
}