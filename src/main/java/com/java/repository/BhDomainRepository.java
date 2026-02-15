package com.java.repository;

import com.java.model.entity.BhDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BhDomainRepository extends JpaRepository<BhDomain, Long> {

    Optional<BhDomain> findByDomain(String domain);

    List<BhDomain> findAllByOrderByUrlCountDesc();

    @Query("SELECT d FROM BhDomain d WHERE LOWER(d.domain) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY d.urlCount DESC")
    List<BhDomain> searchByDomain(@Param("q") String query);

    @Modifying
    @Query("UPDATE BhDomain d SET d.urlCount = d.urlCount + 1 WHERE d.domain = :domain")
    void incrementUrlCount(@Param("domain") String domain);
}
