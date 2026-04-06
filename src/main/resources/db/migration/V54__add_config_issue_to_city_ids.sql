-- Задача 1: флаг конфигурационной проблемы на уровне клиент→сайт (zoomos_city_ids)
ALTER TABLE zoomos_city_ids
    ADD COLUMN has_config_issue BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN config_issue_note TEXT;
