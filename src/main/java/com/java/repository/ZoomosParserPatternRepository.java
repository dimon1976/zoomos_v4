package com.java.repository;

import com.java.model.entity.ZoomosParserPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ZoomosParserPatternRepository extends JpaRepository<ZoomosParserPattern, Long> {

    List<ZoomosParserPattern> findBySiteNameOrderByPatternAsc(String siteName);

    /**
     * REQUIRES_NEW: изолирует от внешней транзакции runCheck.
     * Если ON CONFLICT не сработает (индекс не найден) — только эта транзакция откатится,
     * внешняя транзакция не будет испорчена.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "INSERT INTO zoomos_parser_patterns (site_name, pattern) " +
                   "VALUES (:siteName, :pattern) ON CONFLICT (site_name, md5(pattern)) DO NOTHING", nativeQuery = true)
    void upsert(@Param("siteName") String siteName, @Param("pattern") String pattern);
}
