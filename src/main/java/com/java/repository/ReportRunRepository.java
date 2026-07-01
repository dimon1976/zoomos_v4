package com.java.repository;

import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {

    List<ReportRun> findAllByConfigIdOrderByCreatedAtDesc(Long configId);

    boolean existsByConfigIdAndStatusIn(Long configId, List<ReportRunStatus> statuses);
}
