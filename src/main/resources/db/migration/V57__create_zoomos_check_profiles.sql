-- V57: Профили проверки выкачки и сайты внутри профиля

CREATE TABLE zoomos_check_profiles (
    id               BIGSERIAL PRIMARY KEY,
    shop_id          BIGINT NOT NULL REFERENCES zoomos_shops(id) ON DELETE CASCADE,
    label            VARCHAR(100),
    days_of_week     VARCHAR(20),             -- пусто = каждый день, иначе "1,2,3" (1=пн, 7=вс)
    time_from        VARCHAR(5),              -- "HH:mm" — начало окна выкачки
    time_to          VARCHAR(5),              -- "HH:mm" — дедлайн проверки
    cron_expression  VARCHAR(255),            -- для автозапуска (опционально)
    drop_threshold          INTEGER DEFAULT 10,
    error_growth_threshold  INTEGER DEFAULT 30,
    baseline_days           INTEGER DEFAULT 7,
    min_absolute_errors     INTEGER DEFAULT 5,
    trend_drop_threshold    INTEGER DEFAULT 30,
    trend_error_threshold   INTEGER DEFAULT 100,
    stall_minutes           INTEGER DEFAULT 60,  -- порог зависания (updated_time не менялся X мин)
    is_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE zoomos_profile_sites (
    id               BIGSERIAL PRIMARY KEY,
    profile_id       BIGINT NOT NULL REFERENCES zoomos_check_profiles(id) ON DELETE CASCADE,
    site_name        VARCHAR(255) NOT NULL,
    city_ids         TEXT,                    -- переопределяют настройки магазина, если заполнены
    account_filter   VARCHAR(255),            -- фильтр по аккаунту (пусто = любой)
    parser_include        TEXT,
    parser_include_mode   VARCHAR(3) DEFAULT 'OR',
    parser_exclude        TEXT,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (profile_id, site_name)
);

CREATE INDEX idx_zoomos_check_profiles_shop ON zoomos_check_profiles(shop_id);
CREATE INDEX idx_zoomos_profile_sites_profile ON zoomos_profile_sites(profile_id);
