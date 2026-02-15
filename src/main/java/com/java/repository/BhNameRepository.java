package com.java.repository;

import com.java.model.entity.BhName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BhNameRepository extends JpaRepository<BhName, Long> {

    /**
     * Поиск по наименованию (без учёта регистра и пробелов по краям).
     * JOIN FETCH для загрузки продукта в одном запросе.
     */
    @Query("SELECT n FROM BhName n JOIN FETCH n.product WHERE LOWER(TRIM(n.name)) = LOWER(TRIM(:name))")
    Optional<BhName> findByNameIgnoreCase(@Param("name") String name);

    boolean existsByProductIdAndName(Long productId, String name);

    @Query("SELECT n FROM BhName n WHERE n.product.id IN :productIds")
    List<BhName> findByProductIdIn(@Param("productIds") List<Long> productIds);
}
