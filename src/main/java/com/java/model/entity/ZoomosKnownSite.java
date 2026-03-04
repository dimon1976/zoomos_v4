package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_sites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosKnownSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false, unique = true)
    private String siteName;

    @Column(name = "check_type", nullable = false)
    @Builder.Default
    private String checkType = "ITEM";

    @Column(name = "description")
    private String description;

    @Column(name = "is_priority", nullable = false)
    @Builder.Default
    private boolean isPriority = false;

    @Column(name = "ignore_stock", nullable = false)
    @Builder.Default
    private boolean ignoreStock = false;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = ZonedDateTime.now();
    }
}
