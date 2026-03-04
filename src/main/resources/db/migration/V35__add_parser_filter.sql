-- V35: Фильтр по типу парсера для Zoomos Check

-- Справочник известных паттернов парсера (накапливается автоматически при парсинге)
CREATE TABLE IF NOT EXISTS zoomos_parser_patterns (
    id          BIGSERIAL PRIMARY KEY,
    site_name   VARCHAR(255) NOT NULL,
    pattern     TEXT NOT NULL,
    CONSTRAINT uq_parser_pattern UNIQUE (site_name, pattern)
);

-- Хранит текст колонки "Парсер" из таблицы истории
ALTER TABLE zoomos_parsing_stats ADD COLUMN IF NOT EXISTS parser_description TEXT;

-- Фильтры на уровне конфигурации сайта+города
-- parser_include: запятая-разделённые подстроки включения, NULL/пусто = без фильтра
-- parser_include_mode: 'OR' (любая) / 'AND' (все должны совпасть), default OR
-- parser_exclude: запятая-разделённые подстроки исключения (всегда OR-логика)
ALTER TABLE zoomos_city_ids ADD COLUMN IF NOT EXISTS parser_include TEXT;
ALTER TABLE zoomos_city_ids ADD COLUMN IF NOT EXISTS parser_include_mode VARCHAR(3) DEFAULT 'OR';
ALTER TABLE zoomos_city_ids ADD COLUMN IF NOT EXISTS parser_exclude TEXT;
