package com.java.repository;

import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.service.dashboard.DashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileOperationRepository extends JpaRepository<FileOperation, Long>, JpaSpecificationExecutor<FileOperation> {

    // Найти операции клиента
    Page<FileOperation> findByClient(Client client, Pageable pageable);

    // Найти операции по типу
    List<FileOperation> findByOperationType(FileOperation.OperationType type);

    // Найти активные операции импорта
    @Query("SELECT fo FROM FileOperation fo " +
            "WHERE fo.operationType = 'IMPORT' " +
            "AND fo.status IN ('PENDING', 'PROCESSING')")
    List<FileOperation> findActiveImportOperations();

    // Найти последние операции импорта клиента
    @Query("SELECT fo FROM FileOperation fo " +
            "WHERE fo.client = :client " +
            "AND fo.operationType = 'IMPORT' " +
            "ORDER BY fo.startedAt DESC")
    List<FileOperation> findRecentImportOperations(@Param("client") Client client, Pageable pageable);

    // Подсчет по статусам
    Long countByStatus(FileOperation.OperationStatus status);
    Long countByStatusIn(List<FileOperation.OperationStatus> statuses);

    // Подсчет по типам операций
    Long countByOperationType(FileOperation.OperationType operationType);

    // Статистика по файлам
    @Query("SELECT COUNT(fo) FROM FileOperation fo WHERE fo.fileSize IS NOT NULL")
    Long countNonNullFileSize();

    @Query("SELECT COALESCE(SUM(fo.fileSize), 0) FROM FileOperation fo")
    Long sumFileSize();

    @Query("SELECT COALESCE(SUM(fo.recordCount), 0) FROM FileOperation fo WHERE fo.recordCount IS NOT NULL")
    Long sumRecordCount();

    // Статистика по датам
    Long countByStartedAtGreaterThanEqual(ZonedDateTime startDate);

    // Производительность
    /**
     * Среднее время обработки (в минутах).
     * Нативный SQL, т.к. EXTRACT(EPOCH ...) — синтаксис PostgreSQL, которого нет в JPQL.
     */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (fo.completed_at - fo.started_at)) / 60.0)
            FROM file_operations fo
            WHERE fo.completed_at IS NOT NULL AND fo.started_at IS NOT NULL
            """, nativeQuery = true)
    Double getAverageProcessingTimeMinutes();

    /**
     * Название клиента с наибольшим количеством операций.
     * Нативный SQL с LIMIT 1 (в JPQL LIMIT не поддерживается).
     */
    @Query(value = """
            SELECT c.name
            FROM file_operations fo
            JOIN clients c ON c.id = fo.client_id
            GROUP BY c.id, c.name
            ORDER BY COUNT(fo.id) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findTopClientByOperationCount();

    /**
     * Самый часто используемый тип файла среди операций.
     */
    @Query(value = """
            SELECT fo.file_type
            FROM file_operations fo
            GROUP BY fo.file_type
            ORDER BY COUNT(fo.id) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findMostUsedFileType();



    // Список уникальных типов файлов
    @Query("SELECT DISTINCT fo.fileType FROM FileOperation fo WHERE fo.fileType IS NOT NULL ORDER BY fo.fileType")
    List<String> findDistinctFileTypes();

    // Статистика по клиентам
    @Query(value = """
            SELECT
                c.id                                    AS clientId,
                c.name                                  AS clientName,
                COUNT(fo.id)                            AS totalOps,
                SUM(CASE WHEN fo.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedOps,
                SUM(CASE WHEN fo.status = 'FAILED'    THEN 1 ELSE 0 END) AS failedOps,
                CASE WHEN COUNT(fo.id) > 0
                     THEN (SUM(CASE WHEN fo.status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0)
                          / COUNT(fo.id)
                     ELSE 0.0
                END                                     AS successRate
            FROM clients c
            LEFT JOIN file_operations fo ON fo.client_id = c.id
            GROUP BY c.id, c.name
            ORDER BY COUNT(fo.id) DESC
            """, nativeQuery = true)
    List<DashboardService.ClientStatsDto> getClientStatistics();
}