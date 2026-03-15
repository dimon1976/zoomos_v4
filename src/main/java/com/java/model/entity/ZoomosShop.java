package com.java.model.entity;

import com.java.model.Client;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

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

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column(name = "is_priority", nullable = false)
    @Builder.Default
    private boolean isPriority = false;

    @Column(name = "last_synced_at")
    private ZonedDateTime lastSyncedAt;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    @ToString.Exclude
    private Client client;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ZoomosCityId> cityIds = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = ZonedDateTime.now();
    }
}
