package com.java.service.normalization;

import com.java.model.enums.NormalizationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Основной сервис нормализации
 * Делегирует выполнение конкретным нормализаторам
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NormalizationServiceImpl implements NormalizationService {
    
    private final List<NormalizationService> normalizers;
    
    @Override
    public Object normalize(Object value, NormalizationType normalizationType, String normalizationRule) {
        if (value == null || normalizationType == null) {
            return value;
        }
        
        // Находим подходящий нормализатор
        NormalizationService normalizer = findNormalizer(normalizationType);
        if (normalizer == null) {
            log.warn("Нормализатор для типа {} не найден", normalizationType);
            return value;
        }
        
        try {
            Object result = normalizer.normalize(value, normalizationType, normalizationRule);
            log.debug("Нормализация {} '{}' → '{}'", normalizationType, value, result);
            return result;
        } catch (Exception e) {
            log.error("Ошибка нормализации {} для значения '{}': {}", 
                    normalizationType, value, e.getMessage(), e);
            return value; // В случае ошибки возвращаем исходное значение
        }
    }
    
    @Override
    public boolean supports(NormalizationType normalizationType) {
        return findNormalizer(normalizationType) != null;
    }
    
    private NormalizationService findNormalizer(NormalizationType normalizationType) {
        return normalizers.stream()
                .filter(n -> n != this) // Исключаем себя из поиска
                .filter(n -> n.supports(normalizationType))
                .findFirst()
                .orElse(null);
    }
}