package com.java.service.exports.normalization;

/**
 * Интерфейс для нормализации данных перед экспортом
 */
public interface NormalizationService {
    
    /**
     * Нормализовать значение согласно правилам
     *
     * @param value исходное значение
     * @param rules JSON-строка с правилами нормализации
     * @return нормализованное значение
     */
    Object normalize(Object value, String rules);
    
    /**
     * Проверка поддержки типа значения
     *
     * @param value значение для проверки
     * @return true если тип поддерживается
     */
    boolean supports(Object value);
    
    /**
     * Получить пример правил для UI
     *
     * @return пример JSON с правилами
     */
    String getExampleRules();
}