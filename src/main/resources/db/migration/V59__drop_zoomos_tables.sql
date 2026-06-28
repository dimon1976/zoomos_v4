-- Удаление всего функционала Zoomos Check (перенесён в отдельное приложение)

DROP TABLE IF EXISTS zoomos_profile_sites CASCADE;
DROP TABLE IF EXISTS zoomos_check_profiles CASCADE;
DROP TABLE IF EXISTS zoomos_redmine_issues CASCADE;
DROP TABLE IF EXISTS zoomos_shop_schedules CASCADE;
DROP TABLE IF EXISTS zoomos_parser_patterns CASCADE;
DROP TABLE IF EXISTS zoomos_city_names CASCADE;
DROP TABLE IF EXISTS zoomos_city_addresses CASCADE;
DROP TABLE IF EXISTS zoomos_parsing_stats CASCADE;
DROP TABLE IF EXISTS zoomos_check_runs CASCADE;
DROP TABLE IF EXISTS zoomos_sites CASCADE;
DROP TABLE IF EXISTS zoomos_city_ids CASCADE;
DROP TABLE IF EXISTS zoomos_sessions CASCADE;
DROP TABLE IF EXISTS zoomos_shops CASCADE;
