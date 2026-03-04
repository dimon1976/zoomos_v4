-- V38: убираем UNIQUE на shop_id, добавляем поле label
ALTER TABLE zoomos_shop_schedules DROP CONSTRAINT IF EXISTS zoomos_shop_schedules_shop_id_key;
ALTER TABLE zoomos_shop_schedules ADD COLUMN IF NOT EXISTS label VARCHAR(50);
