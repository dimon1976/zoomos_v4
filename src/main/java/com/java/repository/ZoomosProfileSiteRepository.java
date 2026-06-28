package com.java.repository;

import com.java.model.entity.ZoomosProfileSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomosProfileSiteRepository extends JpaRepository<ZoomosProfileSite, Long> {

    List<ZoomosProfileSite> findByProfileIdOrderBySiteName(Long profileId);

    List<ZoomosProfileSite> findByProfileIdAndActiveTrue(Long profileId);

    void deleteByProfileId(Long profileId);
}
