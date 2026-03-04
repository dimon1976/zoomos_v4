package com.java.repository;

import com.java.model.entity.ZoomosCityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosCityIdRepository extends JpaRepository<ZoomosCityId, Long> {

    List<ZoomosCityId> findByShopIdOrderBySiteName(Long shopId);

    Optional<ZoomosCityId> findByShopIdAndSiteName(Long shopId, String siteName);

    List<ZoomosCityId> findAllBySiteName(String siteName);

    @Modifying
    @Transactional
    @Query("DELETE FROM ZoomosCityId c WHERE c.shop.id = :shopId")
    void deleteByShopId(Long shopId);
}
