package com.java.repository;

import com.java.model.Client;
import com.java.model.entity.ImportTemplate;
import com.java.model.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportTemplateRepository extends JpaRepository<ImportTemplate, Long> {

    // Найти активные шаблоны клиента
    List<ImportTemplate> findByClientAndIsActiveTrue(Client client);

    // Найти шаблоны по типу сущности
    List<ImportTemplate> findByEntityTypeAndIsActiveTrue(EntityType entityType);

    // Найти шаблон по имени и клиенту
    Optional<ImportTemplate> findByNameAndClient(String name, Client client);

    // Проверка существования шаблона с таким именем у клиента
    boolean existsByNameAndClient(String name, Client client);

    // Найти шаблон с полями
    @Query("SELECT t FROM ImportTemplate t LEFT JOIN FETCH t.fields WHERE t.id = :id")
    Optional<ImportTemplate> findByIdWithFields(@Param("id") Long id);

    // Найти все шаблоны клиента с количеством использований
    @Query("SELECT t, COUNT(s) FROM ImportTemplate t " +
            "LEFT JOIN ImportSession s ON s.template = t " +
            "WHERE t.client = :client " +
            "GROUP BY t")
    List<Object[]> findTemplatesWithUsageCount(@Param("client") Client client);
}