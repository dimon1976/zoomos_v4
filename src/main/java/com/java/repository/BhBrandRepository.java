package com.java.repository;

import com.java.model.entity.BhBrand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BhBrandRepository extends JpaRepository<BhBrand, Long> {

    Optional<BhBrand> findByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = "synonyms")
    Page<BhBrand> findAllByOrderByName(Pageable pageable);

    @EntityGraph(attributePaths = "synonyms")
    Page<BhBrand> findByNameContainingIgnoreCaseOrderByName(String name, Pageable pageable);
}
