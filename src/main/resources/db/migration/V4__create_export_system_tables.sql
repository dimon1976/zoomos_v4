-- V4__create_export_system_tables.sql

-- Таблица шаблонов экспорта
CREATE TABLE export_templates (
                                  id BIGSERIAL PRIMARY KEY,
                                  name VARCHAR(255) NOT NULL,
                                  description TEXT,
                                  client_id BIGINT NOT NULL,
                                  entity_type VARCHAR(50) NOT NULL,
                                  export_strategy VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
                                  file_format VARCHAR(10) NOT NULL DEFAULT 'CSV',

    -- Настройки CSV
                                  csv_delimiter VARCHAR(5) DEFAULT ';',
                                  csv_encoding VARCHAR(50) DEFAULT 'UTF-8',
                                  csv_quote_char VARCHAR(5) DEFAULT '"',
                                  csv_include_header BOOLEAN DEFAULT TRUE,

    -- Настройки XLSX
                                  xlsx_sheet_name VARCHAR(100) DEFAULT 'Данные',
                                  xlsx_auto_size_columns BOOLEAN DEFAULT TRUE,

    -- Общие настройки
                                  max_rows_per_file INTEGER,
                                  is_active BOOLEAN DEFAULT TRUE,
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_export_template_client FOREIGN KEY (client_id)
                                      REFERENCES clients(id) ON DELETE CASCADE,
                                  CONSTRAINT uk_export_template_name_client UNIQUE (name, client_id)
);

-- Таблица полей для экспорта
CREATE TABLE export_template_fields (
                                        id BIGSERIAL PRIMARY KEY,
                                        template_id BIGINT NOT NULL,
                                        entity_field_name VARCHAR(255) NOT NULL,
                                        export_column_name VARCHAR(255) NOT NULL,
                                        field_order INTEGER NOT NULL,
                                        is_included BOOLEAN DEFAULT TRUE,
                                        data_format VARCHAR(100), -- Формат данных (для дат, чисел)
                                        transformation_rule TEXT,

                                        CONSTRAINT fk_export_field_template FOREIGN KEY (template_id)
                                            REFERENCES export_templates(id) ON DELETE CASCADE
);

-- Таблица фильтров шаблона
CREATE TABLE export_template_filters (
                                         id BIGSERIAL PRIMARY KEY,
                                         template_id BIGINT NOT NULL,
                                         field_name VARCHAR(255) NOT NULL,
                                         filter_type VARCHAR(50) NOT NULL,
                                         filter_value TEXT NOT NULL,
                                         is_active BOOLEAN DEFAULT TRUE,

                                         CONSTRAINT fk_export_filter_template FOREIGN KEY (template_id)
                                             REFERENCES export_templates(id) ON DELETE CASCADE
);

-- Таблица сессий экспорта
CREATE TABLE export_sessions (
                                 id BIGSERIAL PRIMARY KEY,
                                 file_operation_id BIGINT NOT NULL UNIQUE,
                                 template_id BIGINT NOT NULL,

    -- Входные параметры
                                 source_operation_ids TEXT NOT NULL,
                                 date_filter_from TIMESTAMP WITH TIME ZONE,
                                 date_filter_to TIMESTAMP WITH TIME ZONE,
                                 applied_filters TEXT,

    -- Статистика
                                 total_rows BIGINT,
                                 exported_rows BIGINT DEFAULT 0,
                                 filtered_rows BIGINT DEFAULT 0,
                                 modified_rows BIGINT DEFAULT 0,

    -- Результат
                                 status VARCHAR(50) NOT NULL DEFAULT 'INITIALIZING',
                                 error_message TEXT,
                                 result_file_path TEXT,
                                 file_size BIGINT,

                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,

                                 CONSTRAINT fk_export_session_file_operation FOREIGN KEY (file_operation_id)
                                     REFERENCES file_operations(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_export_session_template FOREIGN KEY (template_id)
                                     REFERENCES export_templates(id)
);

-- Индексы для производительности
CREATE INDEX idx_export_templates_client_id ON export_templates(client_id);
CREATE INDEX idx_export_templates_entity_type ON export_templates(entity_type);
CREATE INDEX idx_export_template_fields_template_id ON export_template_fields(template_id);
CREATE INDEX idx_export_template_fields_order ON export_template_fields(template_id, field_order);
CREATE INDEX idx_export_template_filters_template_id ON export_template_filters(template_id);
CREATE INDEX idx_export_sessions_template_id ON export_sessions(template_id);
CREATE INDEX idx_export_sessions_status ON export_sessions(status);
CREATE INDEX idx_export_sessions_created_at ON export_sessions(started_at DESC);

-- Триггер для обновления updated_at
CREATE TRIGGER update_export_templates_updated_at BEFORE UPDATE
    ON export_templates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Представление для статистики экспортов
CREATE OR REPLACE VIEW v_export_statistics AS
SELECT
    es.id AS session_id,
    es.template_id,
    et.name AS template_name,
    et.client_id,
    c.name AS client_name,
    es.exported_rows,
    es.filtered_rows,
    es.modified_rows,
    es.status,
    es.started_at,
    es.completed_at,
    fo.file_name
FROM export_sessions es
         JOIN export_templates et ON es.template_id = et.id
         JOIN clients c ON et.client_id = c.id
         JOIN file_operations fo ON es.file_operation_id = fo.id
ORDER BY es.started_at DESC;