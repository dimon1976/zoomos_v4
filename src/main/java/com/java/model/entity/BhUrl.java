package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "bh_urls", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "url"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BhUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private BhProduct product;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(length = 255)
    private String domain;

    @Column(name = "site_name", length = 500)
    private String siteName;

    @Column(length = 500)
    private String source;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
