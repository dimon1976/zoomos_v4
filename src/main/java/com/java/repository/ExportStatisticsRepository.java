package com.java.repository;

import com.java.model.entity.ExportStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий для работы с сохраненной статистикой экспорта
 */
@Repository
public interface ExportStatisticsRepository extends JpaRepository<ExportStatistics, Long> {

    /**
     * Получить статистику для списка сессий
     */
    @Query(value = """
        SELECT * FROM export_statistics 
        WHERE export_session_id IN :sessionIds 
        ORDER BY export_session_id, group_field_value, count_field_name
        """, nativeQuery = true)
    List<ExportStatistics> findBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * Получить статистику для одной сессии
     */
    @Query(value = """
        SELECT * FROM export_statistics 
        WHERE export_session_id = :sessionId 
        ORDER BY group_field_value, count_field_name
        """, nativeQuery = true)
    List<ExportStatistics> findBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Удалить всю статистику для сессии
     */
    void deleteByExportSessionId(Long sessionId);

    /**
     * Проверить наличие статистики для сессии
     */
    boolean existsByExportSessionId(Long sessionId);
}