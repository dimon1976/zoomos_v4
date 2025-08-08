package com.java.repository;

import com.java.model.entity.ExportTemplateStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExportStatisticsRepository extends JpaRepository<ExportTemplateStatistics, Long> {

    Optional<ExportTemplateStatistics> findByTemplateId(Long templateId);

    @Query("SELECT ets FROM ExportTemplateStatistics ets " +
            "JOIN FETCH ets.template " +
            "WHERE ets.template.id = :templateId")
    Optional<ExportTemplateStatistics> findByTemplateIdWithTemplate(@Param("templateId") Long templateId);

    boolean existsByTemplateId(Long templateId);
}