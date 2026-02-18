package com.java.repository;

import com.java.model.entity.ZoomosParsingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface ZoomosParsingStatsRepository extends JpaRepository<ZoomosParsingStats, Long> {

    // Только не-baseline записи (для отображения результатов проверки)
    List<ZoomosParsingStats> findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(Long checkRunId);

    @Query("SELECT s FROM ZoomosParsingStats s WHERE s.checkRun.id = :checkRunId " +
           "AND s.siteName = :siteName ORDER BY s.finishTime DESC")
    List<ZoomosParsingStats> findByCheckRunAndSite(Long checkRunId, String siteName);

    @Query("SELECT s FROM ZoomosParsingStats s WHERE s.checkRun.id = :checkRunId " +
           "AND s.siteName = :siteName AND s.cityName = :cityName " +
           "ORDER BY s.finishTime DESC")
    List<ZoomosParsingStats> findByRunAndSiteAndCity(Long checkRunId, String siteName, String cityName);

    /**
     * Исторические данные для baseline-анализа (по всем check_run в указанном date range).
     * cityName = null → все города для данного сайта.
     * Включает как isBaseline=true (специально загруженные), так и isBaseline=false (из прошлых проверок).
     */
    @Query("SELECT s FROM ZoomosParsingStats s " +
           "WHERE s.siteName = :siteName " +
           "AND (:cityName IS NULL OR s.cityName = :cityName) " +
           "AND s.startTime >= :from AND s.startTime < :to " +
           "AND s.isFinished = true AND s.completionPercent >= 100 " +
           "ORDER BY s.startTime ASC")
    List<ZoomosParsingStats> findForBaseline(
            @Param("siteName") String siteName,
            @Param("cityName") String cityName,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);
}
