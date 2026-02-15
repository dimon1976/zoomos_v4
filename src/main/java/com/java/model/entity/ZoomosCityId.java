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

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

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
