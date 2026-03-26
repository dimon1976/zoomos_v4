-- V47: Справочник брендов и синонимов для нормализации при импорте
CREATE TABLE bh_brands (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(name)
);

CREATE TABLE bh_brand_synonyms (
    id         BIGSERIAL PRIMARY KEY,
    brand_id   BIGINT NOT NULL REFERENCES bh_brands(id) ON DELETE CASCADE,
    synonym    VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(synonym)
);

CREATE INDEX idx_bh_brand_synonyms_lower ON bh_brand_synonyms (LOWER(synonym));
