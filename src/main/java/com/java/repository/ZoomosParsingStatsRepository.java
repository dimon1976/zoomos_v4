package com.java.repository;

import com.java.model.entity.ZoomosParsingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosParsingStatsRepository extends JpaRepository<ZoomosParsingStats, Long> {

    void deleteByCheckRunId(Long checkRunId);

    // Только не-baseline записи (для отображения результатов проверки)
    List<ZoomosParsingStats> findByCheckRunIdAndIsBaselineFalseOrderBySiteNameAscCityNameAsc(Long checkRunId);

    // Baseline-записи текущего run (для отображения в таблице деталей)
    List<ZoomosParsingStats> findByCheckRunIdAndIsBaselineTrueOrderByStartTimeDesc(Long checkRunId);

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
     * Только isBaseline=true — записи, явно загруженные как исторический baseline.
     */
    @Query("SELECT s FROM ZoomosParsingStats s " +
           "WHERE s.siteName = :siteName " +
           "AND (:cityName IS NULL OR s.cityName = :cityName) " +
           "AND (:addressId IS NULL OR s.addressId = :addressId) " +
           "AND s.startTime >= :from AND s.startTime < :to " +
           "AND s.isBaseline = true AND s.completionPercent >= 100 " +
           "ORDER BY s.startTime ASC")
    List<ZoomosParsingStats> findForBaseline(
            @Param("siteName") String siteName,
            @Param("cityName") String cityName,
            @Param("addressId") String addressId,
            @Param("from") ZonedDateTime from,
            @Param("to") ZonedDateTime to);

    /**
     * Последняя завершённая выкачка для данного сайта+города (по любому прошлому check_run).
     * Ищет по completion_percent >= 100 без ограничения is_finished —
     * overnight-парсинги сохраняются с is_finished=false но фактически завершены.
     * Используется для отображения "Последний раз на 100%" в NOT_FOUND issue.
     */
    @Query(value = "SELECT * FROM zoomos_parsing_stats " +
                   "WHERE site_name = :siteName " +
                   "AND (city_name LIKE CONCAT(:cityId, ' %') OR city_name = :cityId) " +
                   "AND completion_percent >= 100 " +
                   "ORDER BY start_time DESC LIMIT 1",
           nativeQuery = true)
    Optional<ZoomosParsingStats> findLatestFinishedBySiteAndCityId(
            @Param("siteName") String siteName,
            @Param("cityId") String cityId);

    /**
     * Последняя завершённая выкачка для конкретного адреса (address_id).
     * Используется для отображения "Последний раз на 100%" в NOT_FOUND address-issue.
     */
    @Query(value = "SELECT * FROM zoomos_parsing_stats " +
                   "WHERE site_name = :siteName " +
                   "AND address_id = :addressId " +
                   "AND completion_percent >= 100 " +
                   "ORDER BY start_time DESC LIMIT 1",
           nativeQuery = true)
    Optional<ZoomosParsingStats> findLatestFinishedBySiteAndAddressId(
            @Param("siteName") String siteName,
            @Param("addressId") String addressId);

    /**
     * Текущая in-progress выкачка для данного сайта+города.
     * Возвращает последнюю сохранённую запись (ORDER BY id DESC) независимо от start_time.
     * Используется для отображения "Сейчас идёт" в NOT_FOUND issue.
     */
    @Query(value = "SELECT * FROM zoomos_parsing_stats " +
                   "WHERE site_name = :siteName " +
                   "AND (city_name LIKE CONCAT(:cityId, ' %') OR city_name = :cityId) " +
                   "AND is_finished = false " +
                   "ORDER BY id DESC LIMIT 1",
           nativeQuery = true)
    Optional<ZoomosParsingStats> findLatestInProgressBySiteAndCityId(
            @Param("siteName") String siteName,
            @Param("cityId") String cityId);

    /**
     * Batch: последняя завершённая по каждой паре (site_name, address_id).
     * Заменяет N вызовов findLatestFinishedBySiteAndAddressId.
     */
    @Query(value = "SELECT DISTINCT ON (site_name, address_id) * FROM zoomos_parsing_stats " +
                   "WHERE site_name = ANY(:siteNames) AND address_id = ANY(:addressIds) " +
                   "AND completion_percent >= 100 " +
                   "ORDER BY site_name, address_id, start_time DESC",
           nativeQuery = true)
    List<ZoomosParsingStats> findLatestFinishedBySiteAndAddressIds(
            @Param("siteNames") String[] siteNames,
            @Param("addressIds") String[] addressIds);

    /**
     * Batch: последняя завершённая по каждой паре (site_name, city_name) для заданных сайтов.
     * Заменяет N вызовов findLatestFinishedBySiteAndCityId.
     */
    @Query(value = "SELECT DISTINCT ON (site_name, city_name) * FROM zoomos_parsing_stats " +
                   "WHERE site_name = ANY(:siteNames) AND completion_percent >= 100 " +
                   "ORDER BY site_name, city_name, start_time DESC",
           nativeQuery = true)
    List<ZoomosParsingStats> findLatestFinishedBySites(
            @Param("siteNames") String[] siteNames);

    /**
     * Batch: последняя in-progress выкачка для каждой пары (site_name, cityId).
     * cityId — первое слово city_name до пробела.
     * Заменяет N вызовов findLatestInProgressBySiteAndCityId в NOT_FOUND цикле.
     */
    @Query(value = "SELECT DISTINCT ON (site_name, SPLIT_PART(city_name, ' ', 1)) * " +
                   "FROM zoomos_parsing_stats " +
                   "WHERE site_name = ANY(:siteNames) AND is_finished = false " +
                   "ORDER BY site_name, SPLIT_PART(city_name, ' ', 1), id DESC",
           nativeQuery = true)
    List<ZoomosParsingStats> findLatestInProgressBySites(@Param("siteNames") String[] siteNames);
}
