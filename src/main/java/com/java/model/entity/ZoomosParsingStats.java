package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_parsing_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosParsingStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_run_id", nullable = false)
    private ZoomosCheckRun checkRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id_ref")
    private ZoomosCityId cityIdRef;

    // Идентификация выкачки
    @Column(name = "parsing_id")
    private Long parsingId;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(name = "city_name")
    private String cityName;

    @Column(name = "server_name")
    private String serverName;

    // Временные метки парсинга
    @Column(name = "start_time")
    private ZonedDateTime startTime;

    @Column(name = "finish_time")
    private ZonedDateTime finishTime;

    // Метрики
    @Column(name = "total_products")
    private Integer totalProducts;

    @Column(name = "in_stock")
    private Integer inStock;

    @Column(name = "category_count")
    private Integer categoryCount;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "completion_total")
    private String completionTotal;

    @Column(name = "completion_percent")
    private Integer completionPercent;

    @Column(name = "parsing_duration")
    private String parsingDuration;

    @Column(name = "parsing_duration_minutes")
    private Integer parsingDurationMinutes;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "updated_time")
    private ZonedDateTime updatedTime;

    @Column(name = "is_finished")
    @Builder.Default
    private Boolean isFinished = true;

    // Мета
    @Column(name = "parsing_date", nullable = false)
    private LocalDate parsingDate;

    @Column(name = "check_type", nullable = false)
    private String checkType;

    @Column(name = "checked_at", updatable = false)
    private ZonedDateTime checkedAt;

    @PrePersist
    public void prePersist() {
        checkedAt = ZonedDateTime.now();
    }
}
