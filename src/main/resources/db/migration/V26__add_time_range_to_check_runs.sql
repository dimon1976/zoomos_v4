-- Добавляем опциональное время старта и финиша для проверок
ALTER TABLE zoomos_check_runs ADD COLUMN time_from VARCHAR(5);
ALTER TABLE zoomos_check_runs ADD COLUMN time_to   VARCHAR(5);
