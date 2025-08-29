package com.java.service.exports.normalization;

import com.java.model.enums.NormalizationType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Фабрика для получения нормализаторов данных
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NormalizationServiceFactory {
    
    private final ApplicationContext applicationContext;
    private final Map<NormalizationType, NormalizationService> normalizers = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // Регистрируем нормализаторы
        normalizers.put(NormalizationType.VOLUME,
                applicationContext.getBean("volumeNormalizer", NormalizationService.class));
        
        normalizers.put(NormalizationType.BRAND,
                applicationContext.getBean("brandNormalizer", NormalizationService.class));
        
        log.info("Инициализировано {} нормализаторов", normalizers.size());
    }
    
    /**
     * Получить нормализатор по типу
     *
     * @param type тип нормализации
     * @return нормализатор или null если не найден
     */
    public NormalizationService getNormalizer(NormalizationType type) {
        NormalizationService normalizer = normalizers.get(type);
        
        if (normalizer == null) {
            log.warn("Нормализатор для типа {} не найден", type);
        }
        
        return normalizer;
    }
    
    /**
     * Проверить поддержку типа нормализации
     *
     * @param type тип нормализации
     * @return true если поддерживается
     */
    public boolean isSupported(NormalizationType type) {
        return normalizers.containsKey(type);
    }
    
    /**
     * Получить все доступные типы нормализации
     *
     * @return множество поддерживаемых типов
     */
    public java.util.Set<NormalizationType> getSupportedTypes() {
        return normalizers.keySet();
    }
}