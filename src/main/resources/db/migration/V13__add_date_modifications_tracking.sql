-- V13__add_date_modifications_tracking.sql

-- Добавляем поля для отслеживания изменений дат в статистику экспорта
ALTER TABLE export_statistics
    ADD COLUMN date_modifications_count BIGINT DEFAULT 0,
    ADD COLUMN total_records_count BIGINT DEFAULT 0,
    ADD COLUMN modification_type VARCHAR(50) DEFAULT 'STANDARD';

-- Добавляем поле для настройки фильтруемых полей в шаблоны экспорта  
ALTER TABLE export_templates
    ADD COLUMN filterable_fields TEXT; -- JSON массив полей доступных для фильтрации

-- Комментарии к новым полям
COMMENT ON COLUMN export_statistics.date_modifications_count IS 'Количество записей с измененными датами (для TASK_REPORT стратегии)';
COMMENT ON COLUMN export_statistics.total_records_count IS 'Общее количество записей в группе для расчета процента изменений';
COMMENT ON COLUMN export_statistics.modification_type IS 'Тип изменения: STANDARD (обычная статистика) или DATE_ADJUSTMENT (корректировка дат)';
COMMENT ON COLUMN export_templates.filterable_fields IS 'JSON массив полей entity доступных для фильтрации в статистике';

-- Обновляем существующие записи статистики (устанавливаем total_records_count = count_value для обычной статистики)
UPDATE export_statistics 
SET total_records_count = count_value, 
    modification_type = 'STANDARD'
WHERE total_records_count = 0;

-- Добавляем индекс для быстрого поиска статистики с изменениями дат
CREATE INDEX idx_export_statistics_modification_type ON export_statistics(modification_type);