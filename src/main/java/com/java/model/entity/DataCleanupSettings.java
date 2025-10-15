package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Настройки очистки данных для различных типов сущностей
 */
@Entity
@Table(name = "data_cleanup_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, unique = true, length = 50)
    private String entityType;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    @Column(name = "auto_cleanup_enabled")
    @Builder.Default
    private Boolean autoCleanupEnabled = false;

    @Column(name = "cleanup_batch_size")
    @Builder.Default
    private Integer cleanupBatchSize = 10000;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
