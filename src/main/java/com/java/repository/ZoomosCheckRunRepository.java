package com.java.repository;

import com.java.model.entity.ZoomosCheckRun;
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
    @Query("SELECT r FROM ZoomosCheckRun r WHERE r.status = 'COMPLETED' " +
           "AND r.startedAt >= :startOfDay ORDER BY r.startedAt DESC")
    List<ZoomosCheckRun> findCompletedToday(@Param("startOfDay") ZonedDateTime startOfDay);
}
