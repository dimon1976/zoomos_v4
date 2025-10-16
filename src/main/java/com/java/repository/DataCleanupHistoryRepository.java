package com.java.repository;

import com.java.model.entity.DataCleanupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с историей очистки данных
 */
@Repository
public interface DataCleanupHistoryRepository extends JpaRepository<DataCleanupHistory, Long> {

    /**
     * Найти историю по типу данных
     */
    List<DataCleanupHistory> findByEntityTypeOrderByCleanupDateDesc(String entityType);

    /**
     * Найти историю за период
     */
    List<DataCleanupHistory> findByCleanupDateBetweenOrderByCleanupDateDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Найти последние N записей истории
     */
    List<DataCleanupHistory> findTop20ByOrderByCleanupDateDesc();

    /**
     * Найти успешные операции очистки
     */
    List<DataCleanupHistory> findByStatusOrderByCleanupDateDesc(String status);

    /**
     * Получить общую статистику удаленных записей по типу данных
     */
    @Query("SELECT SUM(h.recordsDeleted) FROM DataCleanupHistory h WHERE h.entityType = :entityType AND h.status = 'SUCCESS'")
    Long getTotalDeletedRecordsByEntityType(String entityType);
}
