package com.java.repository;

import com.java.model.Client;
import com.java.model.entity.ExportTemplate;
import com.java.model.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportTemplateRepository extends JpaRepository<ExportTemplate, Long> {

    List<ExportTemplate> findByClientAndIsActiveTrue(Client client);

    List<ExportTemplate> findByClientAndEntityTypeAndIsActiveTrue(Client client, EntityType entityType);

    Optional<ExportTemplate> findByNameAndClient(String name, Client client);

    boolean existsByNameAndClient(String name, Client client);

    @Query("SELECT t FROM ExportTemplate t LEFT JOIN FETCH t.fields WHERE t.id = :id")
    Optional<ExportTemplate> findByIdWithFields(@Param("id") Long id);

    //    @Query("SELECT t FROM ExportTemplate t LEFT JOIN FETCH t.fields LEFT JOIN FETCH t.filters WHERE t.id = :id")
    @Query("SELECT DISTINCT t FROM ExportTemplate t LEFT JOIN FETCH t.fields LEFT JOIN FETCH t.filters WHERE t.id = :id")
    Optional<ExportTemplate> findByIdWithFieldsAndFilters(@Param("id") Long id);
}