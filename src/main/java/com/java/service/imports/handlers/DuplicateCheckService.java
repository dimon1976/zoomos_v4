package com.java.service.imports.handlers;

import com.java.model.entity.ImportTemplate;
import com.java.model.entity.ImportTemplateField;
import com.java.model.enums.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис проверки дубликатов при импорте
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DuplicateCheckService {

    private final JdbcTemplate jdbcTemplate;
    // Если Redis не используется, можно использовать in-memory кеш
    private final Map<String, Set<String>> inMemoryCache = new HashMap<>();

    /**
     * Генерирует ключ для проверки дубликатов
     */
    public String generateDuplicateKey(Map<String, Object> data, ImportTemplate template) {
        // Находим уникальные поля
        List<ImportTemplateField> uniqueFields = template.getFields().stream()
                .filter(ImportTemplateField::getIsUnique)
                .collect(Collectors.toList());

        if (uniqueFields.isEmpty()) {
            // Если уникальные поля не заданы, используем все поля
            return data.values().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("|"));
        }

        // Формируем ключ из уникальных полей
        return uniqueFields.stream()
                .map(field -> String.valueOf(data.get(field.getEntityFieldName())))
                .collect(Collectors.joining("|"));
    }

    /**
     * Проверяет, является ли запись дубликатом
     */
    public boolean isDuplicate(String key, EntityType entityType) {
        String cacheKey = entityType.name() + ":" + key;

        // Проверяем в кеше
        Set<String> typeCache = inMemoryCache.computeIfAbsent(entityType.name(), k -> new HashSet<>());
        if (typeCache.contains(key)) {
            return true;
        }

        // Проверяем в БД
        return checkInDatabase(key, entityType);
    }

    /**
     * Сохраняет ключи в кеш
     */
    public void saveDuplicateKeys(Set<String> keys, EntityType entityType) {
        Set<String> typeCache = inMemoryCache.computeIfAbsent(entityType.name(), k -> new HashSet<>());
        typeCache.addAll(keys);
    }

    /**
     * Проверяет наличие дубликата в базе данных
     */
    private boolean checkInDatabase(String key, EntityType entityType) {
        // Реализация зависит от структуры БД
        // Это примерная реализация

        String tableName = getTableName(entityType);
        String[] keyParts = key.split("\\|");

        // Формируем запрос для проверки
        StringBuilder query = new StringBuilder("SELECT COUNT(*) FROM ").append(tableName);
        query.append(" WHERE 1=1");

        List<Object> params = new ArrayList<>();

        // Добавляем условия для каждой части ключа
        // Это упрощенная версия, в реальности нужно знать какие поля проверять
        if (keyParts.length > 0 && !keyParts[0].equals("null")) {
            query.append(" AND name = ?");
            params.add(keyParts[0]);
        }

        if (params.isEmpty()) {
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(query.toString(),
                    params.toArray(), Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Ошибка проверки дубликата в БД", e);
            return false;
        }
    }

    /**
     * Очищает кеш дубликатов
     */
    public void clearCache(EntityType entityType) {
        inMemoryCache.remove(entityType.name());
    }

    /**
     * Получает имя таблицы по типу сущности
     */
    private String getTableName(EntityType entityType) {
        switch (entityType) {
            case AV_DATA:
                return "av_data";
            case AV_HANDBOOK:
                return "customers";
            default:
                throw new IllegalArgumentException("Неизвестный тип сущности: " + entityType);
        }
    }
}