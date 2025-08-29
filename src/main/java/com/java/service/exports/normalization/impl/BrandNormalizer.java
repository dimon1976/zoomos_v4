package com.java.service.exports.normalization.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.service.exports.normalization.NormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Нормализация брендов: "The Macallan", "Macallan, Edition №5" → "Macallan"
 */
@Component("brandNormalizer")
@Slf4j
@RequiredArgsConstructor
public class BrandNormalizer implements NormalizationService {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public Object normalize(Object value, String rules) {
        if (value == null || value.toString().trim().isEmpty()) {
            return value;
        }
        
        try {
            String stringValue = value.toString().trim();
            
            // Парсим правила если они есть
            BrandRules brandRules = parseRules(rules);
            
            // Убираем артикли
            if (brandRules.removeArticles && !brandRules.articles.isEmpty()) {
                stringValue = removeArticles(stringValue, brandRules.articles);
            }
            
            // Извлекаем основной бренд (до запятой)
            if (brandRules.extractMainBrand && brandRules.splitBy != null) {
                if (brandRules.takeFirst && stringValue.contains(brandRules.splitBy)) {
                    stringValue = stringValue.split(brandRules.splitBy)[0].trim();
                }
            }
            
            // Приводим регистр
            if ("PROPER_CASE".equals(brandRules.caseNormalization)) {
                stringValue = toProperCase(stringValue);
            } else if ("UPPER_CASE".equals(brandRules.caseNormalization)) {
                stringValue = stringValue.toUpperCase();
            } else if ("LOWER_CASE".equals(brandRules.caseNormalization)) {
                stringValue = stringValue.toLowerCase();
            }
            
            // Убираем лишние пробелы и спецсимволы
            stringValue = cleanupString(stringValue);
            
            return stringValue;
            
        } catch (Exception e) {
            log.warn("Ошибка нормализации бренда: {} -> {}", value, e.getMessage());
            return value;
        }
    }
    
    private String removeArticles(String input, List<String> articles) {
        for (String article : articles) {
            // Убираем артикль в начале строки
            if (input.toLowerCase().startsWith(article.toLowerCase() + " ")) {
                input = input.substring(article.length() + 1).trim();
            }
        }
        return input;
    }
    
    private String toProperCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : input.toCharArray()) {
            if (Character.isLetter(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            } else {
                result.append(c);
                capitalizeNext = Character.isWhitespace(c);
            }
        }
        
        return result.toString();
    }
    
    private String cleanupString(String input) {
        // Убираем лишние пробелы
        input = input.replaceAll("\\s+", " ").trim();
        
        // Убираем специальные символы в конце строки
        input = input.replaceAll("[\\s.,;:!?]+$", "");
        
        return input;
    }
    
    private BrandRules parseRules(String rules) {
        if (rules == null || rules.trim().isEmpty()) {
            return BrandRules.defaultRules();
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(rules);
            BrandRules brandRules = new BrandRules();
            
            // Проверяем формат поля removeArticles - может быть boolean или массивом
            if (rootNode.has("removeArticles")) {
                JsonNode removeArticlesNode = rootNode.get("removeArticles");
                if (removeArticlesNode.isBoolean()) {
                    brandRules.removeArticles = removeArticlesNode.asBoolean();
                } else if (removeArticlesNode.isArray()) {
                    // Если массив - то это список артиклей для удаления
                    brandRules.removeArticles = true;
                    brandRules.articles.clear();
                    for (JsonNode articleNode : removeArticlesNode) {
                        brandRules.articles.add(articleNode.asText());
                    }
                }
            }
            
            // Отдельное поле articles (для совместимости)
            if (rootNode.has("articles")) {
                brandRules.articles.clear();
                for (JsonNode articleNode : rootNode.get("articles")) {
                    brandRules.articles.add(articleNode.asText());
                }
            }
            
            if (rootNode.has("extractMainBrand")) {
                JsonNode extractNode = rootNode.get("extractMainBrand");
                brandRules.extractMainBrand = true;
                if (extractNode.has("splitBy")) {
                    brandRules.splitBy = extractNode.get("splitBy").asText();
                }
                if (extractNode.has("takeFirst")) {
                    brandRules.takeFirst = extractNode.get("takeFirst").asBoolean();
                }
            }
            
            if (rootNode.has("caseNormalization")) {
                brandRules.caseNormalization = rootNode.get("caseNormalization").asText();
            }
            
            return brandRules;
            
        } catch (Exception e) {
            log.warn("Ошибка парсинга правил нормализации бренда: {}", e.getMessage());
            return BrandRules.defaultRules();
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
                  "removeArticles": ["The", "A", "An"],
                  "extractMainBrand": {
                    "splitBy": ",",
                    "takeFirst": true
                  },
                  "caseNormalization": "PROPER_CASE"
                }
                """;
    }
    
    private static class BrandRules {
        boolean removeArticles = true;
        List<String> articles = Arrays.asList("The", "A", "An");
        boolean extractMainBrand = true;
        String splitBy = ",";
        boolean takeFirst = true;
        String caseNormalization = "PROPER_CASE";
        
        static BrandRules defaultRules() {
            return new BrandRules();
        }
    }
}