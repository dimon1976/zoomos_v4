package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "export_statistics_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportStatisticsCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_session_id", nullable = false)
    private ExportSession exportSession;

    @Column(name = "group_key")
    private String groupKey;

    @Column(name = "group_value")
    private String groupValue;

    @Column(name = "metrics", columnDefinition = "JSONB", nullable = false)
    private String metrics;

    @Column(name = "filter_applied")
    @Builder.Default
    private Boolean filterApplied = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}