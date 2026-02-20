package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_city_addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosCityAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private String cityId;

    @Column(name = "address_id", nullable = false)
    private String addressId;

    @Column(name = "address_name")
    private String addressName;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
