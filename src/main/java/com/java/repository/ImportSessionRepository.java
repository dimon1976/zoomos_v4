package com.java.repository;

import com.java.model.entity.ImportSession;
import com.java.model.entity.ImportTemplate;
import com.java.model.enums.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportSessionRepository extends JpaRepository<ImportSession, Long> {

    // Найти сессию по операции файла
    Optional<ImportSession> findByFileOperationId(Long fileOperationId);

    // Найти активные сессии (в процессе)
    @Query("SELECT s FROM ImportSession s WHERE s.status IN :statuses")
    List<ImportSession> findActiveSessionss(@Param("statuses") List<ImportStatus> statuses);

    // Найти сессии по шаблону
    Page<ImportSession> findByTemplate(ImportTemplate template, Pageable pageable);

    // Найти сессии по статусу
    List<ImportSession> findByStatus(ImportStatus status);

    // Найти сессии для отмены (долго выполняющиеся)
    @Query("SELECT s FROM ImportSession s WHERE s.status = :status AND s.startedAt < :timeout")
    List<ImportSession> findTimedOutSessions(@Param("status") ImportStatus status,
                                             @Param("timeout") ZonedDateTime timeout);

    // Статистика по шаблону
    @Query("SELECT " +
            "COUNT(s), " +
            "SUM(s.successRows), " +
            "SUM(s.errorRows), " +
            "AVG(s.totalRows) " +
            "FROM ImportSession s WHERE s.template = :template")
    Object[] getTemplateStatistics(@Param("template") ImportTemplate template);
}