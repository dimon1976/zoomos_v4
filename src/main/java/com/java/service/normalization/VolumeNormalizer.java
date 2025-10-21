package com.java.service.normalization;

import com.java.model.enums.NormalizationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нормализатор объемов
 * Превращает "0.7л", "0,7 л.", "0.7" в "0.7"
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VolumeNormalizer implements NormalizationService {
    
    private final ObjectMapper objectMapper;
    
    // Паттерн для извлечения числового значения
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([0-9]*[.,]?[0-9]+)");


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
            VolumeNormalizationRule rules = parseRules(normalizationRule);
            return applyVolumeNormalization(stringValue, rules);
        } catch (Exception e) {
            log.warn("Ошибка нормализации объема '{}': {}", stringValue, e.getMessage());
            return value;
        }
    }
    
    @Override
    public boolean supports(NormalizationType normalizationType) {
        return NormalizationType.VOLUME == normalizationType;
    }
    
    private VolumeNormalizationRule parseRules(String normalizationRule) {
        if (normalizationRule == null || normalizationRule.trim().isEmpty()) {
            return VolumeNormalizationRule.defaultRules();
        }
        
        try {
            Map<String, Object> rulesMap = objectMapper.readValue(normalizationRule, new TypeReference<Map<String, Object>>() {});
            return VolumeNormalizationRule.from(rulesMap);
        } catch (Exception e) {
            log.warn("Ошибка парсинга правил нормализации '{}', используются правила по умолчанию: {}", 
                    normalizationRule, e.getMessage());
            return VolumeNormalizationRule.defaultRules();
        }
    }

    private String applyVolumeNormalization(String value, VolumeNormalizationRule rules) {
        String result = value;

        // 1. Извлекаем числовое значение если нужно
        if (rules.isExtractNumeric()) {
            Matcher matcher = NUMERIC_PATTERN.matcher(result);
            if (matcher.find()) {
                result = matcher.group(1);
            }
        }

        // 2. Убираем единицы измерения, но не трогаем десятичные точки
        if (rules.getRemoveUnits() != null) {
            for (String unit : rules.getRemoveUnits()) {
                if (unit.equals(".") || unit.equals(",")) {
                    // игнорируем знаки пунктуации, которые могут быть частью числа
                    continue;
                }
                result = result.replace(unit, "").trim();
            }
        }

        // 3. Заменяем запятую на точку
        if (rules.isReplaceCommaWithDot()) {
            result = result.replace(",", ".");
        }

        // 4. Убираем лишние пробелы
        result = result.replaceAll("\\s+", "").trim();

        log.debug("Нормализация объема: '{}' → '{}'", value, result);
        return result;
    }


    /**
     * Правила нормализации объемов
     */
    public static class VolumeNormalizationRule {
        private boolean extractNumeric = true;
        private boolean replaceCommaWithDot = true;
        private List<String> removeUnits = List.of("л", "л.", "мл", "ml", " ");
        
        public static VolumeNormalizationRule defaultRules() {
            return new VolumeNormalizationRule();
        }
        
        public static VolumeNormalizationRule from(Map<String, Object> rulesMap) {
            VolumeNormalizationRule rules = new VolumeNormalizationRule();
            
            if (rulesMap.containsKey("extractNumeric")) {
                rules.extractNumeric = (Boolean) rulesMap.get("extractNumeric");
            }
            
            if (rulesMap.containsKey("replaceCommaWithDot")) {
                rules.replaceCommaWithDot = (Boolean) rulesMap.get("replaceCommaWithDot");
            }
            
            if (rulesMap.containsKey("removeUnits")) {
                @SuppressWarnings("unchecked")
                List<String> units = (List<String>) rulesMap.get("removeUnits");
                rules.removeUnits = units;
            }
            
            return rules;
        }
        
        // Getters
        public boolean isExtractNumeric() { return extractNumeric; }
        public boolean isReplaceCommaWithDot() { return replaceCommaWithDot; }
        public List<String> getRemoveUnits() { return removeUnits; }
    }
}