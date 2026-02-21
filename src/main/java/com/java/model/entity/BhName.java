package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "bh_names", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "name"})
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BhName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private BhProduct product;

    @Column(nullable = false, length = 2000)
    private String name;

    @Column(length = 500)
    private String source;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
