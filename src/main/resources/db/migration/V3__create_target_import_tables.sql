CREATE TABLE IF NOT EXISTS av_data (
                                       id BIGSERIAL PRIMARY KEY,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                       data_source VARCHAR(50),
                                       operation_id BIGINT,
                                       client_id BIGINT,
                                       product_id VARCHAR(255),
                                       product_name VARCHAR(255),
                                       product_brand VARCHAR(255),
                                       product_bar VARCHAR(255),
                                       product_description TEXT,
                                       product_url VARCHAR(1100),
                                       product_category1 VARCHAR(255),
                                       product_category2 VARCHAR(255),
                                       product_category3 VARCHAR(255),
                                       product_price VARCHAR(255),
                                       product_analog VARCHAR(255),
                                       product_additional1 VARCHAR(255),
                                       product_additional2 VARCHAR(255),
                                       product_additional3 VARCHAR(255),
                                       product_additional4 VARCHAR(255),
                                       product_additional5 VARCHAR(255),
                                       region VARCHAR(255),
                                       region_address VARCHAR(500),
                                       competitor_name VARCHAR(255),
                                       competitor_price VARCHAR(255),
                                       competitor_promotional_price VARCHAR(255),
                                       competitor_time VARCHAR(100),
                                       competitor_date VARCHAR(100),
                                       competitor_local_date_time TIMESTAMP WITHOUT TIME ZONE,
                                       competitor_stock_status VARCHAR(100),
                                       competitor_additional_price VARCHAR(255),
                                       competitor_commentary TEXT,
                                       competitor_product_name VARCHAR(255),
                                       competitor_additional VARCHAR(255),
                                       competitor_additional2 VARCHAR(255),
                                       competitor_url VARCHAR(1100),
                                       competitor_web_cache_url VARCHAR(1100),
                                       import_session_id BIGINT,
                                       CONSTRAINT fk_av_data_import_session FOREIGN KEY (import_session_id)
                                           REFERENCES import_sessions(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS av_handbook (
                                           id BIGSERIAL PRIMARY KEY,
                                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                           handbook_retail_network_code VARCHAR(255),
                                           handbook_retail_network VARCHAR(255),
                                           handbook_physical_address VARCHAR(1000),
                                           handbook_price_zone_code VARCHAR(255),
                                           handbook_web_site VARCHAR(1100),
                                           handbook_region_code VARCHAR(100),
                                           handbook_region_name VARCHAR(255),
                                           import_session_id BIGINT,
                                           CONSTRAINT fk_av_handbook_import_session FOREIGN KEY (import_session_id)
                                               REFERENCES import_sessions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_av_data_product_id          ON av_data(product_id);
CREATE INDEX IF NOT EXISTS idx_av_data_product_name        ON av_data(product_name);
CREATE INDEX IF NOT EXISTS idx_av_data_data_source         ON av_data(data_source);
CREATE INDEX IF NOT EXISTS idx_av_data_operation_id        ON av_data(operation_id);
CREATE INDEX IF NOT EXISTS idx_av_data_client_id           ON av_data(client_id);
CREATE INDEX IF NOT EXISTS idx_av_data_competitor_name     ON av_data(competitor_name);
CREATE INDEX IF NOT EXISTS idx_av_data_import_session      ON av_data(import_session_id);
CREATE INDEX IF NOT EXISTS idx_av_data_created_at          ON av_data(created_at);

CREATE INDEX IF NOT EXISTS idx_av_handbook_network_code    ON av_handbook(handbook_retail_network_code);
CREATE INDEX IF NOT EXISTS idx_av_handbook_region_code     ON av_handbook(handbook_region_code);
CREATE INDEX IF NOT EXISTS idx_av_handbook_import_session  ON av_handbook(import_session_id);
CREATE INDEX IF NOT EXISTS idx_av_handbook_created_at      ON av_handbook(created_at);

CREATE TRIGGER update_av_data_updated_at BEFORE UPDATE
    ON av_data FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_av_handbook_updated_at BEFORE UPDATE
    ON av_handbook FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE VIEW v_av_data_import_stats AS
SELECT
    ims.id            AS session_id,
    ims.template_id,
    it.name           AS template_name,
    COUNT(d.id)       AS imported_records,
    MIN(d.created_at) AS first_record_imported,
    MAX(d.created_at) AS last_record_imported
FROM import_sessions ims
         JOIN import_templates it ON ims.template_id = it.id
         LEFT JOIN av_data d ON d.import_session_id = ims.id
WHERE it.entity_type = 'AV_DATA'
GROUP BY ims.id, ims.template_id, it.name;

CREATE OR REPLACE VIEW v_av_handbook_import_stats AS
SELECT
    ims.id            AS session_id,
    ims.template_id,
    it.name           AS template_name,
    COUNT(h.id)       AS imported_records,
    MIN(h.created_at) AS first_record_imported,
    MAX(h.created_at) AS last_record_imported
FROM import_sessions ims
         JOIN import_templates it ON ims.template_id = it.id
         LEFT JOIN av_handbook h ON h.import_session_id = ims.id
WHERE it.entity_type = 'AV_HANDBOOK'
GROUP BY ims.id, ims.template_id, it.name;
