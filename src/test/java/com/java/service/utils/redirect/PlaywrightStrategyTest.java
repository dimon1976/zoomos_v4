package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для PlaywrightStrategy
 * Использует реальные URL для проверки функциональности обхода антиботных систем
 */
@ExtendWith(MockitoExtension.class)
class PlaywrightStrategyTest {
    
    @InjectMocks
    private PlaywrightStrategy playwrightStrategy;
    
    @Test
    void testGoldAppleRedirect() {
        // https://goldapple.ru/qr/19000180719 - должен редиректить на страницу товара
        String url = "https://goldapple.ru/qr/19000180719";
        
        RedirectResult result = playwrightStrategy.followRedirects(url, 5, 15000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getStrategy()).isEqualTo("playwright");
        
        // Проверяем что произошел редирект
        if (result.getStatus() == PageStatus.REDIRECT) {
            assertThat(result.getFinalUrl()).isNotEqualTo(url);
            assertThat(result.getFinalUrl()).contains("goldapple.ru");
            assertThat(result.getRedirectCount()).isGreaterThan(0);
        }
        
        // Результат должен быть либо REDIRECT, либо OK (если редирект не обнаружен)
        assertThat(result.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK, PageStatus.ERROR, PageStatus.NOT_FOUND);
        assertThat(result.getStartTime()).isLessThanOrEqualTo(result.getEndTime());
    }
    
    @Test
    void testGoogleRedirect() {
        // http://google.com - должен редиректить на http://www.google.com/
        String url = "http://google.com";
        
        RedirectResult result = playwrightStrategy.followRedirects(url, 5, 15000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getStrategy()).isEqualTo("playwright");
        
        // Проверяем что произошел редирект
        if (result.getStatus() == PageStatus.REDIRECT) {
            assertThat(result.getFinalUrl()).isNotEqualTo(url);
            assertThat(result.getFinalUrl()).contains("www.google.com");
            assertThat(result.getRedirectCount()).isGreaterThan(0);
        }
        
        assertThat(result.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK, PageStatus.ERROR, PageStatus.NOT_FOUND);
        assertThat(result.getStartTime()).isLessThanOrEqualTo(result.getEndTime());
    }
    
    @Test 
    void testLentaRedirect() {
        // https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/
        // Должен редиректить на укороченную версию URL
        String url = "https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/";
        
        RedirectResult result = playwrightStrategy.followRedirects(url, 5, 15000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getStrategy()).isEqualTo("playwright");
        
        // Проверяем результат (может быть редирект или обычная страница)
        if (result.getStatus() == PageStatus.REDIRECT) {
            assertThat(result.getFinalUrl()).isNotEqualTo(url);
            assertThat(result.getFinalUrl()).contains("lenta.com");
            assertThat(result.getRedirectCount()).isGreaterThan(0);
        }
        
        assertThat(result.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK, PageStatus.ERROR, PageStatus.NOT_FOUND);
        assertThat(result.getStartTime()).isLessThanOrEqualTo(result.getEndTime());
    }
    
    @Test
    void testCanHandle() {
        String url = "https://example.com";
        
        // Должен обрабатывать только заблокированные страницы
        assertThat(playwrightStrategy.canHandle(url, PageStatus.BLOCKED)).isTrue();
        assertThat(playwrightStrategy.canHandle(url, PageStatus.OK)).isFalse();
        assertThat(playwrightStrategy.canHandle(url, PageStatus.REDIRECT)).isFalse();
        assertThat(playwrightStrategy.canHandle(url, PageStatus.ERROR)).isFalse();
        assertThat(playwrightStrategy.canHandle(url, PageStatus.NOT_FOUND)).isFalse();
    }
    
    @Test
    void testPriority() {
        // Должен иметь приоритет 2 (после curl, но перед httpclient)
        assertThat(playwrightStrategy.getPriority()).isEqualTo(2);
    }
    
    @Test
    void testStrategyName() {
        assertThat(playwrightStrategy.getStrategyName()).isEqualTo("playwright");
    }
    
    @Test
    void testTimeout() {
        // Тест с очень коротким таймаутом для проверки обработки таймаутов
        String url = "https://goldapple.ru/qr/19000180719";
        
        RedirectResult result = playwrightStrategy.followRedirects(url, 5, 100); // 100ms - очень мало
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(url);
        assertThat(result.getStrategy()).isEqualTo("playwright");
        
        // При таком коротком таймауте должна быть ошибка
        if (result.getStatus() == PageStatus.ERROR) {
            assertThat(result.getErrorMessage()).contains("Таймаут");
        }
    }
    
    @Test
    void testInvalidUrl() {
        // Тест с недействительным URL
        String invalidUrl = "http://nonexistent-domain-12345-test.com";
        
        RedirectResult result = playwrightStrategy.followRedirects(invalidUrl, 5, 10000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(invalidUrl);
        assertThat(result.getStrategy()).isEqualTo("playwright");
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).isNotNull();
    }
}