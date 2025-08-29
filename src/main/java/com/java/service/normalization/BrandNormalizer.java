package com.java.service.normalization;

import com.java.model.enums.NormalizationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Нормализатор брендов
 * Превращает "The Macallan", "MACALLAN", "Macallan, Edition №5" в "Macallan"
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BrandNormalizer implements NormalizationService {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Object normalize(Object value, NormalizationType normalizationType, String normalizationRule) {
        if (value == null) {
            return null;
        }
        
        String stringValue = value.toString().trim();
        if (stringValue.isEmpty()) {
            return stringValue;
        }
        
        try {
            // Парсим правила нормализации
            BrandNormalizationRule rules = parseRules(normalizationRule);
            return applyBrandNormalization(stringValue, rules);
        } catch (Exception e) {
            log.warn("Ошибка нормализации бренда '{}': {}", stringValue, e.getMessage());
            return value;
        }
    }
    
    @Override
    public boolean supports(NormalizationType normalizationType) {
        return NormalizationType.BRAND == normalizationType;
    }
    
    private BrandNormalizationRule parseRules(String normalizationRule) {
        if (normalizationRule == null || normalizationRule.trim().isEmpty()) {
            return BrandNormalizationRule.defaultRules();
        }
        
        try {
            Map<String, Object> rulesMap = objectMapper.readValue(normalizationRule, new TypeReference<Map<String, Object>>() {});
            return BrandNormalizationRule.from(rulesMap);
        } catch (Exception e) {
            log.warn("Ошибка парсинга правил нормализации '{}', используются правила по умолчанию: {}", 
                    normalizationRule, e.getMessage());
            return BrandNormalizationRule.defaultRules();
        }
    }
    
    private String applyBrandNormalization(String value, BrandNormalizationRule rules) {
        String result = value.trim();
        
        // 1. Убираем артикли в начале
        if (rules.getRemoveArticles() != null) {
            for (String article : rules.getRemoveArticles()) {
                String articlePattern = "^" + article + "\\s+";
                result = result.replaceAll("(?i)" + articlePattern, "");
            }
        }
        
        // 2. Извлекаем основной бренд (до первой запятой)
        if (rules.isExtractMainBrand()) {
            String splitBy = rules.getExtractMainBrandSplitBy();
            if (splitBy != null && result.contains(splitBy)) {
                result = result.split(splitBy)[0].trim();
            }
        }
        
        // 3. Нормализация регистра
        if (rules.getCaseNormalization() != null) {
            switch (rules.getCaseNormalization()) {
                case "UPPER_CASE":
                    result = result.toUpperCase();
                    break;
                case "LOWER_CASE":
                    result = result.toLowerCase();
                    break;
                case "PROPER_CASE":
                    result = toProperCase(result);
                    break;
            }
        }
        
        // 4. Убираем лишние пробелы
        result = result.replaceAll("\\s+", " ").trim();
        
        log.debug("Нормализация бренда: '{}' → '{}'", value, result);
        return result;
    }
    
    private String toProperCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Правила нормализации брендов
     */
    public static class BrandNormalizationRule {
        private List<String> removeArticles = List.of("The", "A", "An", "the", "a", "an");
        private boolean extractMainBrand = true;
        private String extractMainBrandSplitBy = ",";
        private String caseNormalization = "PROPER_CASE";
        
        public static BrandNormalizationRule defaultRules() {
            return new BrandNormalizationRule();
        }
        
        public static BrandNormalizationRule from(Map<String, Object> rulesMap) {
            BrandNormalizationRule rules = new BrandNormalizationRule();
            
            if (rulesMap.containsKey("removeArticles")) {
                @SuppressWarnings("unchecked")
                List<String> articles = (List<String>) rulesMap.get("removeArticles");
                rules.removeArticles = articles;
            }
            
            if (rulesMap.containsKey("extractMainBrand")) {
                rules.extractMainBrand = (Boolean) rulesMap.get("extractMainBrand");
            }
            
            if (rulesMap.containsKey("extractMainBrandSplitBy")) {
                rules.extractMainBrandSplitBy = (String) rulesMap.get("extractMainBrandSplitBy");
            }
            
            if (rulesMap.containsKey("caseNormalization")) {
                rules.caseNormalization = (String) rulesMap.get("caseNormalization");
            }
            
            return rules;
        }
        
        // Getters
        public List<String> getRemoveArticles() { return removeArticles; }
        public boolean isExtractMainBrand() { return extractMainBrand; }
        public String getExtractMainBrandSplitBy() { return extractMainBrandSplitBy; }
        public String getCaseNormalization() { return caseNormalization; }
    }
}