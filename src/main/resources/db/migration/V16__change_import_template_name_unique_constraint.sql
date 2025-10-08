-- V16__change_import_template_name_unique_constraint.sql
-- Изменяет ограничение уникальности имени шаблона импорта:
-- С глобального (только name) на уникальность в рамках клиента (name, client_id)

-- Удаляем старое глобальное ограничение уникальности на поле name
ALTER TABLE import_templates DROP CONSTRAINT IF EXISTS import_templates_name_key;

-- Добавляем новое составное ограничение уникальности на пару (name, client_id)
-- Теперь разные клиенты могут иметь шаблоны с одинаковыми именами
ALTER TABLE import_templates ADD CONSTRAINT uk_import_template_name_client UNIQUE (name, client_id);
