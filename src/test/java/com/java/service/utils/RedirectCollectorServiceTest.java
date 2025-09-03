package com.java.service.utils;

import com.java.config.AntiBlockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedirectCollectorServiceTest {

    @Mock
    private AntiBlockConfig antiBlockConfig;

    private SimpleHttpStrategy simpleHttpStrategy;

    @InjectMocks
    private AntiBlockService antiBlockService;

    @BeforeEach
    void setUp() {
        // Manually create a spy for the strategy to handle constructor with arguments
        simpleHttpStrategy = Mockito.spy(new SimpleHttpStrategy(antiBlockConfig));
        antiBlockService = new AntiBlockService(antiBlockConfig, Arrays.asList(simpleHttpStrategy));

        List<String> userAgents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        );
        when(antiBlockConfig.getUserAgents()).thenReturn(userAgents);
        when(antiBlockConfig.isLogStrategies()).thenReturn(true); // Включаем логи для диагностики
    }

    @Test
    void shouldHandleSuccessfulRedirect() {
        String originalUrl = "http://httpbin.org/redirect-to?url=https://example.com";
        String finalUrl = "https://example.com";
        RedirectCollectorService.RedirectResult result = antiBlockService.processUrlWithFallback(originalUrl, 5, 20, 0, 0);
        assertEquals(PageStatus.SUCCESS.toString(), result.getStatus());
        assertEquals(finalUrl, result.getFinalUrl());
        assertEquals(1, result.getRedirectCount());
    }

    @Test
    void shouldHandle404NotFound() {
        String url = "https://httpbin.org/status/404";
        RedirectCollectorService.RedirectResult result = antiBlockService.processUrlWithFallback(url, 5, 20, 0, 0);
        assertEquals(PageStatus.NOT_FOUND.toString(), result.getStatus());
        assertEquals(url, result.getFinalUrl());
    }

    @Test
    void shouldHandleGoldappleUrl() {
        // Этот тест показывает основную проблему: HttpURLConnection не обнаруживает редиректы
        String originalUrl = "https://goldapple.ru/qr/19000180719";
        RedirectCollectorService.RedirectResult result = antiBlockService.processUrlWithFallback(originalUrl, 5, 30, 0, 0);
        
        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        
        // Проблема: SimpleHttpStrategy не обнаруживает редиректы, возвращает исходный URL
        // Либо SUCCESS (если сервер отвечает 200), либо FORBIDDEN (если блокирует)
        String status = result.getStatus();
        assertTrue(
            PageStatus.SUCCESS.toString().equals(status) || 
            PageStatus.FORBIDDEN.toString().equals(status) ||
            PageStatus.TIMEOUT.toString().equals(status),
            "goldapple.ru должен возвращать SUCCESS, FORBIDDEN или TIMEOUT, но получил: " + status
        );
        
        // Проблема: редирект не обнаруживается
        assertEquals(originalUrl, result.getFinalUrl(), "Проблема: финальный URL остается таким же - редирект не обнаружен");
        assertEquals(0, result.getRedirectCount(), "Проблема: редиректы не обнаружены");
    }

    @Test
    void shouldHandleLentaUrl() {
        // Лента часто блокирует автоматические запросы
        String originalUrl = "https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/";
        RedirectCollectorService.RedirectResult result = antiBlockService.processUrlWithFallback(originalUrl, 5, 30, 0, 0);
        
        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        
        // Лента может блокировать автоматические запросы или отвечать нормально
        String status = result.getStatus();
        assertTrue(
            PageStatus.SUCCESS.toString().equals(status) || 
            PageStatus.FORBIDDEN.toString().equals(status) ||
            PageStatus.TIMEOUT.toString().equals(status) ||
            PageStatus.NOT_FOUND.toString().equals(status),
            "lenta.com может возвращать различные статусы, но получил: " + status
        );
    }
}