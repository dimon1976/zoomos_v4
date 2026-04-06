-- Задача 2: перенести master_city_id с уровня клиент→сайт (zoomos_city_ids) на уровень сайта (zoomos_sites)
ALTER TABLE zoomos_sites ADD COLUMN master_city_id VARCHAR(50) NULL;

UPDATE zoomos_sites s SET master_city_id = (
    SELECT master_city_id FROM zoomos_city_ids c
    WHERE c.site_name = s.site_name
      AND c.master_city_id IS NOT NULL AND c.master_city_id <> ''
    ORDER BY c.id LIMIT 1
)
WHERE EXISTS (
    SELECT 1 FROM zoomos_city_ids c
    WHERE c.site_name = s.site_name
      AND c.master_city_id IS NOT NULL AND c.master_city_id <> ''
);

COMMENT ON COLUMN zoomos_city_ids.master_city_id IS
  'DEPRECATED с V53: перенесено в zoomos_sites.master_city_id';
