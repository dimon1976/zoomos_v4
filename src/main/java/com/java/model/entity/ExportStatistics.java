package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Сущность для хранения предрассчитанной статистики экспорта
 */
@Entity
@Table(name = "export_statistics", indexes = {
        @Index(name = "idx_export_statistics_session", columnList = "export_session_id"),
        @Index(name = "idx_export_statistics_group", columnList = "group_field_name, group_field_value"),
        @Index(name = "idx_export_statistics_count_field", columnList = "count_field_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "export_session_id", nullable = false)
    private Long exportSessionId;

    @Column(name = "group_field_name", nullable = false)
    private String groupFieldName;

    @Column(name = "group_field_value", nullable = false)
    private String groupFieldValue;

    @Column(name = "count_field_name", nullable = false)
    private String countFieldName;

    @Column(name = "count_value", nullable = false)
    @Builder.Default
    private Long countValue = 0L;

    @Column(name = "filter_conditions", columnDefinition = "TEXT")
    private String filterConditions; // JSON для будущих расширений

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}