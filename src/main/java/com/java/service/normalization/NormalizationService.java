package com.java.service.normalization;

import com.java.model.enums.NormalizationType;

/**
 * Интерфейс сервиса нормализации данных
 */
public interface NormalizationService {
    
    /**
     * Нормализует значение согласно правилам
     * @param value исходное значение
     * @param normalizationType тип нормализации
     * @param normalizationRule JSON с правилами нормализации
     * @return нормализованное значение
     */
    Object normalize(Object value, NormalizationType normalizationType, String normalizationRule);
    
    /**
     * Проверяет, поддерживается ли тип нормализации
     */
    boolean supports(NormalizationType normalizationType);
}