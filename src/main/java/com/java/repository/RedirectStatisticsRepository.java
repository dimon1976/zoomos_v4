package com.java.repository;

import com.java.model.entity.RedirectStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface RedirectStatisticsRepository extends JpaRepository<RedirectStatistics, Long> {

    /**
     * Получить статистику успешности по стратегиям за период
     */
    @Query(value = """
        SELECT strategy_name, 
               COUNT(*) as total_requests,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_requests,
               ROUND(100.0 * SUM(CASE WHEN success = true THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate,
               AVG(processing_time_ms) as avg_processing_time,
               COUNT(DISTINCT domain) as unique_domains
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate
        GROUP BY strategy_name 
        ORDER BY success_rate DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStrategySuccessRates(@Param("fromDate") ZonedDateTime fromDate, 
                                                       @Param("toDate") ZonedDateTime toDate);

    /**
     * Получить топ самых проблемных доменов
     */
    @Query(value = """
        SELECT domain,
               COUNT(*) as total_attempts,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_attempts,
               SUM(CASE WHEN is_blocked = true THEN 1 ELSE 0 END) as blocked_attempts,
               ROUND(100.0 * SUM(CASE WHEN is_blocked = true THEN 1 ELSE 0 END) / COUNT(*), 2) as block_rate
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate AND domain IS NOT NULL
        GROUP BY domain
        HAVING COUNT(*) >= :minAttempts
        ORDER BY block_rate DESC, total_attempts DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Map<String, Object>> getTopBlockedDomains(@Param("fromDate") ZonedDateTime fromDate,
                                                    @Param("toDate") ZonedDateTime toDate,
                                                    @Param("minAttempts") Integer minAttempts,
                                                    @Param("limit") Integer limit);

    /**
     * Получить распределение статусов за период
     */
    @Query(value = """
        SELECT status, 
               COUNT(*) as count,
               ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER(), 2) as percentage
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate
        GROUP BY status 
        ORDER BY count DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getStatusDistribution(@Param("fromDate") ZonedDateTime fromDate,
                                                     @Param("toDate") ZonedDateTime toDate);

    /**
     * Получить динамику успешности по часам
     */
    @Query(value = """
        SELECT DATE_TRUNC('hour', created_at) as hour,
               COUNT(*) as total_requests,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_requests,
               ROUND(100.0 * SUM(CASE WHEN success = true THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate
        GROUP BY DATE_TRUNC('hour', created_at)
        ORDER BY hour
        """, nativeQuery = true)
    List<Map<String, Object>> getHourlySuccessRate(@Param("fromDate") ZonedDateTime fromDate,
                                                    @Param("toDate") ZonedDateTime toDate);

    /**
     * Получить среднее время обработки по стратегиям
     */
    @Query(value = """
        SELECT strategy_name,
               AVG(processing_time_ms) as avg_time_ms,
               MIN(processing_time_ms) as min_time_ms,
               MAX(processing_time_ms) as max_time_ms,
               COUNT(*) as requests_count
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate AND success = true
        GROUP BY strategy_name
        ORDER BY avg_time_ms
        """, nativeQuery = true)
    List<Map<String, Object>> getProcessingTimeByStrategy(@Param("fromDate") ZonedDateTime fromDate,
                                                           @Param("toDate") ZonedDateTime toDate);

    /**
     * Найти самые успешные стратегии для конкретного домена
     */
    @Query(value = """
        SELECT strategy_name,
               COUNT(*) as total_attempts,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_attempts,
               ROUND(100.0 * SUM(CASE WHEN success = true THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate
        FROM redirect_statistics 
        WHERE domain = :domain AND created_at >= :fromDate
        GROUP BY strategy_name
        HAVING COUNT(*) >= 3
        ORDER BY success_rate DESC, successful_attempts DESC
        """, nativeQuery = true)
    List<Map<String, Object>> getBestStrategiesForDomain(@Param("domain") String domain,
                                                          @Param("fromDate") ZonedDateTime fromDate);

    /**
     * Получить общую статистику за период
     */
    @Query(value = """
        SELECT COUNT(*) as total_requests,
               COUNT(DISTINCT domain) as unique_domains,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_requests,
               SUM(CASE WHEN is_blocked = true THEN 1 ELSE 0 END) as blocked_requests,
               ROUND(100.0 * SUM(CASE WHEN success = true THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate,
               ROUND(100.0 * SUM(CASE WHEN is_blocked = true THEN 1 ELSE 0 END) / COUNT(*), 2) as block_rate,
               AVG(processing_time_ms) as avg_processing_time,
               AVG(redirect_count) as avg_redirect_count
        FROM redirect_statistics 
        WHERE created_at >= :fromDate AND created_at <= :toDate
        """, nativeQuery = true)
    Map<String, Object> getOverallStatistics(@Param("fromDate") ZonedDateTime fromDate,
                                              @Param("toDate") ZonedDateTime toDate);

    /**
     * Получить последние неудачные запросы для анализа
     */
    @Query("SELECT rs FROM RedirectStatistics rs WHERE rs.success = false AND rs.createdAt >= :fromDate ORDER BY rs.createdAt DESC")
    List<RedirectStatistics> getRecentFailures(@Param("fromDate") ZonedDateTime fromDate);

    /**
     * Найти статистику по URL или домену
     */
    @Query("SELECT rs FROM RedirectStatistics rs WHERE (rs.originalUrl LIKE %:urlOrDomain% OR rs.domain = :urlOrDomain) ORDER BY rs.createdAt DESC")
    List<RedirectStatistics> findByUrlOrDomain(@Param("urlOrDomain") String urlOrDomain);

    /**
     * Подсчитать количество успешных запросов по стратегиям за сегодня
     */
    @Query(value = """
        SELECT strategy_name,
               SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as successful_today
        FROM redirect_statistics 
        WHERE DATE(created_at) = CURRENT_DATE
        GROUP BY strategy_name
        """, nativeQuery = true)
    List<Map<String, Object>> getSuccessfulRequestsToday();

    /**
     * Очистка старых записей (для maintenance)
     */
    void deleteByCreatedAtBefore(ZonedDateTime beforeDate);
}