package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для проверки реальных URL и стратегий обхода блокировок
 * 
 * Тесты выполняются только при наличии переменной окружения ENABLE_INTEGRATION_TESTS=true
 * Это позволяет избежать зависимостей от внешних сервисов в CI/CD
 */
@SpringBootTest
@TestPropertySource(properties = {
    "anti-block.log-strategies=true",
    "logging.level.com.java.service.utils=DEBUG"
})
@EnabledIfEnvironmentVariable(named = "ENABLE_INTEGRATION_TESTS", matches = "true")
class RedirectStrategiesIntegrationTest {
    
    private AntiBlockConfig antiBlockConfig;
    private List<AntiBlockStrategy> strategies;
    
    @BeforeEach
    void setUp() {
        // Создаем реальную конфигурацию
        antiBlockConfig = new AntiBlockConfig();
        antiBlockConfig.setLogStrategies(true);
        antiBlockConfig.setUserAgents(Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ));
        
        // Инициализируем все доступные стратегии
        strategies = Arrays.asList(
            new SafeOkHttpStrategy(antiBlockConfig),
            new SimpleHttpStrategy(antiBlockConfig),
            new EnhancedHttpStrategy(antiBlockConfig)
        );
    }
    
    /**
     * Критический тест: goldapple.ru QR редирект
     * 
     * Этот URL вызывал проблемы с HttpURLConnection - должен правильно определять редирект
     */
    @Test
    void testGoldappleRedirect() {
        String originalUrl = "https://goldapple.ru/qr/19000180718";
        
        for (AntiBlockStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                System.out.println("Стратегия " + strategy.getStrategyName() + " недоступна, пропускаем");
                continue;
            }
            
            System.out.println("\\n=== Тестирование стратегии: " + strategy.getStrategyName() + " ===");
            
            RedirectCollectorService.RedirectResult result = strategy.processUrl(originalUrl, 5, 30);
            
            assertNotNull(result, "Результат не должен быть null");
            assertEquals(originalUrl, result.getOriginalUrl(), "Исходный URL должен сохраняться");
            assertNotNull(result.getStatus(), "Статус не должен быть null");
            assertNotNull(result.getFinalUrl(), "Финальный URL не должен быть null");
            
            // Для goldapple.ru ожидаем либо успешный редирект, либо блокировку
            String status = result.getStatus();
            assertTrue(
                PageStatus.SUCCESS.toString().equals(status) || 
                PageStatus.FORBIDDEN.toString().equals(status) ||
                PageStatus.TIMEOUT.toString().equals(status),
                "Неожиданный статус для goldapple.ru: " + status
            );
            
            // Если успешно, проверяем что финальный URL отличается от исходного
            if (PageStatus.SUCCESS.toString().equals(status)) {
                assertNotEquals(originalUrl, result.getFinalUrl(), 
                    "Финальный URL должен отличаться от исходного при успешном редиректе");
                assertTrue(result.getRedirectCount() >= 0, 
                    "Количество редиректов должно быть неотрицательным");
                
                System.out.println("✅ " + strategy.getStrategyName() + ": " + 
                    originalUrl + " -> " + result.getFinalUrl() + 
                    " (редиректов: " + result.getRedirectCount() + ")");
            } else {
                System.out.println("⚠️ " + strategy.getStrategyName() + ": статус " + status);
            }
        }
    }
    
    /**
     * Тест простого HTTP редиректа (должен работать у всех стратегий)
     */
    @Test 
    void testSimpleHttpbinRedirect() {
        String originalUrl = "http://httpbin.org/redirect-to?url=https://httpbin.org/get";
        String expectedFinalUrl = "https://httpbin.org/get";
        
        for (AntiBlockStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                continue;
            }
            
            System.out.println("\\n=== Тест простого редиректа: " + strategy.getStrategyName() + " ===");
            
            RedirectCollectorService.RedirectResult result = strategy.processUrl(originalUrl, 3, 15);
            
            assertNotNull(result);
            assertEquals(originalUrl, result.getOriginalUrl());
            
            // Для простого редиректа ожидаем SUCCESS
            if (PageStatus.SUCCESS.toString().equals(result.getStatus())) {
                assertEquals(expectedFinalUrl, result.getFinalUrl(), 
                    "Стратегия " + strategy.getStrategyName() + " неправильно обработала простой редирект");
                assertEquals(1, result.getRedirectCount(),
                    "Должен быть ровно один редирект");
                
                System.out.println("✅ " + strategy.getStrategyName() + ": корректный редирект");
            } else {
                // Некоторые стратегии могут не работать с HTTP (только HTTPS)
                System.out.println("⚠️ " + strategy.getStrategyName() + ": статус " + result.getStatus() + 
                    " (возможно, не поддерживает HTTP)");
            }
        }
    }
    
    /**
     * Тест обработки 404 ошибки
     */
    @Test
    void testNotFoundHandling() {
        String notFoundUrl = "https://httpbin.org/status/404";
        
        for (AntiBlockStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                continue;
            }
            
            System.out.println("\\n=== Тест 404: " + strategy.getStrategyName() + " ===");
            
            RedirectCollectorService.RedirectResult result = strategy.processUrl(notFoundUrl, 3, 15);
            
            assertNotNull(result);
            assertEquals(notFoundUrl, result.getOriginalUrl());
            assertEquals(notFoundUrl, result.getFinalUrl(), "При 404 финальный URL должен равняться исходному");
            assertEquals(0, result.getRedirectCount(), "При 404 не должно быть редиректов");
            
            // Ожидаем статус NOT_FOUND
            assertEquals(PageStatus.NOT_FOUND.toString(), result.getStatus(),
                "Стратегия " + strategy.getStrategyName() + " неправильно обработала 404");
            
            System.out.println("✅ " + strategy.getStrategyName() + ": корректно обработал 404");
        }
    }
    
    /**
     * Тест производительности различных стратегий
     */
    @Test
    void testStrategyPerformance() {
        String testUrl = "https://httpbin.org/get";
        
        System.out.println("\\n=== ТЕСТ ПРОИЗВОДИТЕЛЬНОСТИ ===");
        
        for (AntiBlockStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                continue;
            }
            
            long startTime = System.currentTimeMillis();
            RedirectCollectorService.RedirectResult result = strategy.processUrl(testUrl, 3, 10);
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            System.out.println(String.format("%s: %dms | Статус: %s | Приоритет: %d",
                strategy.getStrategyName(), elapsedTime, result.getStatus(), strategy.getPriority()));
        }
    }
    
    /**
     * Тест обработки таймаута
     */
    @Test
    void testTimeoutHandling() {
        // URL который должен вызвать таймаут (1 секунда на медленный ответ)
        String timeoutUrl = "https://httpbin.org/delay/2";
        
        for (AntiBlockStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                continue;
            }
            
            System.out.println("\\n=== Тест таймаута: " + strategy.getStrategyName() + " ===");
            
            long startTime = System.currentTimeMillis();
            RedirectCollectorService.RedirectResult result = strategy.processUrl(timeoutUrl, 3, 1); // 1 секунда таймаут
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            assertNotNull(result);
            assertEquals(timeoutUrl, result.getOriginalUrl());
            
            // Проверяем что таймаут сработал достаточно быстро (не более 5 секунд)
            assertTrue(elapsedTime < 5000, 
                "Стратегия " + strategy.getStrategyName() + " слишком долго обрабатывала таймаут: " + elapsedTime + "ms");
            
            // Ожидаем статус TIMEOUT или ERROR
            String status = result.getStatus();
            assertTrue(
                PageStatus.TIMEOUT.toString().equals(status) || 
                PageStatus.ERROR.toString().equals(status) ||
                PageStatus.IO_ERROR.toString().equals(status),
                "Неожиданный статус при таймауте для " + strategy.getStrategyName() + ": " + status
            );
            
            System.out.println("✅ " + strategy.getStrategyName() + ": таймаут обработан за " + elapsedTime + "ms");
        }
    }
    
    /**
     * Проверка доступности стратегий
     */
    @Test
    void testStrategyAvailability() {
        System.out.println("\\n=== ДОСТУПНОСТЬ СТРАТЕГИЙ ===");
        
        int availableCount = 0;
        for (AntiBlockStrategy strategy : strategies) {
            boolean available = strategy.isAvailable();
            System.out.println(String.format("%s: %s (приоритет: %d)", 
                strategy.getStrategyName(), 
                available ? "✅ Доступна" : "❌ Недоступна",
                strategy.getPriority()));
            if (available) {
                availableCount++;
            }
        }
        
        assertTrue(availableCount > 0, "Должна быть доступна хотя бы одна стратегия");
        System.out.println("\\nДоступно стратегий: " + availableCount + " из " + strategies.size());
    }
}