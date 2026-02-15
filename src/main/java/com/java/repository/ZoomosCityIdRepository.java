package com.java.repository;

import com.java.model.entity.ZoomosCityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomosCityIdRepository extends JpaRepository<ZoomosCityId, Long> {

    List<ZoomosCityId> findByShopIdOrderBySiteName(Long shopId);
}
