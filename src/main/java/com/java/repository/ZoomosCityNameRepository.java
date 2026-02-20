package com.java.repository;

import com.java.model.entity.ZoomosCityName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoomosCityNameRepository extends JpaRepository<ZoomosCityName, String> {

    @Modifying
    @Query(value = """
            INSERT INTO zoomos_city_names (city_id, city_name, updated_at)
            VALUES (:cityId, :cityName, NOW())
            ON CONFLICT (city_id) DO UPDATE
                SET city_name = :cityName, updated_at = NOW()
            """, nativeQuery = true)
    void upsert(@Param("cityId") String cityId, @Param("cityName") String cityName);
}
