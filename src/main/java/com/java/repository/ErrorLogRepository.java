package com.java.repository;

import com.java.model.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository для работы с логами ошибок.
 * Часть централизованной системы обработки ошибок.
 */
@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>, JpaSpecificationExecutor<ErrorLog> {

    /**
     * Находит все ошибки по типу
     * 
     * @param errorType тип ошибки
     * @param pageable пагинация
     * @return страница ошибок
     */
    Page<ErrorLog> findByErrorType(String errorType, Pageable pageable);

    /**
     * Находит все ошибки по уровню серьезности
     * 
     * @param severity уровень серьезности
     * @param pageable пагинация
     * @return страница ошибок
     */
    Page<ErrorLog> findBySeverity(ErrorLog.ErrorSeverity severity, Pageable pageable);

    /**
     * Находит все ошибки по статусу
     * 
     * @param status статус ошибки
     * @param pageable пагинация
     * @return страница ошибок
     */
    Page<ErrorLog> findByStatus(ErrorLog.ErrorStatus status, Pageable pageable);

    /**
     * Находит ошибки в указанном диапазоне времени
     * 
     * @param startTime начальное время
     * @param endTime конечное время
     * @param pageable пагинация
     * @return страница ошибок
     */
    Page<ErrorLog> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Находит ошибки по URI запроса
     * 
     * @param requestUri URI запроса
     * @param pageable пагинация
     * @return страница ошибок
     */
    Page<ErrorLog> findByRequestUriContainingIgnoreCase(String requestUri, Pageable pageable);

    /**
     * Находит ошибки, для которых еще не отправлено уведомление
     * 
     * @return список ошибок без уведомлений
     */
    List<ErrorLog> findByNotificationSentFalse();

    /**
     * Находит критичные ошибки за последний период
     * 
     * @param severity уровень серьезности
     * @param since время, с которого искать
     * @return список критичных ошибок
     */
    @Query("SELECT e FROM ErrorLog e WHERE e.severity = :severity AND e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<ErrorLog> findRecentCriticalErrors(@Param("severity") ErrorLog.ErrorSeverity severity, 
                                          @Param("since") LocalDateTime since);

    /**
     * Подсчитывает количество ошибок по типу за период
     * 
     * @param errorType тип ошибки
     * @param since время, с которого считать
     * @return количество ошибок
     */
    @Query("SELECT COUNT(e) FROM ErrorLog e WHERE e.errorType = :errorType AND e.createdAt >= :since")
    Long countByErrorTypeAndCreatedAtAfter(@Param("errorType") String errorType, 
                                         @Param("since") LocalDateTime since);

    /**
     * Находит самые частые ошибки за период
     * 
     * @param since время, с которого анализировать
     * @param limit количество записей
     * @return список типов ошибок с количеством
     */
    @Query(value = """
        SELECT e.error_type as errorType, COUNT(*) as errorCount 
        FROM error_logs e 
        WHERE e.created_at >= :since 
        GROUP BY e.error_type 
        ORDER BY COUNT(*) DESC 
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findMostFrequentErrors(@Param("since") LocalDateTime since, @Param("limit") int limit);

    /**
     * Удаляет старые логи ошибок
     * 
     * @param before время, до которого удалять логи
     * @return количество удаленных записей
     */
    @Query("DELETE FROM ErrorLog e WHERE e.createdAt < :before AND e.severity NOT IN (:preserveSeverities)")
    int deleteOldLogs(@Param("before") LocalDateTime before, 
                     @Param("preserveSeverities") List<ErrorLog.ErrorSeverity> preserveSeverities);

    /**
     * Находит последнюю ошибку определенного типа
     * 
     * @param errorType тип ошибки
     * @return последняя ошибка или пусто
     */
    Optional<ErrorLog> findFirstByErrorTypeOrderByCreatedAtDesc(String errorType);
}