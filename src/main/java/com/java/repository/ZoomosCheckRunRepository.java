package com.java.repository;

import com.java.model.entity.ZoomosCheckRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomosCheckRunRepository extends JpaRepository<ZoomosCheckRun, Long> {

    List<ZoomosCheckRun> findByShopIdOrderByStartedAtDesc(Long shopId);
}
