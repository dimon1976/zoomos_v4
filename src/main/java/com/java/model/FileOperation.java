package com.java.model;

import com.java.model.entity.Client;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "file_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "record_count")
    private Integer recordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OperationStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "created_by")
    private String createdBy;

    // Добавленные поля
    @Column(name = "source_file_path")
    private String sourceFilePath;

    @Column(name = "result_file_path")
    private String resultFilePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "processed_records")
    private Integer processedRecords;

    @Column(name = "processing_progress")
    private Integer processingProgress;

    @Column(name = "field_mapping_id")
    private Long fieldMappingId;

    @Column(name = "strategy_id")
    private Long strategyId;

    @Column(name = "processing_params", columnDefinition = "TEXT")
    private String processingParams;

    @Column(name = "file_hash")
    private String fileHash;

    // Enum для типа операции
    public enum OperationType {
        IMPORT, EXPORT, PROCESS
    }

    // Enum для статуса операции
    public enum OperationStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    // Метод для установки завершения операции
    public void markAsCompleted(int recordCount) {
        this.status = OperationStatus.COMPLETED;
        this.recordCount = recordCount;
        this.completedAt = ZonedDateTime.now();

        // Устанавливаем прогресс в 100%
        this.processingProgress = 100;
    }

    // Метод для установки ошибки операции
    public void markAsFailed(String errorMessage) {
        this.status = OperationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = ZonedDateTime.now();
    }

    // Метод для изменения статуса на "в процессе"
    public void markAsProcessing() {
        this.status = OperationStatus.PROCESSING;
    }

    // Метод для получения строкового представления длительности операции
    public String getDuration() {
        if (startedAt == null) {
            return "Н/Д";
        }

        if (completedAt == null) {
            if (status == OperationStatus.PROCESSING || status == OperationStatus.PENDING) {
                return "В процессе";
            }
            return "Не завершено";
        }

        long durationSeconds = completedAt.toEpochSecond() - startedAt.toEpochSecond();

        // Форматирование длительности
        if (durationSeconds < 60) {
            return durationSeconds + " сек";
        } else if (durationSeconds < 3600) {
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            return String.format("%d мин %d сек", minutes, seconds);
        } else {
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            return String.format("%d ч %d мин", hours, minutes);
        }
    }
}