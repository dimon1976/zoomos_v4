-- V5__add_data_source_type_to_import_templates.sql

ALTER TABLE import_templates
    ADD COLUMN data_source_type VARCHAR(50) NOT NULL DEFAULT 'FILE';