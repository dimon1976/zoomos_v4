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

    /**
     * Получает общую статистику (без фильтра) для нескольких сессий
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "WHERE es.exportSession.id IN :sessionIds " +
           "AND es.filterFieldName IS NULL " +
           "ORDER BY es.exportSession.startedAt DESC, es.groupFieldValue, es.countFieldName")
    List<ExportStatistics> findBySessionIdsWithoutFilter(@Param("sessionIds") List<Long> sessionIds);

    /**
     * Получает отфильтрованную статистику для нескольких сессий
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "WHERE es.exportSession.id IN :sessionIds " +
           "AND es.filterFieldName = :filterFieldName " +
           "AND es.filterFieldValue = :filterFieldValue " +
           "ORDER BY es.exportSession.startedAt DESC, es.groupFieldValue, es.countFieldName")
    List<ExportStatistics> findBySessionIdsAndFilter(
            @Param("sessionIds") List<Long> sessionIds,
            @Param("filterFieldName") String filterFieldName,
            @Param("filterFieldValue") String filterFieldValue);

    /**
     * Получает уникальные значения фильтра для указанного поля в нескольких сессиях
     */
    @Query("SELECT DISTINCT es.filterFieldValue FROM ExportStatistics es " +
           "WHERE es.exportSession.id IN :sessionIds " +
           "AND es.filterFieldName = :filterFieldName " +
           "AND es.filterFieldValue IS NOT NULL " +
           "ORDER BY es.filterFieldValue")
    List<String> findDistinctFilterValues(
            @Param("sessionIds") List<Long> sessionIds,
            @Param("filterFieldName") String filterFieldName);

    /**
     * Получает уникальные названия полей фильтрации в нескольких сессиях
     */
    @Query("SELECT DISTINCT es.filterFieldName FROM ExportStatistics es " +
           "WHERE es.exportSession.id IN :sessionIds " +
           "AND es.filterFieldName IS NOT NULL " +
           "ORDER BY es.filterFieldName")
    List<String> findDistinctFilterFields(@Param("sessionIds") List<Long> sessionIds);

    /**
     * Получает историю значений метрики для конкретной группы по всем операциям шаблона
     * Используется для построения графиков трендов
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "JOIN FETCH es.exportSession sess " +
           "WHERE sess.template.id = :templateId " +
           "AND es.groupFieldValue = :groupValue " +
           "AND es.countFieldName = :metricName " +
           "AND ((:filterFieldName IS NULL AND es.filterFieldName IS NULL) " +
           "     OR (es.filterFieldName = :filterFieldName AND es.filterFieldValue = :filterFieldValue)) " +
           "ORDER BY sess.startedAt DESC")
    List<ExportStatistics> findHistoryForMetric(
            @Param("templateId") Long templateId,
            @Param("groupValue") String groupValue,
            @Param("metricName") String metricName,
            @Param("filterFieldName") String filterFieldName,
            @Param("filterFieldValue") String filterFieldValue);

    /**
     * Получает историю для всех групп по одной метрике (для построения сводных графиков)
     */
    @Query("SELECT es FROM ExportStatistics es " +
           "JOIN FETCH es.exportSession sess " +
           "WHERE sess.template.id = :templateId " +
           "AND es.countFieldName = :metricName " +
           "AND ((:filterFieldName IS NULL AND es.filterFieldName IS NULL) " +
           "     OR (es.filterFieldName = :filterFieldName AND es.filterFieldValue = :filterFieldValue)) " +
           "ORDER BY sess.startedAt DESC, es.groupFieldValue")
    List<ExportStatistics> findHistoryForMetricAllGroups(
            @Param("templateId") Long templateId,
            @Param("metricName") String metricName,
            @Param("filterFieldName") String filterFieldName,
            @Param("filterFieldValue") String filterFieldValue);

    /**
     * Получает список уникальных групп для шаблона (исключая ОБЩЕЕ КОЛИЧЕСТВО)
     */
    @Query("SELECT DISTINCT es.groupFieldValue FROM ExportStatistics es " +
           "WHERE es.exportSession.template.id = :templateId " +
           "AND es.groupFieldValue != 'ОБЩЕЕ КОЛИЧЕСТВО' " +
           "ORDER BY es.groupFieldValue")
    List<String> findDistinctGroupValuesByTemplateId(@Param("templateId") Long templateId);

    /**
     * Получает список уникальных метрик для шаблона
     */
    @Query("SELECT DISTINCT es.countFieldName FROM ExportStatistics es " +
           "WHERE es.exportSession.template.id = :templateId " +
           "AND es.countFieldName != 'DATE_MODIFICATIONS' " +
           "ORDER BY es.countFieldName")
    List<String> findDistinctMetricNamesByTemplateId(@Param("templateId") Long templateId);
}