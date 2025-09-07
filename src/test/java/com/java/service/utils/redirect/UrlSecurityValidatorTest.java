package com.java.service.utils.redirect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlSecurityValidatorTest {
    
    private UrlSecurityValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new UrlSecurityValidator();
    }
    
    @Test
    void validateUrl_ValidHttpsUrl_DoesNotThrow() {
        assertDoesNotThrow(() -> validator.validateUrl("https://example.com"));
        assertDoesNotThrow(() -> validator.validateUrl("https://google.com/search?q=test"));
        assertDoesNotThrow(() -> validator.validateUrl("http://httpbin.org/redirect/1"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "invalid-url",
        "not a url at all"
    })
    void validateUrl_InvalidUrls_ThrowsSecurityException(String url) {
        assertThrows(SecurityException.class, () -> validator.validateUrl(url));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "file:///etc/passwd",
        "ftp://example.com/file.txt", 
        "jar:file:/path/to/jar.jar!/file",
        "data:text/plain,hello",
        "javascript:alert('xss')",
        "vbscript:msgbox('test')"
    })
    void validateUrl_BlockedSchemes_ThrowsSecurityException(String url) {
        SecurityException exception = assertThrows(SecurityException.class, 
            () -> validator.validateUrl(url));
        assertTrue(exception.getMessage().contains("Запрещенный протокол") ||
                  exception.getMessage().contains("Разрешены только HTTP и HTTPS"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/test",
        "https://127.0.0.1/api", 
        "http://0.0.0.0:8080"
    })
    void validateUrl_LocalhostAddresses_ThrowsSecurityException(String url) {
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validateUrl(url));
        assertTrue(exception.getMessage().contains("loopback") || 
                  exception.getMessage().contains("Запрещенный хост"));
    }
    
    @Test
    void validateUrl_IPv6Localhost_ThrowsSecurityException() {
        // Отдельный тест для IPv6 localhost, так как URI парсинг может быть особенным
        assertThrows(SecurityException.class,
            () -> validator.validateUrl("http://[::1]/path"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://192.168.1.1/internal",
        "https://10.0.0.1/admin",
        "http://172.16.0.1/config",
        "https://169.254.1.1/metadata"
    })
    void validateUrl_PrivateNetworkAddresses_ThrowsSecurityException(String url) {
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validateUrl(url));
        assertTrue(exception.getMessage().contains("внутренним сетевым адресам"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.com:22/ssh",     // SSH port
        "https://example.com:3306/db",   // MySQL port  
        "http://example.com:5432/pg",    // PostgreSQL port
        "https://example.com:6379/redis", // Redis port
        "http://example.com:135/rpc"     // Windows RPC
    })
    void validateUrl_RestrictedPorts_ThrowsSecurityException(String url) {
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validateUrl(url));
        assertTrue(exception.getMessage().contains("Запрещенный порт"));
    }
    
    @Test
    void validateUrl_NoScheme_ThrowsSecurityException() {
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validateUrl("example.com/path"));
        assertTrue(exception.getMessage().contains("протокол"));
    }
    
    @Test
    void validateUrl_InvalidPort_ThrowsSecurityException() {
        assertThrows(SecurityException.class,
            () -> validator.validateUrl("http://example.com:99999/path"));
        assertThrows(SecurityException.class,
            () -> validator.validateUrl("http://example.com:-1/path"));
    }
    
    @Test
    void validateUrl_NullUrl_ThrowsSecurityException() {
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validateUrl(null));
        assertTrue(exception.getMessage().contains("не может быть пустым"));
    }
}