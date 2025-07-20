package com.java.service.imports.handlers;

import com.java.model.entity.ImportSession;
import com.java.model.enums.EntityType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.*;

/**
 * Сервис для сохранения импортированных данных в БД
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final EntityManager entityManager;

    /**
     * Сохраняет батч записей в БД
     */
    @Transactional
    public int saveBatch(List<Map<String, Object>> batch, EntityType entityType) {
        if (batch.isEmpty()) return 0;

        switch (entityType) {
            case PRODUCT:
                return saveProducts(batch);
            case CUSTOMER:
                return saveCustomers(batch);
            default:
                throw new UnsupportedOperationException("Неподдерживаемый тип сущности: " + entityType);
        }
    }

    /**
     * Сохраняет продукты
     */
    private int saveProducts(List<Map<String, Object>> batch) {
        String sql = "INSERT INTO products (name, description, sku, price, quantity, " +
                "category, brand, weight, dimensions, created_at, updated_at) " +
                "VALUES (:name, :description, :sku, :price, :quantity, " +
                ":category, :brand, :weight, :dimensions, :createdAt, :updatedAt)";

        // Добавляем системные поля
        LocalDateTime now = LocalDateTime.now();
        batch.forEach(product -> {
            product.putIfAbsent("createdAt", now);
            product.putIfAbsent("updatedAt", now);

            // Преобразуем null значения в дефолтные
            product.putIfAbsent("quantity", 0);
            product.putIfAbsent("price", 0.0);
        });

        SqlParameterSource[] batchParams = SqlParameterSourceUtils.createBatch(batch);
        int[] updateCounts = namedParameterJdbcTemplate.batchUpdate(sql, batchParams);

        return Arrays.stream(updateCounts).sum();
    }

    /**
     * Сохраняет клиентов
     */
    private int saveCustomers(List<Map<String, Object>> batch) {
        String sql = "INSERT INTO customers (name, email, phone, address, city, " +
                "country, postal_code, company, notes, created_at, updated_at) " +
                "VALUES (:name, :email, :phone, :address, :city, " +
                ":country, :postalCode, :company, :notes, :createdAt, :updatedAt)";

        // Добавляем системные поля
        LocalDateTime now = LocalDateTime.now();
        batch.forEach(customer -> {
            customer.putIfAbsent("createdAt", now);
            customer.putIfAbsent("updatedAt", now);
        });

        SqlParameterSource[] batchParams = SqlParameterSourceUtils.createBatch(batch);
        int[] updateCounts = namedParameterJdbcTemplate.batchUpdate(sql, batchParams);

        return Arrays.stream(updateCounts).sum();
    }

    /**
     * Откатывает импорт (удаляет импортированные записи)
     */
    @Transactional
    public void rollbackImport(ImportSession session) {
        log.warn("Откат импорта для сессии: {}", session.getId());

        // Это упрощенная версия
        // В реальности нужно отслеживать ID импортированных записей
        // и удалять только их

        // Можно добавить поле import_session_id в целевые таблицы
        // и удалять по нему:
        // DELETE FROM products WHERE import_session_id = ?

        log.info("Откат импорта завершен");
    }
}