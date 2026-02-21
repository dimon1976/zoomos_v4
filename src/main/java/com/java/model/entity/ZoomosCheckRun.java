package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_check_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosCheckRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id", nullable = false)
    private ZoomosShop shop;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "time_from")
    private String timeFrom;   // "HH:mm" или null → 00:00

    @Column(name = "time_to")
    private String timeTo;     // "HH:mm" или null → 23:59

    @Column(name = "total_sites")
    @Builder.Default
    private Integer totalSites = 0;

    @Column(name = "ok_count")
    @Builder.Default
    private Integer okCount = 0;

    @Column(name = "warning_count")
    @Builder.Default
    private Integer warningCount = 0;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "not_found_count")
    @Builder.Default
    private Integer notFoundCount = 0;

    @Column(name = "drop_threshold")
    @Builder.Default
    private Integer dropThreshold = 10;

    @Column(name = "error_growth_threshold")
    @Builder.Default
    private Integer errorGrowthThreshold = 30;

    @Column(name = "baseline_days")
    @Builder.Default
    private Integer baselineDays = 7;

    @Column(name = "status")
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @PrePersist
    public void prePersist() {
        startedAt = ZonedDateTime.now();
    }
}
