package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Сущность для хранения статистики экспорта
 */
@Entity
@Table(name = "export_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "exportSession")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExportStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_session_id", nullable = false)
    private ExportSession exportSession;

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
    private String filterConditions; // JSON условий фильтрации (может не использоваться)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}