-- V8__improve_export_statistics_indices.sql

-- Улучшаем индексы для быстрого поиска статистики
DROP INDEX IF EXISTS idx_export_statistics_session;
DROP INDEX IF EXISTS idx_export_statistics_group;
DROP INDEX IF EXISTS idx_export_statistics_count_field;

-- Создаем составные индексы для оптимизации запросов
CREATE INDEX idx_export_statistics_session_group ON export_statistics(export_session_id, group_field_value);
CREATE INDEX idx_export_statistics_session_field ON export_statistics(export_session_id, count_field_name);
CREATE INDEX idx_export_statistics_group_field ON export_statistics(group_field_name, group_field_value, count_field_name);

-- Индекс для быстрого поиска по нескольким сессиям
CREATE INDEX idx_export_statistics_multi_session ON export_statistics(export_session_id, group_field_value, count_field_name);

-- Комментарии для документации
COMMENT ON INDEX idx_export_statistics_session_group IS 'Для поиска статистики по сессии и группе';
COMMENT ON INDEX idx_export_statistics_session_field IS 'Для поиска статистики по сессии и полю';
COMMENT ON INDEX idx_export_statistics_group_field IS 'Для поиска статистики по группе и полю';
COMMENT ON INDEX idx_export_statistics_multi_session IS 'Для сравнения статистики между сессиями';