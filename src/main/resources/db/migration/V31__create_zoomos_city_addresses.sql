-- Справочник адресов по городам (cityId → addressId + название)
CREATE TABLE zoomos_city_addresses (
    id           BIGSERIAL     PRIMARY KEY,
    city_id      VARCHAR(50)   NOT NULL,
    address_id   VARCHAR(50)   NOT NULL,
    address_name VARCHAR(500),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (city_id, address_id)
);

CREATE INDEX idx_zca_city_id ON zoomos_city_addresses(city_id);

COMMENT ON TABLE zoomos_city_addresses IS 'Справочник: cityId → известные addressId с названиями';
