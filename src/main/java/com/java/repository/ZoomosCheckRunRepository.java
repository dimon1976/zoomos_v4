package com.java.repository;

import com.java.model.entity.CheckRunStatus;
import com.java.model.entity.ZoomosCheckRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosCheckRunRepository extends JpaRepository<ZoomosCheckRun, Long> {

    List<ZoomosCheckRun> findByShopIdOrderByStartedAtDesc(Long shopId);

    Optional<ZoomosCheckRun> findFirstByShopIdOrderByStartedAtDesc(Long shopId);

    /**
     * Последние COMPLETED run-ы за сегодня (после startOfDay), отсортированные DESC по времени.
     * Используется для priority-alerts: берём самый свежий run для каждого магазина.
     */
    @Query("SELECT r FROM ZoomosCheckRun r JOIN FETCH r.shop " +
           "WHERE r.status = com.java.model.entity.CheckRunStatus.COMPLETED " +
           "AND r.startedAt >= :startOfDay ORDER BY r.startedAt DESC")
    List<ZoomosCheckRun> findCompletedToday(@Param("startOfDay") ZonedDateTime startOfDay);

    @Query("SELECT r FROM ZoomosCheckRun r JOIN FETCH r.shop WHERE r.id = :id")
    Optional<ZoomosCheckRun> findByIdWithShop(@Param("id") Long id);

    @Query("SELECT r FROM ZoomosCheckRun r JOIN FETCH r.shop ORDER BY r.startedAt DESC")
    List<ZoomosCheckRun> findAllWithShopOrderByStartedAtDesc();

    @Query("SELECT r FROM ZoomosCheckRun r JOIN FETCH r.shop ORDER BY r.startedAt DESC")
    List<ZoomosCheckRun> findAllWithShopOrderByStartedAtDesc(Pageable pageable);

    List<ZoomosCheckRun> findAllByStatus(CheckRunStatus status);

    /**
     * PERF-001: batch-загрузка последнего run для каждого магазина из списка.
     * JOIN FETCH r.shop — избегает N+1 lazy-load при обращении к r.getShop().getId().
     * Заменяет N вызовов findFirstByShopIdOrderByStartedAtDesc.
     */
    @Query("SELECT r FROM ZoomosCheckRun r JOIN FETCH r.shop s " +
           "WHERE s.id IN :shopIds " +
           "AND r.startedAt = (SELECT MAX(r2.startedAt) FROM ZoomosCheckRun r2 WHERE r2.shop.id = s.id)")
    List<ZoomosCheckRun> findLastRunsForShops(@Param("shopIds") List<Long> shopIds);

    @Query(value = "SELECT * FROM zoomos_check_runs WHERE shop_id = :shopId AND status = 'COMPLETED' ORDER BY started_at DESC LIMIT 2",
           nativeQuery = true)
    List<ZoomosCheckRun> findLastTwoCompletedRuns(@Param("shopId") Long shopId);
}
