-- V17__add_data_cleanup_system.sql
-- Добавление системы очистки устаревших данных с индексами и таблицами настроек

-- =====================================================
-- ЧАСТЬ 1: Оптимизация индексов для av_data
-- =====================================================

-- Композитный индекс для фильтрации по клиенту и дате
CREATE INDEX IF NOT EXISTS idx_av_data_client_created ON av_data(client_id, created_at DESC);

-- Композитный индекс для группировки по операциям и дате
CREATE INDEX IF NOT EXISTS idx_av_data_operation_created ON av_data(operation_id, created_at DESC);

-- BRIN индекс для эффективной работы с большими временными рядами
-- BRIN индексы отлично работают для естественно упорядоченных данных (временные метки)
-- Занимают гораздо меньше места чем B-tree индексы
CREATE INDEX IF NOT EXISTS idx_av_data_created_at_brin ON av_data USING BRIN(created_at);

-- Комментарии для документации индексов
COMMENT ON INDEX idx_av_data_client_created IS 'Оптимизация поиска данных по клиенту и дате для очистки';
COMMENT ON INDEX idx_av_data_operation_created IS 'Оптимизация группировки операций по дате';
COMMENT ON INDEX idx_av_data_created_at_brin IS 'BRIN индекс для эффективной очистки больших объемов данных по дате';

-- =====================================================
-- ЧАСТЬ 2: Индексы для сессий импорта/экспорта
-- =====================================================

-- Индекс для поиска старых сессий импорта
CREATE INDEX IF NOT EXISTS idx_import_sessions_started_status ON import_sessions(started_at DESC, status);

-- Индекс для поиска старых сессий экспорта
CREATE INDEX IF NOT EXISTS idx_export_sessions_started_status ON export_sessions(started_at DESC, status);

-- Индекс для file_operations по дате и статусу
CREATE INDEX IF NOT EXISTS idx_file_operations_started_status ON file_operations(started_at DESC, status);

COMMENT ON INDEX idx_import_sessions_started_status IS 'Поиск старых сессий импорта для очистки';
COMMENT ON INDEX idx_export_sessions_started_status IS 'Поиск старых сессий экспорта для очистки';
COMMENT ON INDEX idx_file_operations_started_status IS 'Поиск старых файловых операций для очистки';

-- =====================================================
-- ЧАСТЬ 3: Таблица настроек очистки данных
-- =====================================================

CREATE TABLE data_cleanup_settings (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL DEFAULT 30,
    auto_cleanup_enabled BOOLEAN DEFAULT FALSE,
    cleanup_batch_size INTEGER DEFAULT 10000,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_retention_days_positive CHECK (retention_days > 0),
    CONSTRAINT chk_batch_size_positive CHECK (cleanup_batch_size > 0)
);

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER update_data_cleanup_settings_updated_at BEFORE UPDATE
    ON data_cleanup_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE data_cleanup_settings IS 'Настройки хранения и очистки для различных типов данных';
COMMENT ON COLUMN data_cleanup_settings.entity_type IS 'Тип данных: AV_DATA, IMPORT_SESSIONS, EXPORT_SESSIONS, IMPORT_ERRORS';
COMMENT ON COLUMN data_cleanup_settings.retention_days IS 'Количество дней хранения данных';
COMMENT ON COLUMN data_cleanup_settings.auto_cleanup_enabled IS 'Включена ли автоматическая очистка (по умолчанию выключена)';
COMMENT ON COLUMN data_cleanup_settings.cleanup_batch_size IS 'Размер порции для batch-удаления';

-- Вставляем настройки по умолчанию
INSERT INTO data_cleanup_settings (entity_type, retention_days, auto_cleanup_enabled, cleanup_batch_size, description)
VALUES
    ('AV_DATA', 30, false, 10000, 'Сырые данные из импорта - основная таблица'),
    ('IMPORT_SESSIONS', 120, false, 1000, 'Сессии импорта и связанные метаданные'),
    ('EXPORT_SESSIONS', 730, false, 1000, 'Сессии экспорта - хранятся долго для статистики'),
    ('IMPORT_ERRORS', 60, false, 5000, 'Ошибки импорта'),
    ('FILE_OPERATIONS', 120, false, 1000, 'Файловые операции (COMPLETED/FAILED)')
ON CONFLICT (entity_type) DO NOTHING;

-- =====================================================
-- ЧАСТЬ 4: Таблица истории очистки данных
-- =====================================================

CREATE TABLE data_cleanup_history (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    cleanup_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cutoff_date TIMESTAMP WITH TIME ZONE NOT NULL,
    records_deleted BIGINT NOT NULL DEFAULT 0,
    execution_time_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    initiated_by VARCHAR(255),
    batch_size INTEGER,
    excluded_client_ids TEXT,

    CONSTRAINT chk_cleanup_status CHECK (status IN ('SUCCESS', 'FAILED', 'PARTIAL'))
);

-- Индексы для быстрого поиска истории
CREATE INDEX idx_cleanup_history_entity_type ON data_cleanup_history(entity_type);
CREATE INDEX idx_cleanup_history_cleanup_date ON data_cleanup_history(cleanup_date DESC);
CREATE INDEX idx_cleanup_history_status ON data_cleanup_history(status);

COMMENT ON TABLE data_cleanup_history IS 'История выполнения операций очистки данных';
COMMENT ON COLUMN data_cleanup_history.entity_type IS 'Тип очищенных данных';
COMMENT ON COLUMN data_cleanup_history.cutoff_date IS 'Дата, до которой были удалены данные';
COMMENT ON COLUMN data_cleanup_history.records_deleted IS 'Количество удаленных записей';
COMMENT ON COLUMN data_cleanup_history.execution_time_ms IS 'Время выполнения в миллисекундах';
COMMENT ON COLUMN data_cleanup_history.initiated_by IS 'Кто инициировал очистку (пользователь или система)';
COMMENT ON COLUMN data_cleanup_history.excluded_client_ids IS 'JSON массив ID клиентов, данные которых не удалялись';

-- =====================================================
-- ЧАСТЬ 5: Представление для мониторинга размеров таблиц
-- =====================================================

CREATE OR REPLACE VIEW v_data_cleanup_stats AS
SELECT
    'av_data' AS table_name,
    COUNT(*) AS total_records,
    MIN(created_at) AS oldest_record,
    MAX(created_at) AS newest_record,
    pg_size_pretty(pg_total_relation_size('av_data')) AS table_size,
    pg_total_relation_size('av_data') AS table_size_bytes
FROM av_data
UNION ALL
SELECT
    'av_handbook' AS table_name,
    COUNT(*) AS total_records,
    MIN(created_at) AS oldest_record,
    MAX(created_at) AS newest_record,
    pg_size_pretty(pg_total_relation_size('av_handbook')) AS table_size,
    pg_total_relation_size('av_handbook') AS table_size_bytes
FROM av_handbook
UNION ALL
SELECT
    'import_sessions' AS table_name,
    COUNT(*) AS total_records,
    MIN(started_at) AS oldest_record,
    MAX(started_at) AS newest_record,
    pg_size_pretty(pg_total_relation_size('import_sessions')) AS table_size,
    pg_total_relation_size('import_sessions') AS table_size_bytes
FROM import_sessions
UNION ALL
SELECT
    'export_sessions' AS table_name,
    COUNT(*) AS total_records,
    MIN(started_at) AS oldest_record,
    MAX(started_at) AS newest_record,
    pg_size_pretty(pg_total_relation_size('export_sessions')) AS table_size,
    pg_total_relation_size('export_sessions') AS table_size_bytes
FROM export_sessions
UNION ALL
SELECT
    'import_errors' AS table_name,
    COUNT(*) AS total_records,
    MIN(occurred_at) AS oldest_record,
    MAX(occurred_at) AS newest_record,
    pg_size_pretty(pg_total_relation_size('import_errors')) AS table_size,
    pg_total_relation_size('import_errors') AS table_size_bytes
FROM import_errors;

COMMENT ON VIEW v_data_cleanup_stats IS 'Статистика размеров таблиц для мониторинга необходимости очистки';

-- =====================================================
-- ЧАСТЬ 6: Обновление настроек в application.properties
-- =====================================================

-- Добавляем комментарий с рекомендуемыми настройками для application.properties:
-- database.maintenance.statistics.retention-days=730
-- database.maintenance.raw-data.retention-days=30
-- maintenance.scheduler.raw-data-cleanup.cron=0 0 4 * * *
-- maintenance.scheduler.raw-data-cleanup.enabled=false
