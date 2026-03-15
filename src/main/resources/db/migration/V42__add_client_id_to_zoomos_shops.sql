-- Связь ZoomosShop → Client (nullable, ON DELETE SET NULL)
ALTER TABLE zoomos_shops
    ADD COLUMN IF NOT EXISTS client_id BIGINT,
    ADD CONSTRAINT fk_zoomos_shops_client_id
        FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_zoomos_shops_client_id ON zoomos_shops(client_id);
