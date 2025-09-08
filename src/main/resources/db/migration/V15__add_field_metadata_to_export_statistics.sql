-- =================================================================
-- V15: Добавление метаданных полей к статистике экспорта
-- =================================================================
-- Цель: Подготовить БД для хранения метаданных полей для продвинутых фильтров
-- Дата: 09.09.2025
-- Автор: Claude Code Assistant

-- Добавление колонки field_metadata типа JSONB для хранения метаданных полей
ALTER TABLE export_statistics 
ADD COLUMN field_metadata JSONB DEFAULT '{}' NOT NULL;

-- Добавление комментария к новой колонке
COMMENT ON COLUMN export_statistics.field_metadata IS 
'Метаданные полей для продвинутой фильтрации. Содержит информацию о доступных значениях полей, их типах и другие метаданные для построения динамических фильтров';

-- Создание индекса для быстрого поиска по метаданным полей
-- Использование GIN индекса для эффективного поиска в JSONB данных
CREATE INDEX idx_export_statistics_field_metadata_gin 
ON export_statistics USING GIN (field_metadata);

-- Дополнительный индекс для поиска по конкретным ключам метаданных
-- Это позволит быстро находить записи с определенными полями метаданных
CREATE INDEX idx_export_statistics_field_metadata_keys 
ON export_statistics USING GIN ((field_metadata -> 'availableValues'));

-- Обновление существующих записей с пустыми метаданными (уже установлено через DEFAULT)
-- Это безопасная операция, так как DEFAULT уже применился

-- Создание частичного индекса для записей с непустыми метаданными
-- Оптимизирует поиск только среди записей, которые действительно имеют метаданные
CREATE INDEX idx_export_statistics_non_empty_metadata 
ON export_statistics (id) 
WHERE field_metadata != '{}';

-- Добавление проверочного ограничения для валидации JSON структуры
-- Убеждаемся, что field_metadata всегда содержит валидный JSON
ALTER TABLE export_statistics 
ADD CONSTRAINT chk_field_metadata_is_json 
CHECK (field_metadata IS NOT NULL AND jsonb_typeof(field_metadata) = 'object');