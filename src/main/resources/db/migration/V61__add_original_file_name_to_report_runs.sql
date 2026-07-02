-- V61__add_original_file_name_to_report_runs.sql
-- Оригинальное имя файла из заголовка Content-Disposition ответа Zoomos (если он его прислал) —
-- используется при скачивании итогового файла вместо внутреннего сгенерированного имени.

ALTER TABLE report_runs ADD COLUMN original_file_name VARCHAR(500);
