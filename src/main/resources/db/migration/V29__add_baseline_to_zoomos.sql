-- V29: Добавление поддержки исторического baseline-анализа в Zoomos Check
-- baseline_days: горизонт сравнения (дней назад от начала проверяемого периода)
-- is_baseline: флаг что запись была загружена как исторический baseline, а не основная проверка

ALTER TABLE zoomos_check_runs
    ADD COLUMN baseline_days INTEGER NOT NULL DEFAULT 7;

ALTER TABLE zoomos_parsing_stats
    ADD COLUMN is_baseline BOOLEAN NOT NULL DEFAULT false;
