package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_city_names")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosCityName {

    @Id
    @Column(name = "city_id")
    private String cityId;

    @Column(name = "city_name", nullable = false)
    private String cityName;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
