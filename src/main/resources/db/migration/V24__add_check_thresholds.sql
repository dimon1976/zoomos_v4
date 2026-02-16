-- V24: Настраиваемые пороги для определения проблем при проверке выкачки
ALTER TABLE zoomos_check_runs ADD COLUMN drop_threshold INTEGER DEFAULT 10;
ALTER TABLE zoomos_check_runs ADD COLUMN error_growth_threshold INTEGER DEFAULT 30;
