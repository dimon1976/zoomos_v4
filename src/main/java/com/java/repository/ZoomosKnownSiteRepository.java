package com.java.repository;

import com.java.model.entity.ZoomosKnownSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoomosKnownSiteRepository extends JpaRepository<ZoomosKnownSite, Long> {

    Optional<ZoomosKnownSite> findBySiteName(String siteName);

    List<ZoomosKnownSite> findAllByOrderBySiteNameAsc();

    boolean existsBySiteName(String siteName);

    List<ZoomosKnownSite> findAllByIsPriorityTrue();

    List<ZoomosKnownSite> findAllByIgnoreStockTrue();

    List<ZoomosKnownSite> findAllByMasterCityIdNotNull();
}
