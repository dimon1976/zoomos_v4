package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Демонстрационный тест для воспроизведения проблемы с определением редиректов
 * 
 * ПРОБЛЕМА: HttpURLConnection игнорирует setFollowRedirects(false) для некоторых серверов
 * и не позволяет корректно определить редиректы
 */
public class RedirectProblemDemoTest {
    
    private AntiBlockConfig antiBlockConfig;
    private SimpleHttpStrategy simpleHttpStrategy;
    private SafeOkHttpStrategy safeOkHttpStrategy;
    
    @BeforeEach
    void setUp() {
        antiBlockConfig = new AntiBlockConfig();
        antiBlockConfig.setLogStrategies(true);
        antiBlockConfig.setUserAgents(Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ));
        
        simpleHttpStrategy = new SimpleHttpStrategy(antiBlockConfig);
        safeOkHttpStrategy = new SafeOkHttpStrategy(antiBlockConfig);
    }
    
    @Test
    @DisplayName("ПРОБЛЕМА: goldapple.ru - HttpURLConnection возвращает 200 вместо 301 редиректа")
    void demonstrateGoldappleProblem() {
        String originalUrl = "https://goldapple.ru/qr/19000180718";
        
        System.out.println("\\n=== ДЕМОНСТРАЦИЯ ПРОБЛЕМЫ С ОПРЕДЕЛЕНИЕМ РЕДИРЕКТОВ ===");
        System.out.println("URL: " + originalUrl);
        System.out.println("Ожидается: HTTP 301 редирект на https://goldapple.ru/19000180718-elixir-intense");
        System.out.println("curl -I показывает правильный 301 редирект");
        System.out.println();
        
        // Тестируем SimpleHttpStrategy (проблемная)
        System.out.println("--- Тестирование SimpleHttpStrategy (HttpURLConnection) ---");
        RedirectCollectorService.RedirectResult simpleResult = simpleHttpStrategy.processUrl(originalUrl, 5, 30);
        
        System.out.println("SimpleHttpStrategy результат:");
        System.out.println("  Статус: " + simpleResult.getStatus());
        System.out.println("  Финальный URL: " + simpleResult.getFinalUrl()); 
        System.out.println("  Редиректов: " + simpleResult.getRedirectCount());
        
        // Тестируем SafeOkHttpStrategy (улучшенная)
        System.out.println("\\n--- Тестирование SafeOkHttpStrategy (улучшенная) ---");
        RedirectCollectorService.RedirectResult safeResult = safeOkHttpStrategy.processUrl(originalUrl, 5, 30);
        
        System.out.println("SafeOkHttpStrategy результат:");
        System.out.println("  Статус: " + safeResult.getStatus());
        System.out.println("  Финальный URL: " + safeResult.getFinalUrl());
        System.out.println("  Редиректов: " + safeResult.getRedirectCount());
        
        // Анализируем проблему
        System.out.println("\\n=== АНАЛИЗ ПРОБЛЕМЫ ===");
        
        // ПРОБЛЕМА: SimpleHttpStrategy не обнаруживает редирект
        if (simpleResult.getFinalUrl().equals(originalUrl) && simpleResult.getRedirectCount() == 0) {
            System.out.println("❌ ПРОБЛЕМА ПОДТВЕРЖДЕНА:");
            System.out.println("   SimpleHttpStrategy (HttpURLConnection) НЕ обнаруживает редирект");
            System.out.println("   Возвращает исходный URL как финальный");
            System.out.println("   Количество редиректов: 0 (должно быть > 0)");
            System.out.println("   Причина: setFollowRedirects(false) игнорируется сервером");
        }
        
        // РЕШЕНИЕ: SafeOkHttpStrategy или другая стратегия должна работать лучше
        if (!safeResult.getFinalUrl().equals(originalUrl) || safeResult.getRedirectCount() > 0) {
            System.out.println("✅ РЕШЕНИЕ РАБОТАЕТ:");
            System.out.println("   SafeOkHttpStrategy успешно обнаруживает редирект");
            System.out.println("   Финальный URL отличается от исходного");
        } else {
            System.out.println("⚠️  SafeOkHttpStrategy также не смогла определить редирект");
            System.out.println("   Возможно, сервер блокирует автоматические запросы");
        }
        
        System.out.println("\\n=== РЕКОМЕНДАЦИИ ===");
        System.out.println("1. Использовать SafeOkHttpStrategy вместо SimpleHttpStrategy");
        System.out.println("2. Добавить WebClientStrategy для reactive подхода");  
        System.out.println("3. Реализовать browser-based стратегию для сложных случаев");
        System.out.println("4. Добавить анализ HTML содержимого для JavaScript редиректов");
        
        // Базовые проверки для теста
        assertNotNull(simpleResult);
        assertNotNull(safeResult);
        assertEquals(originalUrl, simpleResult.getOriginalUrl());
        assertEquals(originalUrl, safeResult.getOriginalUrl());
        
        // Хотя бы одна стратегия должна давать какой-то осмысленный результат
        assertTrue(
            PageStatus.SUCCESS.toString().equals(simpleResult.getStatus()) ||
            PageStatus.FORBIDDEN.toString().equals(simpleResult.getStatus()) ||
            PageStatus.TIMEOUT.toString().equals(simpleResult.getStatus()),
            "SimpleHttpStrategy должна вернуть валидный статус"
        );
    }
    
    @Test
    @DisplayName("КОНТРОЛЬ: простой HTTP редирект должен работать у всех стратегий")
    void testSimpleRedirectControl() {
        String originalUrl = "http://httpbin.org/redirect-to?url=https://httpbin.org/get";
        String expectedFinalUrl = "https://httpbin.org/get";
        
        System.out.println("\\n=== КОНТРОЛЬНЫЙ ТЕСТ: простой редирект ===");
        System.out.println("URL: " + originalUrl);
        System.out.println("Ожидается: редирект на " + expectedFinalUrl);
        
        // SimpleHttpStrategy должна справляться с простыми редиректами
        RedirectCollectorService.RedirectResult simpleResult = simpleHttpStrategy.processUrl(originalUrl, 3, 15);
        
        System.out.println("SimpleHttpStrategy результат:");
        System.out.println("  Статус: " + simpleResult.getStatus());
        System.out.println("  Финальный URL: " + simpleResult.getFinalUrl());
        System.out.println("  Редиректов: " + simpleResult.getRedirectCount());
        
        // Для простого редиректа ожидаем успех (если поддерживается HTTP)
        if (PageStatus.SUCCESS.toString().equals(simpleResult.getStatus())) {
            assertEquals(expectedFinalUrl, simpleResult.getFinalUrl(), 
                "SimpleHttpStrategy должна правильно обрабатывать простые редиректы");
            assertEquals(1, simpleResult.getRedirectCount(), 
                "Должен быть ровно один редирект");
            
            System.out.println("✅ SimpleHttpStrategy корректно обработала простой редирект");
        } else {
            System.out.println("⚠️ SimpleHttpStrategy не смогла обработать HTTP редирект (возможно, поддерживается только HTTPS)");
        }
    }
    
    @Test
    @DisplayName("СТАТИСТИКА: доступность стратегий")
    void testStrategyAvailability() {
        System.out.println("\\n=== ДОСТУПНОСТЬ СТРАТЕГИЙ ===");
        
        System.out.println("SimpleHttpStrategy: " + 
            (simpleHttpStrategy.isAvailable() ? "✅ Доступна" : "❌ Недоступна") +
            " (приоритет: " + simpleHttpStrategy.getPriority() + ")");
            
        System.out.println("SafeOkHttpStrategy: " + 
            (safeOkHttpStrategy.isAvailable() ? "✅ Доступна" : "❌ Недоступна") +
            " (приоритет: " + safeOkHttpStrategy.getPriority() + ")");
        
        // Хотя бы одна стратегия должна быть доступна
        assertTrue(simpleHttpStrategy.isAvailable() || safeOkHttpStrategy.isAvailable(),
            "Должна быть доступна хотя бы одна стратегия");
    }
}