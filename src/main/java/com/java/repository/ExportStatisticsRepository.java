package com.java.repository;

import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportStatisticsRepository extends JpaRepository<ExportStatistics, Long> {

    /**
     * Получает всю статистику для сессии экспорта
     */
    List<ExportStatistics> findByExportSession(ExportSession exportSession);

    /**
     * Получает статистику для сессии экспорта по ID
     */
    List<ExportStatistics> findByExportSessionId(Long exportSessionId);

    /**
     * Получает статистику для нескольких сессий экспорта
     */
    @Query("SELECT es FROM ExportStatistics es WHERE es.exportSession.id IN :sessionIds ORDER BY es.exportSession.startedAt DESC, es.groupFieldValue, es.countFieldName")
    List<ExportStatistics> findByExportSessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * Получает статистику для конкретной группы в сессии
     */
    List<ExportStatistics> findByExportSessionIdAndGroupFieldValue(Long exportSessionId, String groupFieldValue);

    /**
     * Получает уникальные значения группировки для сессии
     */
    @Query("SELECT DISTINCT es.groupFieldValue FROM ExportStatistics es WHERE es.exportSession.id = :sessionId ORDER BY es.groupFieldValue")
    List<String> findDistinctGroupFieldValuesBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Получает уникальные названия полей подсчета для сессии
     */
    @Query("SELECT DISTINCT es.countFieldName FROM ExportStatistics es WHERE es.exportSession.id = :sessionId ORDER BY es.countFieldName")
    List<String> findDistinctCountFieldNamesBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Удаляет всю статистику для сессии экспорта
     */
    void deleteByExportSession(ExportSession exportSession);

    /**
     * Удаляет статистику для сессии экспорта по ID
     */
    void deleteByExportSessionId(Long exportSessionId);

    /**
     * Проверяет существование статистики для сессии
     */
    boolean existsByExportSessionId(Long exportSessionId);

    /**
     * Получает количество записей статистики для сессии
     */
    long countByExportSessionId(Long exportSessionId);
}