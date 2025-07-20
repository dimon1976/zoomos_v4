-- V3__create_target_import_tables.sql

-- Таблица продуктов
CREATE TABLE IF NOT EXISTS products (
                                        id BIGSERIAL PRIMARY KEY,
                                        name VARCHAR(255) NOT NULL,
                                        description TEXT,
                                        sku VARCHAR(100) UNIQUE,
                                        price DECIMAL(10, 2),
                                        quantity INTEGER DEFAULT 0,
                                        category VARCHAR(100),
                                        brand VARCHAR(100),
                                        weight DECIMAL(10, 3),
                                        dimensions VARCHAR(100),
                                        import_session_id BIGINT,
                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_product_import_session FOREIGN KEY (import_session_id)
                                            REFERENCES import_sessions(id) ON DELETE SET NULL
);

-- Таблица покупателей/заказчиков
CREATE TABLE IF NOT EXISTS customers (
                                         id BIGSERIAL PRIMARY KEY,
                                         name VARCHAR(255) NOT NULL,
                                         email VARCHAR(255) UNIQUE,
                                         phone VARCHAR(50),
                                         address TEXT,
                                         city VARCHAR(100),
                                         country VARCHAR(100),
                                         postal_code VARCHAR(20),
                                         company VARCHAR(255),
                                         notes TEXT,
                                         import_session_id BIGINT,
                                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                         CONSTRAINT fk_customer_import_session FOREIGN KEY (import_session_id)
                                             REFERENCES import_sessions(id) ON DELETE SET NULL
);

-- Индексы для продуктов
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_import_session ON products(import_session_id);
CREATE INDEX idx_products_created_at ON products(created_at);

-- Индексы для покупателей
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_company ON customers(company);
CREATE INDEX idx_customers_import_session ON customers(import_session_id);
CREATE INDEX idx_customers_created_at ON customers(created_at);

-- Триггеры для автоматического обновления updated_at
CREATE TRIGGER update_products_updated_at BEFORE UPDATE
    ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customers_updated_at BEFORE UPDATE
    ON customers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Добавляем примеры данных для тестирования (опционально)
-- INSERT INTO products (name, description, sku, price, quantity, category, brand)
-- VALUES
--     ('Тестовый продукт 1', 'Описание продукта 1', 'TEST-001', 99.99, 10, 'Категория 1', 'Бренд 1'),
--     ('Тестовый продукт 2', 'Описание продукта 2', 'TEST-002', 149.99, 5, 'Категория 2', 'Бренд 2');

-- Представление для статистики импорта по продуктам
CREATE OR REPLACE VIEW v_product_import_stats AS
SELECT
    is.id as session_id,
    is.template_id,
    it.name as template_name,
    COUNT(p.id) as imported_products,
    MIN(p.created_at) as first_product_imported,
    MAX(p.created_at) as last_product_imported
FROM import_sessions is
         JOIN import_templates it ON is.template_id = it.id
         LEFT JOIN products p ON p.import_session_id = is.id
WHERE it.entity_type = 'PRODUCT'
GROUP BY is.id, is.template_id, it.name;

-- Представление для статистики импорта по покупателям
CREATE OR REPLACE VIEW v_customer_import_stats AS
SELECT
    is.id as session_id,
    is.template_id,
    it.name as template_name,
    COUNT(c.id) as imported_customers,
    MIN(c.created_at) as first_customer_imported,
    MAX(c.created_at) as last_customer_imported
FROM import_sessions is
         JOIN import_templates it ON is.template_id = it.id
         LEFT JOIN customers c ON c.import_session_id = is.id
WHERE it.entity_type = 'CUSTOMER'
GROUP BY is.id, is.template_id, it.name;

-- Обновление application.properties - добавьте эти настройки:
-- # Настройки импорта
-- import.async.core-pool-size=2
-- import.async.max-pool-size=4
-- import.async.queue-capacity=100
-- import.async.thread-name-prefix=ImportExecutor-
--
-- # Параметры обработки
-- import.batch-size=1000
-- import.max-memory-percentage=60
-- import.file-analysis.sample-rows=100
-- import.timeout-minutes=60
--
-- # Директории для файлов
-- application.upload.dir=data/upload
-- application.export.dir=data/upload/exports
-- application.import.dir=data/upload/imports
-- application.temp.dir=data/temp