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
           "JOIN FETCH s.fileOperation fo " +
           "WHERE s.startedAt BETWEEN :startDate AND :endDate " +
           "AND fo.client.id = :clientId " +
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
           "JOIN s.fileOperation fo " +
           "WHERE fo.client.id = :clientId " +
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
           "JOIN s.fileOperation fo " +
           "WHERE fo.client.id = :clientId " +
           "AND s.startedAt >= :sinceDate " +
           "GROUP BY DATE(s.startedAt), es.groupFieldValue " +
           "ORDER BY exportDate DESC, dailyCount DESC")
    List<Object[]> findDailyTrendsByClient(@Param("clientId") Long clientId,
                                          @Param("sinceDate") ZonedDateTime sinceDate);

    // === Optimized Filtering Methods for Performance ===

    /**
     * Оптимизированная фильтрация статистики с использованием нативного SQL
     */
    @Query(value = """
        WITH filtered_statistics AS (
            SELECT es.*, s.started_at, s.id as session_id
            FROM export_statistics es
            JOIN export_sessions s ON es.export_session_id = s.id
            JOIN file_operations fo ON s.file_operation_id = fo.id
            WHERE fo.client_id = :clientId
            AND (:sessionIds IS NULL OR s.id = ANY(CAST(:sessionIds AS bigint[])))
            AND (:groupFieldValues IS NULL OR es.group_field_value = ANY(CAST(:groupFieldValues AS text[])))
            AND (:countFieldNames IS NULL OR es.count_field_name = ANY(CAST(:countFieldNames AS text[])))
        )
        SELECT * FROM filtered_statistics
        ORDER BY started_at DESC, group_field_value, count_field_name
        LIMIT :pageSize OFFSET :offset
        """, nativeQuery = true)
    List<ExportStatistics> findFilteredStatisticsOptimized(
        @Param("clientId") Long clientId,
        @Param("sessionIds") String sessionIds,  // JSON array as string
        @Param("groupFieldValues") String groupFieldValues,  // Array as string
        @Param("countFieldNames") String countFieldNames,    // Array as string
        @Param("pageSize") int pageSize,
        @Param("offset") int offset
    );

    /**
     * Подсчет отфильтрованных результатов для пагинации
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND (:sessionIds IS NULL OR s.id = ANY(CAST(:sessionIds AS bigint[])))
        AND (:groupFieldValues IS NULL OR es.group_field_value = ANY(CAST(:groupFieldValues AS text[])))
        AND (:countFieldNames IS NULL OR es.count_field_name = ANY(CAST(:countFieldNames AS text[])))
        """, nativeQuery = true)
    Long countFilteredStatistics(
        @Param("clientId") Long clientId,
        @Param("sessionIds") String sessionIds,
        @Param("groupFieldValues") String groupFieldValues,
        @Param("countFieldNames") String countFieldNames
    );

    /**
     * Получает агрегированную статистику для отфильтрованных данных
     */
    @Query(value = """
        SELECT 
            'total_operations' as metric_name,
            COUNT(DISTINCT s.id)::text as metric_value
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND (:sessionIds IS NULL OR s.id = ANY(CAST(:sessionIds AS bigint[])))
        AND (:groupFieldValues IS NULL OR es.group_field_value = ANY(CAST(:groupFieldValues AS text[])))
        
        UNION ALL
        
        SELECT 
            'total_groups' as metric_name,
            COUNT(DISTINCT es.group_field_value)::text as metric_value
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND (:sessionIds IS NULL OR s.id = ANY(CAST(:sessionIds AS bigint[])))
        AND (:groupFieldValues IS NULL OR es.group_field_value = ANY(CAST(:groupFieldValues AS text[])))
        
        UNION ALL
        
        SELECT 
            'total_records' as metric_name,
            COUNT(es.id)::text as metric_value
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND (:sessionIds IS NULL OR s.id = ANY(CAST(:sessionIds AS bigint[])))
        AND (:groupFieldValues IS NULL OR es.group_field_value = ANY(CAST(:groupFieldValues AS text[])))
        """, nativeQuery = true)
    List<Object[]> getFilteredAggregatedStats(
        @Param("clientId") Long clientId,
        @Param("sessionIds") String sessionIds,
        @Param("groupFieldValues") String groupFieldValues
    );

    /**
     * Получает метаданные полей для построения динамических фильтров
     */
    @Query(value = """
        SELECT 
            'group_field_values' as field_type,
            json_agg(DISTINCT es.group_field_value ORDER BY es.group_field_value) as values
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND s.started_at >= :sinceDate
        AND es.group_field_value IS NOT NULL
        
        UNION ALL
        
        SELECT 
            'count_field_names' as field_type,
            json_agg(DISTINCT es.count_field_name ORDER BY es.count_field_name) as values
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND s.started_at >= :sinceDate
        AND es.count_field_name IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> getFieldMetadataForClient(
        @Param("clientId") Long clientId,
        @Param("sinceDate") ZonedDateTime sinceDate
    );

    /**
     * Оптимизированный поиск с использованием индексов на field_metadata JSONB
     */
    @Query(value = """
        SELECT es.*, s.started_at
        FROM export_statistics es
        JOIN export_sessions s ON es.export_session_id = s.id
        JOIN file_operations fo ON s.file_operation_id = fo.id
        WHERE fo.client_id = :clientId
        AND (:jsonbConditions IS NULL OR es.field_metadata @> CAST(:jsonbConditions AS jsonb))
        ORDER BY s.started_at DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<ExportStatistics> findWithJsonbConditions(
        @Param("clientId") Long clientId,
        @Param("jsonbConditions") String jsonbConditions,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}