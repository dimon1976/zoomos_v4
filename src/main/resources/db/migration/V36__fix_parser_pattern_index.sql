-- V36: Заменяем B-tree индекс по TEXT на MD5-функциональный (решение проблемы "размер строки индекса > 2704")
ALTER TABLE zoomos_parser_patterns DROP CONSTRAINT IF EXISTS uq_parser_pattern;
CREATE UNIQUE INDEX IF NOT EXISTS uq_parser_pattern ON zoomos_parser_patterns (site_name, md5(pattern));
