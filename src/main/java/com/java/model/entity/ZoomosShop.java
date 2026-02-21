package com.java.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zoomos_shops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosShop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name", nullable = false, unique = true)
    private String shopName;

    @Column(name = "last_synced_at")
    private ZonedDateTime lastSyncedAt;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZoomosCityId> cityIds = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = ZonedDateTime.now();
    }
}
