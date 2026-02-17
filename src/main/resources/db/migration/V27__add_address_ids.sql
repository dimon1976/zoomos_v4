-- Добавляем поддержку фильтрации по ID адресов (addressId) для сайтов с несколькими адресами
ALTER TABLE zoomos_city_ids ADD COLUMN address_ids TEXT;
ALTER TABLE zoomos_parsing_stats ADD COLUMN address_id VARCHAR(20);
