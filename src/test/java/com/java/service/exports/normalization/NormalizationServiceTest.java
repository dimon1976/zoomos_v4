package com.java.service.exports.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.service.exports.normalization.impl.BrandNormalizer;
import com.java.service.exports.normalization.impl.VolumeNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты для системы нормализации данных
 */
class NormalizationServiceTest {
    
    private VolumeNormalizer volumeNormalizer;
    private BrandNormalizer brandNormalizer;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        volumeNormalizer = new VolumeNormalizer(objectMapper);
        brandNormalizer = new BrandNormalizer(objectMapper);
    }
    
    @Test
    void testVolumeNormalization() {
        // Правила по умолчанию: извлечь числовое значение, заменить запятую на точку
        String defaultRules = """
                {
                  "extractNumeric": true,
                  "replaceCommaWithDot": true,
                  "resultType": "STRING"
                }
                """;
        
        // Тесты нормализации объемов
        assertEquals("0.7", volumeNormalizer.normalize("0.7л", defaultRules));
        assertEquals("0.7", volumeNormalizer.normalize("0,7 л.", defaultRules));
        assertEquals("0.7", volumeNormalizer.normalize("0.7 мл", defaultRules));
        assertEquals("0.7", volumeNormalizer.normalize("0.7", defaultRules));
        assertEquals("750", volumeNormalizer.normalize("750мл", defaultRules));
        
        // Проверка поддержки разных типов значений
        assertTrue(volumeNormalizer.supports("test"));
        assertTrue(volumeNormalizer.supports(123));
    }
    
    @Test
    void testBrandNormalization() {
        // Правила по умолчанию: убрать артикли, извлечь основной бренд, привести к Proper Case
        String defaultRules = """
                {
                  "removeArticles": ["The", "A", "An"],
                  "extractMainBrand": {
                    "splitBy": ",",
                    "takeFirst": true
                  },
                  "caseNormalization": "PROPER_CASE"
                }
                """;
        
        // Тесты нормализации брендов
        assertEquals("Macallan", brandNormalizer.normalize("The Macallan", defaultRules));
        assertEquals("Macallan", brandNormalizer.normalize("Macallan, Edition №5", defaultRules));
        assertEquals("Macallan", brandNormalizer.normalize("MACALLAN", defaultRules));
        assertEquals("Macallan", brandNormalizer.normalize("macallan", defaultRules));
        assertEquals("Macallan", brandNormalizer.normalize("The Macallan, A Night on Earth", defaultRules));
        
        // Проверка поддержки разных типов значений
        assertTrue(brandNormalizer.supports("test"));
        assertTrue(brandNormalizer.supports(123));
    }
    
    @Test
    void testNormalizationWithNullValues() {
        // Проверяем что null значения возвращаются как есть
        assertEquals(null, volumeNormalizer.normalize(null, null));
        assertEquals(null, brandNormalizer.normalize(null, null));
        
        // Проверяем что пустые строки возвращаются как есть
        assertEquals("", volumeNormalizer.normalize("", null));
        assertEquals("", brandNormalizer.normalize("", null));
    }
    
    @Test
    void testNormalizationWithInvalidRules() {
        // Проверяем что при некорректных правилах используются правила по умолчанию
        String invalidRules = "{ invalid json }";
        
        // Должны использоваться правила по умолчанию
        assertEquals("0.7", volumeNormalizer.normalize("0.7л", invalidRules));
        assertEquals("Macallan", brandNormalizer.normalize("The Macallan", invalidRules));
    }
    
    @Test
    void testExampleRules() {
        // Проверяем что примеры правил валидны как JSON
        String volumeExample = volumeNormalizer.getExampleRules();
        String brandExample = brandNormalizer.getExampleRules();
        
        // Должны парситься без ошибок
        try {
            objectMapper.readTree(volumeExample);
            objectMapper.readTree(brandExample);
        } catch (Exception e) {
            throw new AssertionError("Example rules should be valid JSON", e);
        }
    }
}