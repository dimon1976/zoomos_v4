-- V9__add_filename_template_settings.sql

-- Добавляем настройки для шаблона имени файла
ALTER TABLE export_templates
    ADD COLUMN filename_template VARCHAR(500) DEFAULT NULL,
    ADD COLUMN include_client_name BOOLEAN DEFAULT TRUE,
    ADD COLUMN include_export_type BOOLEAN DEFAULT FALSE,
    ADD COLUMN include_task_number BOOLEAN DEFAULT FALSE,
    ADD COLUMN export_type_label VARCHAR(100) DEFAULT NULL,
    ADD COLUMN operation_name_source VARCHAR(100) DEFAULT NULL; -- 'FILE_NAME', 'TASK_NUMBER', 'CUSTOM'

-- Комментарии
COMMENT ON COLUMN export_templates.filename_template IS 'Шаблон имени файла с плейсхолдерами: {client}, {date}, {time}, {type}, {task}';
COMMENT ON COLUMN export_templates.include_client_name IS 'Включать имя клиента в название файла';
COMMENT ON COLUMN export_templates.include_export_type IS 'Включать тип экспорта в название файла';
COMMENT ON COLUMN export_templates.include_task_number IS 'Включать номер задания в название файла';
COMMENT ON COLUMN export_templates.export_type_label IS 'Метка типа экспорта для имени файла';
COMMENT ON COLUMN export_templates.operation_name_source IS 'Источник для названия операции в статистике';