-- V15: Добавление полей для поддержки фильтрации по дополнительным полям в статистике экспорта

-- Добавляем новые поля для фильтрации
ALTER TABLE export_statistics
    ADD COLUMN filter_field_name VARCHAR(255),
    ADD COLUMN filter_field_value VARCHAR(255);

-- Добавляем индекс для оптимизации поиска по фильтру
CREATE INDEX idx_export_statistics_filter
    ON export_statistics(export_session_id, filter_field_name, filter_field_value);

-- Комментарии к полям
COMMENT ON COLUMN export_statistics.filter_field_name IS 'Название поля фильтрации (например, competitorStockStatus). NULL означает общую статистику без фильтра';
COMMENT ON COLUMN export_statistics.filter_field_value IS 'Значение фильтра (например, "В наличии"). NULL означает общую статистику без фильтра';
