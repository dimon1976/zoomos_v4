package com.java.controller;

import com.java.model.enums.NormalizationType;
import com.java.service.normalization.NormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST контроллер для тестирования нормализации
 */
@RestController
@RequestMapping("/api/normalization")
@Slf4j
public class NormalizationController {
    
    private final NormalizationService normalizationService;
    
    public NormalizationController(@Qualifier("normalizationServiceImpl") NormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }
    
    /**
     * Тестирование нормализации значения
     */
    @PostMapping("/test")
    public ResponseEntity<String> testNormalization(@RequestBody Map<String, Object> request) {
        try {
            Object value = request.get("value");
            String typeStr = (String) request.get("type");
            String rule = (String) request.get("rule");
            
            if (value == null || typeStr == null || typeStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Не указано значение или тип нормализации");
            }
            
            NormalizationType type;
            try {
                type = NormalizationType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("Неизвестный тип нормализации: " + typeStr);
            }
            
            // Применяем нормализацию
            Object result = normalizationService.normalize(value, type, rule);
            
            log.debug("Тест нормализации: {} -> {} (тип: {}, правило: {})", 
                     value, result, type, rule);
            
            return ResponseEntity.ok(result != null ? result.toString() : "null");
            
        } catch (Exception e) {
            log.error("Ошибка тестирования нормализации", e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка нормализации: " + e.getMessage());
        }
    }
    
    /**
     * Получение примеров для типа нормализации
     */
    @GetMapping("/examples/{type}")
    public ResponseEntity<Map<String, String>> getExamples(@PathVariable String type) {
        try {
            NormalizationType normType = NormalizationType.valueOf(type.toUpperCase());
            
            Map<String, String> examples = switch (normType) {
                case VOLUME -> Map.of(
                    "0.7л", "0.7",
                    "0,7 л.", "0.7",
                    "500мл", "500",
                    "1л", "1"
                );
                case BRAND -> Map.of(
                    "The Macallan", "Macallan",
                    "JAMESON", "Jameson",
                    "jameson irish whiskey", "Jameson",
                    "Macallan, Edition №5", "Macallan"
                );
                case CURRENCY -> Map.of(
                    "$100", "100",
                    "100USD", "100",
                    "€50", "50"
                );
                default -> Map.of("example", "Примеры не определены");
            };
            
            return ResponseEntity.ok(examples);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Неизвестный тип: " + type));
        } catch (Exception e) {
            log.error("Ошибка получения примеров нормализации", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Внутренняя ошибка сервера"));
        }
    }
}