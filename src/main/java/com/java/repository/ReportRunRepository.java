package com.java.repository;

import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {

    List<ReportRun> findAllByConfigIdOrderByCreatedAtDesc(Long configId);

    boolean existsByConfigIdAndStatusIn(Long configId, List<ReportRunStatus> statuses);

    @Query("SELECT r FROM ReportRun r JOIN FETCH r.config c LEFT JOIN FETCH c.outputColumns WHERE r.id = :id")
    Optional<ReportRun> findByIdWithConfigAndOutputColumns(@Param("id") Long id);
}
