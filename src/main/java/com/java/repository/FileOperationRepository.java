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
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (fo.completedAt - fo.startedAt)) / 60.0) " +
            "FROM FileOperation fo WHERE fo.completedAt IS NOT NULL AND fo.startedAt IS NOT NULL")
    Double getAverageProcessingTimeMinutes();

    // Топ клиент по количеству операций
    @Query("SELECT c.name FROM FileOperation fo JOIN fo.client c " +
            "GROUP BY c.id, c.name ORDER BY COUNT(fo) DESC LIMIT 1")
    Optional<String> findTopClientByOperationCount();

    // Самый используемый тип файла
    @Query("SELECT fo.fileType FROM FileOperation fo " +
            "GROUP BY fo.fileType ORDER BY COUNT(fo) DESC LIMIT 1")
    Optional<String> findMostUsedFileType();

    // Список уникальных типов файлов
    @Query("SELECT DISTINCT fo.fileType FROM FileOperation fo WHERE fo.fileType IS NOT NULL ORDER BY fo.fileType")
    List<String> findDistinctFileTypes();

    // Статистика по клиентам
    @Query("SELECT new com.java.service.dashboard.DashboardService$ClientStatsDto(" +
            "c.id, c.name, COUNT(fo), " +
            "SUM(CASE WHEN fo.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN fo.status = 'FAILED' THEN 1 ELSE 0 END), " +
            "CASE WHEN COUNT(fo) > 0 THEN " +
            "  CAST(SUM(CASE WHEN fo.status = 'COMPLETED' THEN 1 ELSE 0 END) AS double) / COUNT(fo) * 100 " +
            "ELSE 0.0 END" +
            ") " +
            "FROM Client c LEFT JOIN c.fileOperations fo " +
            "GROUP BY c.id, c.name " +
            "ORDER BY COUNT(fo) DESC")
    List<DashboardService.ClientStatsDto> getClientStatistics();
}