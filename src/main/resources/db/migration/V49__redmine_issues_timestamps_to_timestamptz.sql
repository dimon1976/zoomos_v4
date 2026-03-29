-- V49: Конвертируем created_at/updated_at в TIMESTAMPTZ для согласованности с остальными Entity
ALTER TABLE zoomos_redmine_issues
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
