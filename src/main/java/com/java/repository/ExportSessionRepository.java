package com.java.repository;

import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportTemplate;
import com.java.model.enums.ExportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExportSessionRepository extends JpaRepository<ExportSession, Long> {

    Optional<ExportSession> findByFileOperationId(Long fileOperationId);

    Page<ExportSession> findByTemplate(ExportTemplate template, Pageable pageable);

    List<ExportSession> findByStatus(ExportStatus status);

    @Query("SELECT es FROM ExportSession es WHERE es.template.client.id = :clientId ORDER BY es.startedAt DESC")
    Page<ExportSession> findByClientId(@Param("clientId") Long clientId, Pageable pageable);

    @Query("SELECT es FROM ExportSession es WHERE es.template = :template AND es.startedAt BETWEEN :from AND :to")
    List<ExportSession> findByTemplateAndDateRange(@Param("template") ExportTemplate template,
                                                   @Param("from") ZonedDateTime from,
                                                   @Param("to") ZonedDateTime to);

    // Добавить к существующим методам:

    @Query("SELECT COUNT(es) FROM ExportSession es WHERE es.template.id = :templateId")
    Long countByTemplateId(@Param("templateId") Long templateId);

    @Query("SELECT es FROM ExportSession es WHERE es.template.id = :templateId ORDER BY es.startedAt DESC")
    Optional<ExportSession> findLastByTemplateId(@Param("templateId") Long templateId, Pageable pageable);

    default Optional<ExportSession> findLastByTemplateId(Long templateId) {
        return findLastByTemplateId(templateId, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("SELECT es.template FROM ExportSession es " +
            "WHERE es.template.client.id = :clientId " +
            "ORDER BY es.startedAt DESC")
    Optional<ExportTemplate> findLastUsedTemplateByClientId(@Param("clientId") Long clientId, Pageable pageable);

    default Optional<ExportTemplate> findLastUsedTemplateByClientId(Long clientId) {
        return findLastUsedTemplateByClientId(clientId, PageRequest.of(0, 1)).stream().findFirst();
    }
}