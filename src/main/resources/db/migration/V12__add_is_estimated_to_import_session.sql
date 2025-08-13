-- V999__add_is_estimated_to_import_session.sql
ALTER TABLE import_sessions ADD COLUMN is_estimated BOOLEAN DEFAULT FALSE;