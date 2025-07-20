-- V2__create_import_system_tables.sql

-- Таблица шаблонов импорта
CREATE TABLE import_templates (
                                  id BIGSERIAL PRIMARY KEY,
                                  name VARCHAR(255) NOT NULL UNIQUE,
                                  description TEXT,
                                  client_id BIGINT NOT NULL,
                                  entity_type VARCHAR(50) NOT NULL,
                                  duplicate_strategy VARCHAR(50) NOT NULL DEFAULT 'ALLOW_ALL',
                                  error_strategy VARCHAR(50) NOT NULL DEFAULT 'CONTINUE_ON_ERROR',
                                  file_type VARCHAR(10),
                                  delimiter VARCHAR(5),
                                  encoding VARCHAR(50),
                                  skip_header_rows INTEGER DEFAULT 1,
                                  is_active BOOLEAN DEFAULT TRUE,
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_import_template_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

-- Таблица полей шаблона
CREATE TABLE import_template_fields (
                                        id BIGSERIAL PRIMARY KEY,
                                        template_id BIGINT NOT NULL,
                                        column_name VARCHAR(255),
                                        column_index INTEGER,
                                        entity_field_name VARCHAR(255) NOT NULL,
                                        field_type VARCHAR(50) DEFAULT 'STRING',
                                        is_required BOOLEAN DEFAULT FALSE,
                                        is_unique BOOLEAN DEFAULT FALSE,
                                        default_value TEXT,
                                        date_format VARCHAR(100),
                                        transformation_rule TEXT,
                                        validation_regex TEXT,
                                        validation_message TEXT,
                                        CONSTRAINT fk_template_field_template FOREIGN KEY (template_id) REFERENCES import_templates(id) ON DELETE CASCADE
);

-- Таблица сессий импорта
CREATE TABLE import_sessions (
                                 id BIGSERIAL PRIMARY KEY,
                                 file_operation_id BIGINT NOT NULL UNIQUE,
                                 template_id BIGINT NOT NULL,
                                 total_rows BIGINT,
                                 processed_rows BIGINT DEFAULT 0,
                                 success_rows BIGINT DEFAULT 0,
                                 error_rows BIGINT DEFAULT 0,
                                 duplicate_rows BIGINT DEFAULT 0,
                                 status VARCHAR(50) NOT NULL DEFAULT 'INITIALIZING',
                                 error_message TEXT,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 is_cancelled BOOLEAN DEFAULT FALSE,
                                 CONSTRAINT fk_import_session_file_operation FOREIGN KEY (file_operation_id) REFERENCES file_operations(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_import_session_template FOREIGN KEY (template_id) REFERENCES import_templates(id)
);

-- Таблица ошибок импорта
CREATE TABLE import_errors (
                               id BIGSERIAL PRIMARY KEY,
                               import_session_id BIGINT NOT NULL,
                               row_number BIGINT NOT NULL,
                               column_name VARCHAR(255),
                               field_value TEXT,
                               error_type VARCHAR(50) NOT NULL,
                               error_message TEXT NOT NULL,
                               stack_trace TEXT,
                               occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_import_error_session FOREIGN KEY (import_session_id) REFERENCES import_sessions(id) ON DELETE CASCADE
);

-- Таблица метаданных файла
CREATE TABLE file_metadata (
                               id BIGSERIAL PRIMARY KEY,
                               import_session_id BIGINT NOT NULL UNIQUE,
                               original_filename VARCHAR(500) NOT NULL,
                               file_size BIGINT,
                               file_hash VARCHAR(64),
                               detected_encoding VARCHAR(50),
                               detected_delimiter VARCHAR(5),
                               detected_quote_char VARCHAR(5),
                               detected_escape_char VARCHAR(5),
                               total_columns INTEGER,
                               column_headers TEXT,
                               sample_data TEXT,
                               file_format VARCHAR(10),
                               has_header BOOLEAN,
                               temp_file_path TEXT,
                               CONSTRAINT fk_file_metadata_session FOREIGN KEY (import_session_id) REFERENCES import_sessions(id) ON DELETE CASCADE
);

-- Индексы для производительности
CREATE INDEX idx_import_templates_client_id ON import_templates(client_id);
CREATE INDEX idx_import_templates_entity_type ON import_templates(entity_type);
CREATE INDEX idx_import_template_fields_template_id ON import_template_fields(template_id);
CREATE INDEX idx_import_template_fields_column_index ON import_template_fields(column_index);
CREATE INDEX idx_import_sessions_file_operation_id ON import_sessions(file_operation_id);
CREATE INDEX idx_import_sessions_template_id ON import_sessions(template_id);
CREATE INDEX idx_import_sessions_status ON import_sessions(status);
CREATE INDEX idx_import_errors_session_id ON import_errors(import_session_id);
CREATE INDEX idx_import_errors_row_number ON import_errors(row_number);
CREATE INDEX idx_file_metadata_session_id ON file_metadata(import_session_id);

-- Функция для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER update_import_templates_updated_at BEFORE UPDATE
    ON import_templates FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();