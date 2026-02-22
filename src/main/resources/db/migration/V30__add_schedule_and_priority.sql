-- V30: Расписание проверок и приоритетные сайты в Zoomos Check

-- Приоритетный флаг в справочнике сайтов
ALTER TABLE zoomos_sites ADD COLUMN IF NOT EXISTS is_priority BOOLEAN NOT NULL DEFAULT FALSE;

-- Расписания автоматических проверок (одно расписание на магазин)
CREATE TABLE IF NOT EXISTS zoomos_shop_schedules (
    id                    BIGSERIAL PRIMARY KEY,
    shop_id               BIGINT NOT NULL UNIQUE REFERENCES zoomos_shops(id) ON DELETE CASCADE,
    cron_expression       VARCHAR(100) NOT NULL DEFAULT '0 8 * * *',
    is_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    time_from             VARCHAR(5),
    time_to               VARCHAR(5),
    drop_threshold        INTEGER NOT NULL DEFAULT 10,
    error_growth_threshold INTEGER NOT NULL DEFAULT 30,
    baseline_days         INTEGER NOT NULL DEFAULT 7,
    date_offset_from      INTEGER NOT NULL DEFAULT -1,
    date_offset_to        INTEGER NOT NULL DEFAULT 0,
    last_run_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
