package com.java.repository;

import com.java.model.entity.ZoomosShopSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosShopScheduleRepository extends JpaRepository<ZoomosShopSchedule, Long> {

    Optional<ZoomosShopSchedule> findByShopId(Long shopId);

    List<ZoomosShopSchedule> findAllByIsEnabledTrue();
}
