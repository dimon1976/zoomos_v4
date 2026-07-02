-- V60__create_report_fetcher_tables.sql

CREATE TABLE report_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    client_id BIGINT,
    source_url TEXT NOT NULL,
    lookup_file_original_name VARCHAR(255),
    lookup_file_stored_path VARCHAR(500),
    lookup_file_format VARCHAR(10),
    lookup_file_delimiter VARCHAR(5),
    lookup_file_encoding VARCHAR(50),
    detected_report_columns TEXT,
    output_format VARCHAR(10) NOT NULL DEFAULT 'XLSX',
    row_filter_expression TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_config_client FOREIGN KEY (client_id)
        REFERENCES clients(id) ON DELETE SET NULL
);

CREATE TABLE report_output_columns (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    output_header_name VARCHAR(255) NOT NULL,
    column_order INTEGER NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    source_column_name VARCHAR(255),
    formula TEXT,
    key_column_in_report VARCHAR(255),
    key_column_in_lookup VARCHAR(255),
    value_column_in_lookup VARCHAR(255),

    CONSTRAINT fk_report_output_column_config FOREIGN KEY (config_id)
        REFERENCES report_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_output_columns_config ON report_output_columns(config_id);

CREATE TABLE report_runs (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    result_file_path VARCHAR(500),
    triggered_by VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_run_config FOREIGN KEY (config_id)
        REFERENCES report_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_runs_config ON report_runs(config_id);
CREATE INDEX idx_report_runs_status ON report_runs(status);

CREATE TABLE zoomos_auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    cookies TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
