-- Задача 1: тип конфигурационной проблемы для пары клиент→сайт
ALTER TABLE zoomos_city_ids
    ADD COLUMN config_issue_type VARCHAR(30);

COMMENT ON COLUMN zoomos_city_ids.config_issue_type IS
  'Тип конфиг. проблемы: WRONG_CITY_IDS, WRONG_PARSER, SITE_CHANGED, KNOWN_ISSUE, NOT_RELEVANT, OTHER';
