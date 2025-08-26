package com.java.service.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Сервис для получения уникальных значений полей из базы данных для фильтрации статистики
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldValuesService {

    private final JdbcTemplate jdbcTemplate;
    
    // Максимальное количество уникальных значений для возврата (для производительности)
    private static final int MAX_VALUES_LIMIT = 1000;

    /**
     * Получает уникальные значения для указанного поля из таблицы av_data
     * Результат кэшируется на 1 час
     *
     * @param fieldName название поля
     * @return список уникальных значений (максимум 1000)
     */
    @Cacheable(value = "fieldValues", key = "#fieldName", unless = "#result.size() == 0")
    public List<String> getUniqueFieldValues(String fieldName) {
        log.debug("Получение уникальных значений для поля: {}", fieldName);
        
        // Проверяем, что название поля безопасно (только буквы, цифры и подчеркивания)
        if (!isValidFieldName(fieldName)) {
            log.warn("Небезопасное название поля: {}", fieldName);
            return Collections.emptyList();
        }

        try {
            // Формируем SQL запрос для получения уникальных значений
            String sql = String.format(
                "SELECT DISTINCT %s FROM av_data WHERE %s IS NOT NULL AND %s != '' ORDER BY %s LIMIT ?",
                fieldName, fieldName, fieldName, fieldName
            );

            log.debug("Выполнение SQL запроса: {}", sql);

            List<String> values = jdbcTemplate.queryForList(sql, String.class, MAX_VALUES_LIMIT);

            log.debug("Найдено {} уникальных значений для поля '{}'", values.size(), fieldName);
            
            return values;

        } catch (Exception e) {
            log.error("Ошибка получения уникальных значений для поля '{}': {}", fieldName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Получает количество уникальных значений для поля (без ограничения)
     */
    @Cacheable(value = "fieldValuesCount", key = "#fieldName")
    public Long getUniqueFieldValuesCount(String fieldName) {
        log.debug("Получение количества уникальных значений для поля: {}", fieldName);
        
        if (!isValidFieldName(fieldName)) {
            log.warn("Небезопасное название поля: {}", fieldName);
            return 0L;
        }

        try {
            String sql = String.format(
                "SELECT COUNT(DISTINCT %s) FROM av_data WHERE %s IS NOT NULL AND %s != ''",
                fieldName, fieldName, fieldName
            );

            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            log.debug("Количество уникальных значений для поля '{}': {}", fieldName, count);
            
            return count != null ? count : 0L;

        } catch (Exception e) {
            log.error("Ошибка получения количества уникальных значений для поля '{}': {}", fieldName, e.getMessage());
            return 0L;
        }
    }

    /**
     * Очищает кэш для конкретного поля
     */
    public void evictFieldValuesCache(String fieldName) {
        log.debug("Очистка кэша для поля: {}", fieldName);
        // Кэш будет очищен автоматически через аннотации Spring Cache
    }

    /**
     * Проверяет безопасность названия поля (защита от SQL-инъекций)
     */
    private boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return false;
        }
        
        // Разрешаем только буквы, цифры и подчеркивания
        return fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    /**
     * Получает список всех доступных полей в таблице av_data
     */
    @Cacheable(value = "availableFields")
    public List<String> getAvailableFields() {
        log.debug("Получение списка доступных полей в таблице av_data");
        
        try {
            // Получаем информацию о колонках таблицы
            String sql = """
                SELECT column_name 
                FROM information_schema.columns 
                WHERE table_name = 'av_data' 
                AND table_schema = 'public'
                AND column_name NOT IN ('id', 'created_at', 'updated_at')
                ORDER BY column_name
                """;

            List<String> fields = jdbcTemplate.queryForList(sql, String.class);
            
            log.debug("Найдено {} доступных полей", fields.size());
            return fields;

        } catch (Exception e) {
            log.error("Ошибка получения списка доступных полей: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}