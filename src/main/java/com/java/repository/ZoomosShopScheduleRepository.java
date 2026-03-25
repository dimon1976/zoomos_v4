package com.java.repository;

import com.java.model.entity.ZoomosShopSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZoomosShopScheduleRepository extends JpaRepository<ZoomosShopSchedule, Long> {

    /** Возвращает первое расписание магазина (для обратной совместимости с index.html и одиночных операций) */
    Optional<ZoomosShopSchedule> findFirstByShopId(Long shopId);

    List<ZoomosShopSchedule> findAllByShopId(Long shopId);

    List<ZoomosShopSchedule> findAllByShopIdIn(Collection<Long> shopIds);

    List<ZoomosShopSchedule> findAllByIsEnabledTrue();
}
