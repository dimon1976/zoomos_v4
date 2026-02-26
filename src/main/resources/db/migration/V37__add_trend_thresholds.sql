-- Отдельные пороги для тренд-анализа (отдельно от основной проверки)
-- trend_drop_threshold: порог замедления выкачки и падения доли «В наличии» (%), default 30
-- trend_error_threshold: порог роста доли ошибок от товаров (%), default 100
ALTER TABLE zoomos_check_runs
    ADD COLUMN IF NOT EXISTS trend_drop_threshold   INT NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS trend_error_threshold  INT NOT NULL DEFAULT 100;

ALTER TABLE zoomos_shop_schedules
    ADD COLUMN IF NOT EXISTS trend_drop_threshold   INT NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS trend_error_threshold  INT NOT NULL DEFAULT 100;
