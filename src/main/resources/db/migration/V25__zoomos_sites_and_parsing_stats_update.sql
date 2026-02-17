-- V25: справочник сайтов + расширение zoomos_parsing_stats

-- Справочник сайтов (глобальный, без привязки к клиенту)
CREATE TABLE zoomos_sites (
    id BIGSERIAL PRIMARY KEY,
    site_name VARCHAR(255) NOT NULL UNIQUE,
    check_type VARCHAR(10) NOT NULL DEFAULT 'ITEM',
    description VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Новые поля в zoomos_parsing_stats
ALTER TABLE zoomos_parsing_stats ADD COLUMN client_name VARCHAR(255);
ALTER TABLE zoomos_parsing_stats ADD COLUMN updated_time TIMESTAMPTZ;
ALTER TABLE zoomos_parsing_stats ADD COLUMN is_finished BOOLEAN DEFAULT TRUE;

-- Изменить дефолт check_type на ITEM для новых записей zoomos_city_ids
ALTER TABLE zoomos_city_ids ALTER COLUMN check_type SET DEFAULT 'ITEM';
