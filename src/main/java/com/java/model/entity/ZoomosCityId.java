package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_city_ids",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "site_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosCityId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ZoomosShop shop;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(name = "city_ids", columnDefinition = "TEXT")
    private String cityIds;

    @Column(name = "address_ids", columnDefinition = "TEXT")
    private String addressIds;   // "14342,15234" или null

    @Column(name = "master_city_id", length = 50)
    private String masterCityId;

    @Column(name = "check_type")
    @Builder.Default
    private String checkType = "API";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "parser_include", columnDefinition = "TEXT")
    private String parserInclude;

    @Column(name = "parser_include_mode", length = 3)
    @Builder.Default
    private String parserIncludeMode = "OR";

    @Column(name = "parser_exclude", columnDefinition = "TEXT")
    private String parserExclude;

    @Column(name = "has_config_issue", nullable = false)
    @Builder.Default
    private boolean hasConfigIssue = false;

    @Column(name = "config_issue_note", columnDefinition = "TEXT")
    private String configIssueNote;

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
