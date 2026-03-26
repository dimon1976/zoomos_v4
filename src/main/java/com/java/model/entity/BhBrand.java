package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "bh_brands")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BhBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 500)
    private String name;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BhBrandSynonym> synonyms;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
