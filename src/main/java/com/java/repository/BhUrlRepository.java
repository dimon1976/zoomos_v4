package com.java.repository;

import com.java.model.entity.BhUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BhUrlRepository extends JpaRepository<BhUrl, Long> {

    boolean existsByProductIdAndUrl(Long productId, String url);

    @Query("SELECT u FROM BhUrl u WHERE u.product.id IN :productIds")
    List<BhUrl> findByProductIdIn(@Param("productIds") List<Long> productIds);

    @Query("SELECT u FROM BhUrl u WHERE u.product.id IN :productIds AND u.domain IN :domains")
    List<BhUrl> findByProductIdInAndDomainIn(
            @Param("productIds") List<Long> productIds,
            @Param("domains") List<String> domains
    );
}
