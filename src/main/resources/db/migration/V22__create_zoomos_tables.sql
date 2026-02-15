-- V22: Таблицы для парсинга данных с export.zoomos.by

-- Магазины (клиенты zoomos.by), добавляются вручную в UI
CREATE TABLE zoomos_shops (
    id             BIGSERIAL PRIMARY KEY,
    shop_name      VARCHAR(255) NOT NULL UNIQUE,
    last_synced_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

-- ID городов по сайтам-конкурентам для каждого магазина
CREATE TABLE zoomos_city_ids (
    id         BIGSERIAL PRIMARY KEY,
    shop_id    BIGINT NOT NULL REFERENCES zoomos_shops(id) ON DELETE CASCADE,
    site_name  VARCHAR(255) NOT NULL,
    city_ids   TEXT,
    is_active  BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (shop_id, site_name)
);

-- Сессионные куки авторизации на export.zoomos.by (хранится одна запись)
CREATE TABLE zoomos_sessions (
    id         BIGSERIAL PRIMARY KEY,
    cookies    TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
