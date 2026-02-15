-- V21: Справочник штрихкодов (Barcode Handbook)
-- Таблицы для централизованного хранения связей штрихкод→название→ссылка

-- Основная таблица продуктов (справочная запись)
CREATE TABLE bh_products (
    id         BIGSERIAL PRIMARY KEY,
    barcode    VARCHAR(30) UNIQUE,          -- Может быть NULL (когда штрихкод неизвестен)
    brand      VARCHAR(500),
    manufacturer_code VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE bh_products IS 'Справочник штрихкодов: основная таблица продуктов';
COMMENT ON COLUMN bh_products.barcode IS 'Штрихкод (уникален, может быть NULL)';

-- Варианты наименований продукта
CREATE TABLE bh_names (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES bh_products(id) ON DELETE CASCADE,
    name       VARCHAR(2000) NOT NULL,
    source     VARCHAR(500),               -- Источник (имя файла или шаблон)
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (product_id, name)
);

COMMENT ON TABLE bh_names IS 'Варианты наименований для продукта';

-- Ссылки на карточки товаров
CREATE TABLE bh_urls (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES bh_products(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    domain     VARCHAR(255),               -- Извлечённый домен (goldapple.ru)
    site_name  VARCHAR(500),               -- Название сайта (если передано)
    source     VARCHAR(500),               -- Источник
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (product_id, url)
);

COMMENT ON TABLE bh_urls IS 'Ссылки на карточки продуктов по сайтам';

-- Реестр доменов с счётчиком URL
CREATE TABLE bh_domains (
    id          BIGSERIAL PRIMARY KEY,
    domain      VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN DEFAULT TRUE,
    url_count   BIGINT DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE bh_domains IS 'Реестр доменов сайтов из справочника';

-- Индексы для производительности
CREATE INDEX idx_bh_products_barcode    ON bh_products(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_bh_products_brand      ON bh_products(brand);
CREATE INDEX idx_bh_names_product_id    ON bh_names(product_id);
CREATE INDEX idx_bh_names_name_lower    ON bh_names(LOWER(TRIM(name)));
CREATE INDEX idx_bh_urls_product_id     ON bh_urls(product_id);
CREATE INDEX idx_bh_urls_domain         ON bh_urls(domain);
CREATE INDEX idx_bh_domains_domain      ON bh_domains(domain);
CREATE INDEX idx_bh_domains_active      ON bh_domains(is_active);

-- Триггер обновления updated_at для bh_products
CREATE OR REPLACE FUNCTION bh_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bh_products_updated_at
    BEFORE UPDATE ON bh_products
    FOR EACH ROW EXECUTE FUNCTION bh_update_timestamp();
