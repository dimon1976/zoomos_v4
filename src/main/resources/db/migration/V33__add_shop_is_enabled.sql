-- V33: Признак активности магазина (включён / выключен)
ALTER TABLE zoomos_shops ADD COLUMN IF NOT EXISTS is_enabled BOOLEAN NOT NULL DEFAULT TRUE;
