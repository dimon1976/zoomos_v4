package com.java.repository;

import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
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
     * Получает статистику для нескольких сессий экспорта с оптимизированным запросом
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "JOIN FETCH es.exportSession s " +
           "WHERE s.id IN :sessionIds " +
           "ORDER BY s.startedAt DESC, es.groupFieldValue, es.countFieldName")
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

    // === Enhanced Performance Methods ===

    /**
     * Получает статистику для нескольких сессий с пагинацией
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "JOIN FETCH es.exportSession s " +
           "WHERE s.id IN :sessionIds " +
           "ORDER BY s.startedAt DESC, es.groupFieldValue, es.countFieldName")
    Page<ExportStatistics> findByExportSessionIdsPaged(@Param("sessionIds") List<Long> sessionIds, Pageable pageable);

    /**
     * Получает агрегированную статистику по группам для быстрого dashboard
     */
    @Query("SELECT es.groupFieldValue, " +
           "SUM(es.countValue) as totalCount, " +
           "COUNT(es.id) as recordCount, " +
           "SUM(es.dateModificationsCount) as totalDateModifications " +
           "FROM ExportStatistics es " +
           "WHERE es.exportSession.id IN :sessionIds " +
           "GROUP BY es.groupFieldValue " +
           "ORDER BY totalCount DESC")
    List<Object[]> findAggregatedStatsBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * Получает статистику за определенный период времени
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "JOIN FETCH es.exportSession s " +
           "WHERE s.startedAt BETWEEN :startDate AND :endDate " +
           "AND s.client.id = :clientId " +
           "ORDER BY s.startedAt DESC")
    List<ExportStatistics> findByDateRangeAndClient(@Param("startDate") ZonedDateTime startDate,
                                                   @Param("endDate") ZonedDateTime endDate,
                                                   @Param("clientId") Long clientId);

    /**
     * Получает топ N групп по количеству записей для dashboard
     */
    @Query("SELECT es.groupFieldValue, SUM(es.countValue) as totalCount " +
           "FROM ExportStatistics es " +
           "JOIN es.exportSession s " +
           "WHERE s.client.id = :clientId " +
           "AND s.startedAt >= :sinceDate " +
           "GROUP BY es.groupFieldValue " +
           "ORDER BY totalCount DESC")
    Page<Object[]> findTopGroupsByClient(@Param("clientId") Long clientId,
                                        @Param("sinceDate") ZonedDateTime sinceDate,
                                        Pageable pageable);

    /**
     * Получает статистику трендов (для будущих улучшений)
     */
    @Query("SELECT DATE(s.startedAt) as exportDate, " +
           "es.groupFieldValue, " +
           "SUM(es.countValue) as dailyCount " +
           "FROM ExportStatistics es " +
           "JOIN es.exportSession s " +
           "WHERE s.client.id = :clientId " +
           "AND s.startedAt >= :sinceDate " +
           "GROUP BY DATE(s.startedAt), es.groupFieldValue " +
           "ORDER BY exportDate DESC, dailyCount DESC")
    List<Object[]> findDailyTrendsByClient(@Param("clientId") Long clientId,
                                          @Param("sinceDate") ZonedDateTime sinceDate);
}