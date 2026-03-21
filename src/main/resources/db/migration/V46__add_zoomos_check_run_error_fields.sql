-- Добавляем поля для хранения ошибок и счётчика таймаутов в проверках Zoomos Check
ALTER TABLE zoomos_check_runs
    ADD COLUMN IF NOT EXISTS error_message  VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS timeout_count  INTEGER DEFAULT 0;
