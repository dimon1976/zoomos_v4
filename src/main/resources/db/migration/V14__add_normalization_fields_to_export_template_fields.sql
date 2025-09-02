-- V14__add_normalization_fields_to_export_template_fields.sql
-- Добавляем поля нормализации в таблицу export_template_fields

ALTER TABLE export_template_fields 
ADD COLUMN normalization_type VARCHAR(50);

ALTER TABLE export_template_fields 
ADD COLUMN normalization_rule VARCHAR(1000);

-- Создаем индекс для быстрого поиска полей с нормализацией
CREATE INDEX idx_export_template_fields_normalization_type 
ON export_template_fields(normalization_type);