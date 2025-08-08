package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "export_template_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTemplateStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false, unique = true)
    private ExportTemplate template;

    @Column(name = "metric_fields", columnDefinition = "TEXT")
    @Builder.Default
    private String metricFields = "[]";

    @Column(name = "group_by_field")
    private String groupByField;

    @Column(name = "filter_field")
    private String filterField;

    @Column(name = "filter_values", columnDefinition = "TEXT")
    private String filterValues;

    @Column(name = "deviation_threshold_percent")
    @Builder.Default
    private Integer deviationThresholdPercent = 10;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}