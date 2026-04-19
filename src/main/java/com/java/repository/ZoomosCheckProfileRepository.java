package com.java.repository;

import com.java.model.entity.ZoomosCheckProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomosCheckProfileRepository extends JpaRepository<ZoomosCheckProfile, Long> {

    List<ZoomosCheckProfile> findByShopIdOrderByLabel(Long shopId);

    List<ZoomosCheckProfile> findByEnabledTrue();

    @Query("SELECT p FROM ZoomosCheckProfile p LEFT JOIN FETCH p.sites WHERE p.id = :id")
    java.util.Optional<ZoomosCheckProfile> findByIdWithSites(Long id);
}
