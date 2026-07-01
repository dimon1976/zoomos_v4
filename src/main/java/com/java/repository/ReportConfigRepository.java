package com.java.repository;

import com.java.model.entity.ReportConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {

    @EntityGraph(attributePaths = "client")
    List<ReportConfig> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "client")
    List<ReportConfig> findAllByClientIdOrderByNameAsc(Long clientId);
}
