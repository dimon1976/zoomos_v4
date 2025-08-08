package com.java.repository;

import com.java.model.entity.ExportStatisticsCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportStatisticsCacheRepository extends JpaRepository<ExportStatisticsCache, Long> {

    List<ExportStatisticsCache> findByExportSessionId(Long sessionId);

    @Query("SELECT esc FROM ExportStatisticsCache esc " +
            "WHERE esc.exportSession.id IN :sessionIds " +
            "ORDER BY esc.groupValue, esc.exportSession.startedAt DESC")
    List<ExportStatisticsCache> findByExportSessionIds(@Param("sessionIds") List<Long> sessionIds);

    void deleteByExportSessionId(Long sessionId);

    @Query("SELECT DISTINCT esc.groupValue FROM ExportStatisticsCache esc " +
            "WHERE esc.exportSession.id IN :sessionIds " +
            "ORDER BY esc.groupValue")
    List<String> findDistinctGroupValues(@Param("sessionIds") List<Long> sessionIds);
}