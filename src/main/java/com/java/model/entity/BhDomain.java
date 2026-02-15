package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "bh_domains")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BhDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 255)
    private String domain;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "url_count")
    @Builder.Default
    private Long urlCount = 0L;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isActive == null) isActive = true;
        if (urlCount == null) urlCount = 0L;
    }
}
