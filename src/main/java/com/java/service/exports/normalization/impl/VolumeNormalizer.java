package com.java.service.exports.normalization.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.service.exports.normalization.NormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Нормализация объемов: "0.7л", "0,7 л.", "0.7 мл" → "0.7"
 */
@Component("volumeNormalizer")
@Slf4j
@RequiredArgsConstructor
public class VolumeNormalizer implements NormalizationService {
    
    private final ObjectMapper objectMapper;
    
    // Паттерн для извлечения числового значения из строки
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([0-9]+[,.]?[0-9]*)");
    
    @Override
    public Object normalize(Object value, String rules) {
        if (value == null || value.toString().trim().isEmpty()) {
            return value;
        }
        
        try {
            String stringValue = value.toString().trim();
            
            // Парсим правила если они есть
            VolumeRules volumeRules = parseRules(rules);
            
            // Извлекаем числовое значение
            if (volumeRules.extractNumeric) {
                stringValue = extractNumericValue(stringValue);
            }
            
            // Заменяем запятую на точку
            if (volumeRules.replaceCommaWithDot) {
                stringValue = stringValue.replace(",", ".");
            }
            
            // Возвращаем в нужном формате
            if ("DECIMAL".equals(volumeRules.resultType)) {
                try {
                    return new BigDecimal(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Не удалось преобразовать в число: {}", stringValue);
                    return value;
                }
            }
            
            return stringValue;
            
        } catch (Exception e) {
            log.warn("Ошибка нормализации объема: {} -> {}", value, e.getMessage());
            return value;
        }
    }
    
    private String extractNumericValue(String input) {
        Matcher matcher = NUMERIC_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return input;
    }
    
    private VolumeRules parseRules(String rules) {
        if (rules == null || rules.trim().isEmpty()) {
            return VolumeRules.defaultRules();
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(rules);
            VolumeRules volumeRules = new VolumeRules();
            
            if (rootNode.has("extractNumeric")) {
                volumeRules.extractNumeric = rootNode.get("extractNumeric").asBoolean();
            }
            if (rootNode.has("replaceCommaWithDot")) {
                volumeRules.replaceCommaWithDot = rootNode.get("replaceCommaWithDot").asBoolean();
            }
            if (rootNode.has("resultType")) {
                volumeRules.resultType = rootNode.get("resultType").asText();
            }
            
            return volumeRules;
            
        } catch (Exception e) {
            log.warn("Ошибка парсинга правил нормализации объема: {}", e.getMessage());
            return VolumeRules.defaultRules();
        }
    }
    
    @Override
    public boolean supports(Object value) {
        return value != null;
    }
    
    @Override
    public String getExampleRules() {
        return """
                {
                  "extractNumeric": true,
                  "removeUnits": ["л", "л.", "мл", "ml"],
                  "replaceCommaWithDot": true,
                  "resultType": "DECIMAL"
                }
                """;
    }
    
    private static class VolumeRules {
        boolean extractNumeric = true;
        boolean replaceCommaWithDot = true;
        String resultType = "STRING";
        
        static VolumeRules defaultRules() {
            VolumeRules rules = new VolumeRules();
            rules.extractNumeric = true;
            rules.replaceCommaWithDot = true;
            rules.resultType = "STRING";
            return rules;
        }
    }
}