package com.java.service.normalization;

import com.java.model.enums.NormalizationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class NormalizationServiceTest {
    
    @Autowired
    private NormalizationService normalizationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testVolumeNormalization() throws Exception {
        // Тест нормализации объемов - используем правила по умолчанию
        String rules = null; // будут использованы правила по умолчанию
        
        // Тестируем различные варианты записи объема
        assertEquals("0.7", normalizationService.normalize("0.7л", NormalizationType.VOLUME, rules));
        assertEquals("0.7", normalizationService.normalize("0,7 л.", NormalizationType.VOLUME, rules));
        assertEquals("0.7", normalizationService.normalize("0.7", NormalizationType.VOLUME, rules));
        assertEquals("1", normalizationService.normalize("1л", NormalizationType.VOLUME, rules));
        assertEquals("0.5", normalizationService.normalize("0,5 л.", NormalizationType.VOLUME, rules));
        assertEquals("500", normalizationService.normalize("500мл", NormalizationType.VOLUME, rules));
    }
    
    @Test
    public void testBrandNormalization() throws Exception {
        // Тест нормализации брендов - используем правила по умолчанию
        String rules = null; // будут использованы правила по умолчанию
        
        // Тестируем различные варианты записи брендов
        assertEquals("Macallan", normalizationService.normalize("The Macallan", NormalizationType.BRAND, rules));
        assertEquals("Macallan", normalizationService.normalize("MACALLAN", NormalizationType.BRAND, rules));
        assertEquals("Macallan", normalizationService.normalize("Macallan, Edition №5", NormalizationType.BRAND, rules));
        assertEquals("Glenfiddich", normalizationService.normalize("the glenfiddich", NormalizationType.BRAND, rules));
        assertEquals("Jameson", normalizationService.normalize("jameson irish whiskey", NormalizationType.BRAND, rules));
        assertEquals("Jameson", normalizationService.normalize("JAMESON", NormalizationType.BRAND, rules));
    }
    
    @Test
    public void testNormalizationWithNullValues() {
        // Тест с null значениями
        assertNull(normalizationService.normalize(null, NormalizationType.VOLUME, null));
        assertEquals("test", normalizationService.normalize("test", null, null));
    }
    
    @Test
    public void testNormalizationWithEmptyRules() {
        // Тест с пустыми правилами - должны использоваться правила по умолчанию
        assertEquals("0.7", normalizationService.normalize("0.7л", NormalizationType.VOLUME, ""));
        assertEquals("Macallan", normalizationService.normalize("The Macallan", NormalizationType.BRAND, ""));
    }
}