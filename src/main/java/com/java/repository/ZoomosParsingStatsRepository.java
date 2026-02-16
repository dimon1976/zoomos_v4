package com.java.repository;

import com.java.model.entity.ZoomosParsingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomosParsingStatsRepository extends JpaRepository<ZoomosParsingStats, Long> {

    List<ZoomosParsingStats> findByCheckRunIdOrderBySiteNameAscCityNameAsc(Long checkRunId);

    @Query("SELECT s FROM ZoomosParsingStats s WHERE s.checkRun.id = :checkRunId " +
           "AND s.siteName = :siteName ORDER BY s.finishTime DESC")
    List<ZoomosParsingStats> findByCheckRunAndSite(Long checkRunId, String siteName);

    @Query("SELECT s FROM ZoomosParsingStats s WHERE s.checkRun.id = :checkRunId " +
           "AND s.siteName = :siteName AND s.cityName = :cityName " +
           "ORDER BY s.finishTime DESC")
    List<ZoomosParsingStats> findByRunAndSiteAndCity(Long checkRunId, String siteName, String cityName);
}
