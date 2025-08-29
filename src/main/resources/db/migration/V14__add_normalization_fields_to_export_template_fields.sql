-- Добавление полей нормализации данных к таблице export_template_fields

-- Добавляем поле для типа нормализации
ALTER TABLE export_template_fields 
ADD COLUMN normalization_type VARCHAR(50);

-- Добавляем поле для правил нормализации (JSON)
ALTER TABLE export_template_fields 
ADD COLUMN normalization_rule TEXT;

-- Добавляем флаг включения нормализации
ALTER TABLE export_template_fields 
ADD COLUMN normalization_enabled BOOLEAN DEFAULT FALSE;

-- Добавляем комментарии к новым полям
COMMENT ON COLUMN export_template_fields.normalization_type IS 'Тип нормализации: VOLUME, BRAND, CURRENCY, CUSTOM';
COMMENT ON COLUMN export_template_fields.normalization_rule IS 'JSON с правилами нормализации для конкретного типа';
COMMENT ON COLUMN export_template_fields.normalization_enabled IS 'Флаг включения нормализации для этого поля';

-- Создаем индекс для быстрого поиска по полям с включенной нормализацией
CREATE INDEX idx_export_template_fields_normalization 
ON export_template_fields(normalization_enabled, normalization_type) 
WHERE normalization_enabled = TRUE;