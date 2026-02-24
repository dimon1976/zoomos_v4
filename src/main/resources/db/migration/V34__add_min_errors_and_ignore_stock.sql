-- Минимальный абсолютный порог ошибок (по умолчанию 5)
ALTER TABLE zoomos_check_runs
    ADD COLUMN IF NOT EXISTS min_absolute_errors INTEGER DEFAULT 5;

ALTER TABLE zoomos_shop_schedules
    ADD COLUMN IF NOT EXISTS min_absolute_errors INTEGER NOT NULL DEFAULT 5;

-- Флаг "нет данных о наличии" в справочнике сайтов
ALTER TABLE zoomos_sites
    ADD COLUMN IF NOT EXISTS ignore_stock BOOLEAN NOT NULL DEFAULT FALSE;
