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
 * Нормализатор валют
 * Превращает "$100", "100USD", "€50" в "100", "100", "50"
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CurrencyNormalizer implements NormalizationService {
    
    private final ObjectMapper objectMapper;
    
    // Паттерн для извлечения числового значения из валютных строк
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("([\\d,\\.]+)");
    
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
            CurrencyNormalizationRule rules = parseRules(normalizationRule);
            return applyCurrencyNormalization(stringValue, rules);
        } catch (Exception e) {
            log.warn("Ошибка нормализации валюты '{}': {}", stringValue, e.getMessage());
            return value;
        }
    }
    
    @Override
    public boolean supports(NormalizationType normalizationType) {
        return NormalizationType.CURRENCY == normalizationType;
    }
    
    private CurrencyNormalizationRule parseRules(String normalizationRule) {
        if (normalizationRule == null || normalizationRule.trim().isEmpty()) {
            return CurrencyNormalizationRule.defaultRules();
        }
        
        try {
            Map<String, Object> rulesMap = objectMapper.readValue(normalizationRule, new TypeReference<Map<String, Object>>() {});
            return CurrencyNormalizationRule.from(rulesMap);
        } catch (Exception e) {
            log.warn("Ошибка парсинга правил нормализации валют '{}', используются правила по умолчанию: {}", 
                    normalizationRule, e.getMessage());
            return CurrencyNormalizationRule.defaultRules();
        }
    }
    
    private String applyCurrencyNormalization(String value, CurrencyNormalizationRule rules) {
        String result = value.trim();
        
        // 1. Убираем валютные символы и коды
        if (rules.getRemoveCurrencySymbols() != null) {
            for (String symbol : rules.getRemoveCurrencySymbols()) {
                result = result.replace(symbol, "").trim();
            }
        }
        
        // 2. Извлекаем числовое значение
        if (rules.isExtractNumeric()) {
            Matcher matcher = CURRENCY_PATTERN.matcher(result);
            if (matcher.find()) {
                result = matcher.group(1);
            }
        }
        
        // 3. Заменяем запятую на точку в десятичной дроби
        if (rules.isReplaceCommaWithDot()) {
            result = result.replace(",", ".");
        }
        
        // 4. Убираем лишние пробелы
        result = result.replaceAll("\\s+", "").trim();
        
        // 5. Валидация числа
        if (rules.isValidateNumeric()) {
            try {
                Double.parseDouble(result);
            } catch (NumberFormatException e) {
                log.warn("Результат нормализации валюты '{}' не является числом: '{}'", value, result);
                return value.toString(); // Возвращаем исходное значение если результат не число
            }
        }
        
        log.debug("Нормализация валюты: '{}' → '{}'", value, result);
        return result;
    }
    
    /**
     * Правила нормализации валют
     */
    public static class CurrencyNormalizationRule {
        private boolean extractNumeric = true;
        private boolean replaceCommaWithDot = true;
        private boolean validateNumeric = true;
        private List<String> removeCurrencySymbols = List.of(
            "$", "€", "£", "₽", "¥", "₴", "₸", "₾", 
            "USD", "EUR", "GBP", "RUB", "CNY", "JPY",
            "usd", "eur", "gbp", "rub", "cny", "jpy",
            " ", ".", "руб", "коп", "долл"
        );
        
        public static CurrencyNormalizationRule defaultRules() {
            return new CurrencyNormalizationRule();
        }
        
        @SuppressWarnings("unchecked")
        public static CurrencyNormalizationRule from(Map<String, Object> rulesMap) {
            CurrencyNormalizationRule rules = new CurrencyNormalizationRule();
            
            if (rulesMap.containsKey("extractNumeric")) {
                rules.extractNumeric = (Boolean) rulesMap.get("extractNumeric");
            }
            
            if (rulesMap.containsKey("replaceCommaWithDot")) {
                rules.replaceCommaWithDot = (Boolean) rulesMap.get("replaceCommaWithDot");
            }
            
            if (rulesMap.containsKey("validateNumeric")) {
                rules.validateNumeric = (Boolean) rulesMap.get("validateNumeric");
            }
            
            if (rulesMap.containsKey("removeCurrencySymbols")) {
                rules.removeCurrencySymbols = (List<String>) rulesMap.get("removeCurrencySymbols");
            }
            
            return rules;
        }
        
        // Getters
        public boolean isExtractNumeric() { return extractNumeric; }
        public boolean isReplaceCommaWithDot() { return replaceCommaWithDot; }
        public boolean isValidateNumeric() { return validateNumeric; }
        public List<String> getRemoveCurrencySymbols() { return removeCurrencySymbols; }
    }
}