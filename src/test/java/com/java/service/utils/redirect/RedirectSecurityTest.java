package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RedirectSecurityTest {
    
    private UrlSecurityValidator validator;
    private CurlStrategy curlStrategy;
    private HttpClientStrategy httpClientStrategy;
    private PlaywrightStrategy playwrightStrategy;
    
    @BeforeEach
    void setUp() {
        validator = new UrlSecurityValidator();
        curlStrategy = new CurlStrategy(validator);
        httpClientStrategy = new HttpClientStrategy(validator);
        playwrightStrategy = new PlaywrightStrategy(validator);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:8080/admin",
        "https://127.0.0.1/config",
        "http://192.168.1.1/internal",
        "file:///etc/passwd",
        "ftp://internal.com/secrets"
    })
    void curlStrategy_MaliciousUrls_ReturnsBlockedStatus(String maliciousUrl) {
        RedirectResult result = curlStrategy.followRedirects(maliciousUrl, 5, 10000);
        
        assertEquals(PageStatus.ERROR, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Заблокирован"));
        assertEquals(maliciousUrl, result.getOriginalUrl());
        assertEquals(maliciousUrl, result.getFinalUrl());
        assertEquals(0, result.getRedirectCount());
    }
    
    @ParameterizedTest 
    @ValueSource(strings = {
        "http://localhost:3306/db",
        "https://10.0.0.1/secrets", 
        "http://172.16.1.1:22/ssh",
        "javascript:alert('xss')",
        "data:text/html,<script>alert('xss')</script>"
    })
    void httpClientStrategy_MaliciousUrls_ReturnsBlockedStatus(String maliciousUrl) {
        RedirectResult result = httpClientStrategy.followRedirects(maliciousUrl, 5, 10000);
        
        assertEquals(PageStatus.ERROR, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Заблокирован"));
        assertEquals(maliciousUrl, result.getOriginalUrl());
        assertEquals(maliciousUrl, result.getFinalUrl());
        assertEquals(0, result.getRedirectCount());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.169.254/metadata",  // AWS metadata service
        "http://0.0.0.0:8080/admin",
        "https://::1/internal",
        "ftp://192.168.1.100/files",
        "jar:http://example.com/app.jar!/config"
    })
    void playwrightStrategy_MaliciousUrls_ReturnsBlockedStatus(String maliciousUrl) {
        RedirectResult result = playwrightStrategy.followRedirects(maliciousUrl, 5, 10000);
        
        assertEquals(PageStatus.ERROR, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Заблокирован"));
        assertEquals(maliciousUrl, result.getOriginalUrl());
        assertEquals(maliciousUrl, result.getFinalUrl());
        assertEquals(0, result.getRedirectCount());
    }
    
    @Test
    void allStrategies_ValidPublicUrl_DoNotBlock() {
        String validUrl = "https://httpbin.org/status/200";
        int timeout = 15000; // Увеличенный таймаут для реальных запросов
        
        // Тест может не работать без интернета, но проверяет что валидация пропускает корректные URL
        try {
            RedirectResult curlResult = curlStrategy.followRedirects(validUrl, 3, timeout);
            assertNotEquals(PageStatus.ERROR, curlResult.getStatus(), 
                "CurlStrategy не должна блокировать валидный URL");
                
            RedirectResult httpResult = httpClientStrategy.followRedirects(validUrl, 3, timeout);
            assertNotEquals(PageStatus.ERROR, httpResult.getStatus(),
                "HttpClientStrategy не должна блокировать валидный URL");
                
        } catch (Exception e) {
            // Игнорируем сетевые ошибки - главное что валидация не заблокировала
            System.out.println("Сетевая ошибка (игнорируем): " + e.getMessage());
        }
    }
    
    @Test
    void allStrategies_EmptyUrl_ReturnError() {
        String[] emptyUrls = {"", "   ", null};
        
        for (String emptyUrl : emptyUrls) {
            RedirectResult curlResult = curlStrategy.followRedirects(emptyUrl, 5, 10000);
            assertEquals(PageStatus.ERROR, curlResult.getStatus());
            
            RedirectResult httpResult = httpClientStrategy.followRedirects(emptyUrl, 5, 10000);
            assertEquals(PageStatus.ERROR, httpResult.getStatus());
            
            RedirectResult playwrightResult = playwrightStrategy.followRedirects(emptyUrl, 5, 10000);
            assertEquals(PageStatus.ERROR, playwrightResult.getStatus());
        }
    }
    
    @Test
    void securityValidation_ConsistencyAcrossStrategies() {
        String[] maliciousUrls = {
            "http://localhost/admin",
            "ftp://internal.com/files", 
            "file:///etc/passwd",
            "http://192.168.1.1/config"
        };
        
        for (String maliciousUrl : maliciousUrls) {
            RedirectResult curlResult = curlStrategy.followRedirects(maliciousUrl, 5, 10000);
            RedirectResult httpResult = httpClientStrategy.followRedirects(maliciousUrl, 5, 10000);
            RedirectResult playwrightResult = playwrightStrategy.followRedirects(maliciousUrl, 5, 10000);
            
            // Все стратегии должны одинаково блокировать вредоносные URL
            assertEquals(PageStatus.ERROR, curlResult.getStatus(), 
                "CurlStrategy должна блокировать: " + maliciousUrl);
            assertEquals(PageStatus.ERROR, httpResult.getStatus(),
                "HttpClientStrategy должна блокировать: " + maliciousUrl);
            assertEquals(PageStatus.ERROR, playwrightResult.getStatus(),
                "PlaywrightStrategy должна блокировать: " + maliciousUrl);
                
            assertTrue(curlResult.getErrorMessage().contains("Заблокирован"));
            assertTrue(httpResult.getErrorMessage().contains("Заблокирован"));
            assertTrue(playwrightResult.getErrorMessage().contains("Заблокирован"));
        }
    }
}