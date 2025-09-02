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
 * Нормализатор с пользовательскими правилами
 * Поддерживает простые правила замены типа "старое значение" -> "новое значение"
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomNormalizer implements NormalizationService {
    
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
            CustomNormalizationRule rules = parseRules(normalizationRule);
            return applyCustomNormalization(stringValue, rules);
        } catch (Exception e) {
            log.warn("Ошибка пользовательской нормализации '{}': {}", stringValue, e.getMessage());
            return value;
        }
    }
    
    @Override
    public boolean supports(NormalizationType normalizationType) {
        return NormalizationType.CUSTOM == normalizationType;
    }
    
    private CustomNormalizationRule parseRules(String normalizationRule) {
        if (normalizationRule == null || normalizationRule.trim().isEmpty()) {
            return CustomNormalizationRule.empty();
        }
        
        try {
            Map<String, Object> rulesMap = objectMapper.readValue(normalizationRule, new TypeReference<Map<String, Object>>() {});
            return CustomNormalizationRule.from(rulesMap);
        } catch (Exception e) {
            log.warn("Ошибка парсинга пользовательских правил '{}': {}", 
                    normalizationRule, e.getMessage());
            return CustomNormalizationRule.empty();
        }
    }
    
    private String applyCustomNormalization(String value, CustomNormalizationRule rules) {
        String result = value;
        
        // 1. Применяем простые правила замены
        if (rules.getCustomRules() != null && rules.getCustomRules().size() >= 2) {
            List<String> customRules = rules.getCustomRules();
            
            // Правила идут парами: [старое_значение, новое_значение, старое_значение2, новое_значение2, ...]
            for (int i = 0; i < customRules.size() - 1; i += 2) {
                String oldValue = customRules.get(i);
                String newValue = customRules.get(i + 1);
                
                if (oldValue != null && newValue != null) {
                    // Точное совпадение (с учетом регистра)
                    if (result.equals(oldValue)) {
                        result = newValue;
                        log.debug("Пользовательская нормализация (точное совпадение): '{}' → '{}'", value, result);
                        return result;
                    }
                    
                    // Совпадение без учета регистра
                    if (rules.isCaseInsensitive() && result.equalsIgnoreCase(oldValue)) {
                        result = newValue;
                        log.debug("Пользовательская нормализация (без учета регистра): '{}' → '{}'", value, result);
                        return result;
                    }
                    
                    // Частичное совпадение (содержит)
                    if (rules.isPartialMatch() && result.toLowerCase().contains(oldValue.toLowerCase())) {
                        result = newValue;
                        log.debug("Пользовательская нормализация (частичное совпадение): '{}' → '{}'", value, result);
                        return result;
                    }
                }
            }
        }
        
        // 2. Применяем правила замены Map-формата
        if (rules.getReplacementMap() != null) {
            for (Map.Entry<String, String> entry : rules.getReplacementMap().entrySet()) {
                String oldValue = entry.getKey();
                String newValue = entry.getValue();
                
                if (oldValue != null && newValue != null) {
                    // Точное совпадение
                    if (result.equals(oldValue)) {
                        result = newValue;
                        log.debug("Пользовательская нормализация (Map): '{}' → '{}'", value, result);
                        return result;
                    }
                    
                    // Совпадение без учета регистра
                    if (rules.isCaseInsensitive() && result.equalsIgnoreCase(oldValue)) {
                        result = newValue;
                        log.debug("Пользовательская нормализация (Map, без учета регистра): '{}' → '{}'", value, result);
                        return result;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Правила пользовательской нормализации
     */
    public static class CustomNormalizationRule {
        private List<String> customRules;
        private Map<String, String> replacementMap;
        private boolean caseInsensitive = false;
        private boolean partialMatch = false;
        
        public static CustomNormalizationRule empty() {
            return new CustomNormalizationRule();
        }
        
        @SuppressWarnings("unchecked")
        public static CustomNormalizationRule from(Map<String, Object> rulesMap) {
            CustomNormalizationRule rules = new CustomNormalizationRule();
            
            if (rulesMap.containsKey("customRules")) {
                rules.customRules = (List<String>) rulesMap.get("customRules");
            }
            
            if (rulesMap.containsKey("replacementMap")) {
                rules.replacementMap = (Map<String, String>) rulesMap.get("replacementMap");
            }
            
            if (rulesMap.containsKey("caseInsensitive")) {
                rules.caseInsensitive = (Boolean) rulesMap.get("caseInsensitive");
            }
            
            if (rulesMap.containsKey("partialMatch")) {
                rules.partialMatch = (Boolean) rulesMap.get("partialMatch");
            }
            
            return rules;
        }
        
        // Getters
        public List<String> getCustomRules() { return customRules; }
        public Map<String, String> getReplacementMap() { return replacementMap; }
        public boolean isCaseInsensitive() { return caseInsensitive; }
        public boolean isPartialMatch() { return partialMatch; }
    }
}