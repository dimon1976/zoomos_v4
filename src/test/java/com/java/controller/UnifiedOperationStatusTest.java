package com.java.controller;

import com.java.constants.UrlConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест для проверки унифицированной системы статуса операций
 */
public class UnifiedOperationStatusTest {

    @Test
    public void testUrlConstantsAreValid() {
        // Проверяем, что все URL константы правильно сформированы
        assertNotNull(UrlConstants.CLIENT_OPERATION_DETAIL);
        assertTrue(UrlConstants.CLIENT_OPERATION_DETAIL.contains("{clientId}"));
        assertTrue(UrlConstants.CLIENT_OPERATION_DETAIL.contains("{operationId}"));
        
        // Legacy URLs
        assertNotNull(UrlConstants.LEGACY_IMPORT_STATUS);
        assertTrue(UrlConstants.LEGACY_IMPORT_STATUS.contains("{operationId}"));
        
        // API URLs
        assertNotNull(UrlConstants.API_OPERATION_STATUS);
        assertNotNull(UrlConstants.API_OPERATION_DELETE);
        assertNotNull(UrlConstants.API_OPERATION_CANCEL);
    }

    @Test
    public void testUrlReplacement() {
        // Тестируем замену параметров в URL
        String url = UrlConstants.CLIENT_OPERATION_DETAIL
                .replace("{clientId}", "123")
                .replace("{operationId}", "456");
        
        assertEquals("/clients/123/operations/456", url);
    }

    @Test
    public void testLegacyUrls() {
        // Проверяем legacy URL
        assertEquals("/import/status/{operationId}", UrlConstants.LEGACY_IMPORT_STATUS);
        assertEquals("/import/{clientId}/upload", UrlConstants.LEGACY_IMPORT_UPLOAD);
        assertEquals("/import/{clientId}/analyze", UrlConstants.LEGACY_IMPORT_ANALYZE);
        assertEquals("/import/{clientId}/start", UrlConstants.LEGACY_IMPORT_START);
        assertEquals("/import/{clientId}/cancel", UrlConstants.LEGACY_IMPORT_CANCEL);
    }

    @Test
    public void testApiUrls() {
        // Проверяем API URL
        assertEquals("/api/operations/{operationId}/status", UrlConstants.API_OPERATION_STATUS);
        assertEquals("/api/operations/{operationId}", UrlConstants.API_OPERATION_DELETE);
        assertEquals("/api/operations/{operationId}/cancel", UrlConstants.API_OPERATION_CANCEL);
    }

    @Test
    public void testRelativeApiUrls() {
        // Проверяем относительные API URL
        assertEquals("/operations/{operationId}/status", UrlConstants.REL_OPERATION_STATUS);
        assertEquals("/operations/{operationId}", UrlConstants.REL_OPERATION_DELETE);
        assertEquals("/operations/{operationId}/cancel", UrlConstants.REL_OPERATION_CANCEL);
    }
}