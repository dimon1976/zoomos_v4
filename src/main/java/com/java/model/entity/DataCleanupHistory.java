package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * История выполнения операций очистки данных
 */
@Entity
@Table(name = "data_cleanup_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "cleanup_date", nullable = false)
    @Builder.Default
    private LocalDateTime cleanupDate = LocalDateTime.now();

    @Column(name = "cutoff_date", nullable = false)
    private LocalDateTime cutoffDate;

    @Column(name = "records_deleted", nullable = false)
    @Builder.Default
    private Long recordsDeleted = 0L;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // SUCCESS, FAILED, PARTIAL

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "excluded_client_ids", columnDefinition = "TEXT")
    private String excludedClientIds;

    @PrePersist
    protected void onCreate() {
        if (cleanupDate == null) {
            cleanupDate = LocalDateTime.now();
        }
    }
}
