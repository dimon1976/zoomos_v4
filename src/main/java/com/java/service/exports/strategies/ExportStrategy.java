package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс стратегии обработки данных при экспорте
 */
public interface ExportStrategy {

    /**
     * Получить название стратегии
     */
    String getName();

    /**
     * Обработать данные согласно стратегии
     *
     * @param data исходные данные для экспорта
     * @param template шаблон экспорта
     * @param context контекст с дополнительными параметрами
     * @return обработанные данные
     */
    List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context
    );

    /**
     * Валидация возможности применения стратегии
     */
    default boolean canApply(ExportTemplate template) {
        return true;
    }

    /**
     * Получить необходимые параметры контекста
     */
    default List<String> getRequiredContextParams() {
        return List.of();
    }
}