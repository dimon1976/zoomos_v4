-- V23: Таблицы для автоматической проверки полноты выкачки

-- Тип проверки для каждого сайта: API (полная выкачка) или ITEM (по карточкам из кабинета)
ALTER TABLE zoomos_city_ids ADD COLUMN check_type VARCHAR(10) DEFAULT 'API';

-- Журнал запусков проверок
CREATE TABLE zoomos_check_runs (
    id              BIGSERIAL PRIMARY KEY,
    shop_id         BIGINT NOT NULL REFERENCES zoomos_shops(id) ON DELETE CASCADE,
    date_from       DATE NOT NULL,
    date_to         DATE NOT NULL,
    total_sites     INTEGER DEFAULT 0,
    ok_count        INTEGER DEFAULT 0,
    warning_count   INTEGER DEFAULT 0,
    error_count     INTEGER DEFAULT 0,
    not_found_count INTEGER DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'RUNNING',
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_check_runs_shop ON zoomos_check_runs(shop_id);

-- История выкачек — каждая строка = одна строка из таблицы на странице zoomos.by
CREATE TABLE zoomos_parsing_stats (
    id                      BIGSERIAL PRIMARY KEY,
    check_run_id            BIGINT NOT NULL REFERENCES zoomos_check_runs(id) ON DELETE CASCADE,
    city_id_ref             BIGINT REFERENCES zoomos_city_ids(id) ON DELETE SET NULL,

    -- Идентификация выкачки
    parsing_id              BIGINT,
    site_name               VARCHAR(255) NOT NULL,
    city_name               VARCHAR(255),
    server_name             VARCHAR(100),

    -- Временные метки парсинга
    start_time              TIMESTAMPTZ,
    finish_time             TIMESTAMPTZ,

    -- Метрики
    total_products          INTEGER,
    in_stock                INTEGER,
    category_count          INTEGER,
    error_count             INTEGER DEFAULT 0,
    completion_total        VARCHAR(30),
    completion_percent      INTEGER,
    parsing_duration        VARCHAR(50),
    parsing_duration_minutes INTEGER,

    -- Мета
    parsing_date            DATE NOT NULL,
    check_type              VARCHAR(10) NOT NULL,
    checked_at              TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_parsing_stats_run ON zoomos_parsing_stats(check_run_id);
CREATE INDEX idx_parsing_stats_site_date ON zoomos_parsing_stats(site_name, parsing_date);
CREATE INDEX idx_parsing_stats_city_ref ON zoomos_parsing_stats(city_id_ref);
