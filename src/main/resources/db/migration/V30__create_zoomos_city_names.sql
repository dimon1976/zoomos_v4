-- Справочник: cityId → наименование города (Zoomos)
CREATE TABLE zoomos_city_names (
    city_id    VARCHAR(50)  PRIMARY KEY,
    city_name  VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE zoomos_city_names IS 'Справочник ID городов Zoomos → названия городов';
