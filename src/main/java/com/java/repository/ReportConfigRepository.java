package com.java.repository;

import com.java.model.entity.ReportConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {

    @EntityGraph(attributePaths = "client")
    List<ReportConfig> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "client")
    List<ReportConfig> findAllByClientIdOrderByNameAsc(Long clientId);

    @Query("SELECT DISTINCT c FROM ReportConfig c LEFT JOIN FETCH c.client LEFT JOIN FETCH c.outputColumns WHERE c.id = :id")
    Optional<ReportConfig> findByIdWithClientAndOutputColumns(@Param("id") Long id);
}
