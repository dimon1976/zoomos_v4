-- Задача 3: CITIES_EQUAL_PRICES — флаг что цены одинаковы во всех городах
ALTER TABLE zoomos_sites
    ADD COLUMN cities_equal_prices BOOLEAN,
    ADD COLUMN cities_equal_prices_checked_at TIMESTAMPTZ;
