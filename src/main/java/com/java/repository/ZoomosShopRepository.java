package com.java.repository;

import com.java.model.entity.ZoomosShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosShopRepository extends JpaRepository<ZoomosShop, Long> {

    Optional<ZoomosShop> findByShopName(String shopName);

    Optional<ZoomosShop> findByShopNameIgnoreCase(String shopName);

    Optional<ZoomosShop> findByClientId(Long clientId);

    List<ZoomosShop> findAllByClientIsNotNull();

    boolean existsByShopName(String shopName);
}
