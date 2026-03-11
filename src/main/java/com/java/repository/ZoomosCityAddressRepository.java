package com.java.repository;

import com.java.model.entity.ZoomosCityAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface ZoomosCityAddressRepository extends JpaRepository<ZoomosCityAddress, Long> {

    List<ZoomosCityAddress> findByCityIdOrderByAddressId(String cityId);

    List<ZoomosCityAddress> findByCityIdInOrderByCityIdAscAddressIdAsc(List<String> cityIds);

    @Query("SELECT a.cityId, COUNT(a) FROM ZoomosCityAddress a GROUP BY a.cityId")
    List<Object[]> countByCityId();

    /**
     * REQUIRES_NEW: изолирует от внешней транзакции runCheck — как парсер-паттерны.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO zoomos_city_addresses (city_id, address_id, address_name, updated_at)
            VALUES (:cityId, :addressId, :addressName, NOW())
            ON CONFLICT (city_id, address_id) DO UPDATE
                SET address_name = EXCLUDED.address_name, updated_at = NOW()
            """, nativeQuery = true)
    void upsert(@Param("cityId") String cityId,
                @Param("addressId") String addressId,
                @Param("addressName") String addressName);
}
