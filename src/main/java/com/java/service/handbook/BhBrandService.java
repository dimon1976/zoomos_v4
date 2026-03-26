package com.java.service.handbook;

import com.java.model.entity.BhBrand;
import com.java.model.entity.BhBrandSynonym;
import com.java.repository.BhBrandRepository;
import com.java.repository.BhBrandSynonymRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowCallbackHandler;

@Service
@RequiredArgsConstructor
@Slf4j
public class BhBrandService {

    private final BhBrandRepository brandRepo;
    private final BhBrandSynonymRepository synonymRepo;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<BhBrand> getBrandsPage(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        if (query == null || query.isBlank()) {
            return brandRepo.findAllByOrderByName(pageable);
        }
        return brandRepo.findByNameContainingIgnoreCaseOrderByName(query.trim(), pageable);
    }

    @Transactional
    public BhBrand createBrand(String name) {
        String normalized = name.trim();
        brandRepo.findByNameIgnoreCase(normalized).ifPresent(b -> {
            throw new IllegalArgumentException("Бренд уже существует: " + b.getName());
        });
        return brandRepo.save(BhBrand.builder().name(normalized).build());
    }

    @Transactional
    public void deleteBrand(Long id) {
        BhBrand brand = brandRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Бренд не найден: " + id));
        Integer usageCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bh_products WHERE brand = ?", Integer.class, brand.getName());
        if (usageCount != null && usageCount > 0) {
            throw new IllegalStateException(
                    "Бренд используется в " + usageCount + " продуктах — удаление невозможно");
        }
        brandRepo.deleteById(id);
    }

    @Transactional
    public BhBrandSynonym addSynonym(Long brandId, String synonym) {
        BhBrand brand = brandRepo.findById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("Бренд не найден: " + brandId));
        String normalized = synonym.trim();
        synonymRepo.findBySynonymIgnoreCase(normalized).ifPresent(s -> {
            throw new IllegalArgumentException("Синоним уже существует: " + s.getSynonym());
        });
        return synonymRepo.save(BhBrandSynonym.builder()
                .brand(brand)
                .synonym(normalized)
                .build());
    }

    @Transactional
    public void deleteSynonym(Long synonymId) {
        synonymRepo.deleteById(synonymId);
    }

    /**
     * Нормализует бренд: ищет case-insensitive в bh_brand_synonyms,
     * возвращает эталонное название бренда или оригинал если не найдено.
     */
    @Transactional(readOnly = true)
    public String normalizeBrand(String rawBrand) {
        if (rawBrand == null || rawBrand.isBlank()) return rawBrand;
        return synonymRepo.findBySynonymIgnoreCase(rawBrand.trim())
                .map(s -> s.getBrand().getName())
                .orElse(rawBrand);
    }

    /**
     * Загружает маппинг синонимов для набора брендов одним SQL-запросом.
     * Используется в батч-импорте для устранения N+1 запросов.
     */
    @Transactional(readOnly = true)
    public Map<String, String> buildSynonymMap(Collection<String> rawBrands) {
        if (rawBrands == null || rawBrands.isEmpty()) return Collections.emptyMap();
        List<String> lowerBrands = rawBrands.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(b -> b.trim().toLowerCase())
                .distinct()
                .collect(Collectors.toList());
        if (lowerBrands.isEmpty()) return Collections.emptyMap();
        String inClause = String.join(",", Collections.nCopies(lowerBrands.size(), "?"));
        Map<String, String> result = new HashMap<>();
        jdbc.query(
                "SELECT LOWER(TRIM(s.synonym)) AS syn, b.name AS canonical " +
                "FROM bh_brand_synonyms s JOIN bh_brands b ON b.id = s.brand_id " +
                "WHERE LOWER(TRIM(s.synonym)) IN (" + inClause + ")",
                lowerBrands.toArray(),
                (RowCallbackHandler) rs -> result.put(rs.getString("syn"), rs.getString("canonical")));
        return result;
    }

    /**
     * Загружает DISTINCT бренды из bh_products и создаёт эталонные записи (UPSERT).
     * Возвращает статистику: {"created": N, "skipped": M}.
     */
    @Transactional
    public Map<String, Integer> importFromProducts() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT TRIM(brand)) FROM bh_products " +
                "WHERE brand IS NOT NULL AND TRIM(brand) != ''",
                Integer.class);
        int inserted = jdbc.update(
                "INSERT INTO bh_brands (name, created_at) " +
                "SELECT DISTINCT TRIM(brand), NOW() FROM bh_products " +
                "WHERE brand IS NOT NULL AND TRIM(brand) != '' " +
                "ON CONFLICT (name) DO NOTHING");
        int skipped = (total != null ? total : 0) - inserted;
        log.info("importFromProducts: создано {} брендов, пропущено {}", inserted, skipped);
        return Map.of("created", inserted, "skipped", Math.max(skipped, 0));
    }

    public long countBrands() {
        return brandRepo.count();
    }
}
