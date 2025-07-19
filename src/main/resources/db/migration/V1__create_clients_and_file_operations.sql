-- V1__create_clients_and_file_operations.sql

-- Таблица клиентов
CREATE TABLE clients (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         description TEXT,
                         contact_email VARCHAR(255),
                         contact_phone VARCHAR(255),
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Таблица файловых операций
CREATE TABLE file_operations (
                                 id BIGSERIAL PRIMARY KEY,
                                 client_id BIGINT,
                                 operation_type VARCHAR(50) NOT NULL,
                                 file_name VARCHAR(500) NOT NULL,
                                 file_type VARCHAR(100) NOT NULL,
                                 record_count INTEGER,
                                 status VARCHAR(50) NOT NULL,
                                 error_message TEXT,
                                 started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMP WITH TIME ZONE,
                                 created_by VARCHAR(255),
                                 source_file_path TEXT,
                                 result_file_path TEXT,
                                 file_size BIGINT,
                                 total_records INTEGER,
                                 processed_records INTEGER,
                                 processing_progress INTEGER,
                                 field_mapping_id BIGINT,
                                 strategy_id BIGINT,
                                 processing_params TEXT,
                                 file_hash VARCHAR(255),
                                 CONSTRAINT fk_file_operation_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_file_operations_client_id ON file_operations(client_id);