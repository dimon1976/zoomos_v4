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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Загружает DISTINCT бренды из bh_products и создаёт эталонные записи (UPSERT).
     * Возвращает статистику: {"created": N, "skipped": M}.
     */
    @Transactional
    public Map<String, Integer> importFromProducts() {
        List<String> brands = jdbc.queryForList(
                "SELECT DISTINCT brand FROM bh_products WHERE brand IS NOT NULL AND TRIM(brand) != '' ORDER BY brand",
                String.class);

        int created = 0;
        int skipped = 0;
        for (String brand : brands) {
            String name = brand.trim();
            if (brandRepo.findByNameIgnoreCase(name).isEmpty()) {
                try {
                    brandRepo.save(BhBrand.builder().name(name).build());
                    created++;
                } catch (Exception e) {
                    log.debug("Не удалось создать бренд '{}': {}", name, e.getMessage());
                    skipped++;
                }
            } else {
                skipped++;
            }
        }
        log.info("importFromProducts: создано {} брендов, пропущено {}", created, skipped);
        return Map.of("created", created, "skipped", skipped);
    }

    public long countBrands() {
        return brandRepo.count();
    }
}
