ALTER TABLE zoomos_city_ids ADD COLUMN master_city_id VARCHAR(50) NULL;

COMMENT ON COLUMN zoomos_city_ids.master_city_id IS
  'Мастер-город: если задан, при проверке считается достаточным наличие данных только по этому городу. Остальные города из city_ids не проверяются на наличие.';
