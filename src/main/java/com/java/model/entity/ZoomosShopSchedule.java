package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_shop_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosShopSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "cron_expression", nullable = false)
    @Builder.Default
    private String cronExpression = "0 0 8 * * *";

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = false;

    @Column(name = "time_from")
    private String timeFrom;

    @Column(name = "time_to")
    private String timeTo;

    @Column(name = "drop_threshold", nullable = false)
    @Builder.Default
    private int dropThreshold = 10;

    @Column(name = "error_growth_threshold", nullable = false)
    @Builder.Default
    private int errorGrowthThreshold = 30;

    @Column(name = "baseline_days", nullable = false)
    @Builder.Default
    private int baselineDays = 7;

    @Column(name = "min_absolute_errors", nullable = false)
    @Builder.Default
    private int minAbsoluteErrors = 5;

    /** Порог замедления выкачки и падения доли «В наличии» для TREND_WARNING (%) */
    @Column(name = "trend_drop_threshold", nullable = false)
    @Builder.Default
    private int trendDropThreshold = 30;

    /** Порог роста доли ошибок от товаров для TREND_WARNING (%) */
    @Column(name = "trend_error_threshold", nullable = false)
    @Builder.Default
    private int trendErrorThreshold = 100;

    /** Смещение дня "от" относительно сегодня: -1 = вчера */
    @Column(name = "date_offset_from", nullable = false)
    @Builder.Default
    private int dateOffsetFrom = -1;

    /** Смещение дня "до" относительно сегодня: 0 = сегодня */
    @Column(name = "date_offset_to", nullable = false)
    @Builder.Default
    private int dateOffsetTo = 0;

    @Column(name = "last_run_at")
    private ZonedDateTime lastRunAt;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

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
