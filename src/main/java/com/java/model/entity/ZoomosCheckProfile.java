package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zoomos_check_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosCheckProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ZoomosShop shop;

    @Column(name = "label")
    private String label;

    /** Дни недели через запятую (1=пн, 7=вс). Пусто = каждый день. */
    @Column(name = "days_of_week")
    private String daysOfWeek;

    @Column(name = "time_from")
    private String timeFrom;

    @Column(name = "time_to")
    private String timeTo;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "drop_threshold")
    @Builder.Default
    private Integer dropThreshold = 10;

    @Column(name = "error_growth_threshold")
    @Builder.Default
    private Integer errorGrowthThreshold = 30;

    @Column(name = "baseline_days")
    @Builder.Default
    private Integer baselineDays = 7;

    @Column(name = "min_absolute_errors")
    @Builder.Default
    private Integer minAbsoluteErrors = 5;

    @Column(name = "trend_drop_threshold")
    @Builder.Default
    private Integer trendDropThreshold = 30;

    @Column(name = "trend_error_threshold")
    @Builder.Default
    private Integer trendErrorThreshold = 100;

    /** Порог зависания: сколько минут updated_time не должно меняться для статуса STALLED. */
    @Column(name = "stall_minutes")
    @Builder.Default
    private Integer stallMinutes = 60;

    @Column(name = "is_enabled")
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZoomosProfileSite> sites = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
