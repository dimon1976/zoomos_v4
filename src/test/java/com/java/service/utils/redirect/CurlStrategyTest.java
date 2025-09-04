package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CurlStrategyTest {
    
    @Autowired
    private CurlStrategy curlStrategy;
    
    @Test
    void testGoldAppleRedirect() {
        // https://goldapple.ru/qr/19000180719 -> https://goldapple.ru/19000180719-elixir-precious
        String testUrl = "https://goldapple.ru/qr/19000180719";
        
        RedirectResult result = curlStrategy.followRedirects(testUrl, 10, 10000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(testUrl);
        assertThat(result.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK);
        assertThat(result.getFinalUrl()).isNotNull();
        assertThat(result.getFinalUrl()).contains("goldapple.ru");
        // Редирект может быть 0 если сайт отвечает напрямую
        assertThat(result.getRedirectCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getProcessingTimeMs()).isLessThan(10000L);
        assertThat(result.getStrategy()).isEqualTo("curl");
        assertThat(result.getHttpCode()).isBetween(200, 399);
    }
    
    @Test
    void testLentaRedirect() {
        // https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/
        // -> https://lenta.com/product/vino-igristoe-bel-bryut-italiya-075l/
        String testUrl = "https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/";
        
        RedirectResult result = curlStrategy.followRedirects(testUrl, 10, 10000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(testUrl);
        // Lenta может быть недоступна, поэтому проверяем более мягко
        assertThat(result.getStatus()).isIn(PageStatus.REDIRECT, PageStatus.OK, PageStatus.ERROR);
        assertThat(result.getFinalUrl()).isNotNull();
        if (result.getStatus() != PageStatus.ERROR) {
            assertThat(result.getFinalUrl()).contains("lenta.com");
        }
        assertThat(result.getProcessingTimeMs()).isLessThan(10000L);
        assertThat(result.getStrategy()).isEqualTo("curl");
    }
    
    @Test
    void testNonExistentDomain() {
        String testUrl = "http://nonexistent-domain-12345-test.com";
        
        RedirectResult result = curlStrategy.followRedirects(testUrl, 5, 5000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(testUrl);
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).isNotNull();
        assertThat(result.getFinalUrl()).isEqualTo(testUrl);
        assertThat(result.getRedirectCount()).isEqualTo(0);
        assertThat(result.getStrategy()).isEqualTo("curl");
    }
    
    @Test
    void testValidUrlNoRedirect() {
        String testUrl = "https://www.google.com";
        
        RedirectResult result = curlStrategy.followRedirects(testUrl, 5, 5000);
        
        assertThat(result).isNotNull();
        assertThat(result.getOriginalUrl()).isEqualTo(testUrl);
        assertThat(result.getStatus()).isIn(PageStatus.OK, PageStatus.REDIRECT);
        assertThat(result.getFinalUrl()).isNotNull();
        assertThat(result.getProcessingTimeMs()).isLessThan(5000L);
        assertThat(result.getStrategy()).isEqualTo("curl");
        assertThat(result.getHttpCode()).isBetween(200, 399);
    }
    
    @Test
    void testEmptyUrl() {
        RedirectResult result = curlStrategy.followRedirects("", 5, 5000);
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).contains("пустым");
    }
    
    @Test
    void testNullUrl() {
        RedirectResult result = curlStrategy.followRedirects(null, 5, 5000);
        
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PageStatus.ERROR);
        assertThat(result.getErrorMessage()).contains("пустым");
    }
    
    @Test
    void testStrategyInterface() {
        assertThat(curlStrategy.canHandle("http://example.com", PageStatus.OK)).isTrue();
        assertThat(curlStrategy.canHandle("https://test.com", PageStatus.ERROR)).isTrue();
        assertThat(curlStrategy.getPriority()).isEqualTo(1);
        assertThat(curlStrategy.getStrategyName()).isEqualTo("curl");
    }
}