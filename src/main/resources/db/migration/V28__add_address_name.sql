-- Сохраняем полное название адреса (строку из колонки "Адрес" таблицы истории парсинга)
ALTER TABLE zoomos_parsing_stats ADD COLUMN address_name TEXT;
