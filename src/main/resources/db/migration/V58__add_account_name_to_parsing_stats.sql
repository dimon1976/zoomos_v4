-- V58: Добавляем поле account_name в zoomos_parsing_stats
-- Хранит аккаунт, под которым выполнялась выкачка ("[ID] название@аккаунта")

ALTER TABLE zoomos_parsing_stats ADD COLUMN account_name VARCHAR(255);
