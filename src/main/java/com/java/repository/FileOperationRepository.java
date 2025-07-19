package com.java.repository;

import com.java.model.Client;
import com.java.model.FileOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileOperationRepository extends JpaRepository<FileOperation, Long> {

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
}