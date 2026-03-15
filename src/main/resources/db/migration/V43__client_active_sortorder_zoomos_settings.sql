-- V43: клиенты is_active + sort_order, настройки Zoomos Check

-- 1. Клиенты: признак активности и порядок сортировки
ALTER TABLE clients ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE clients ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
UPDATE clients SET sort_order = id;

-- 2. Глобальные настройки Zoomos Check (key-value)
CREATE TABLE zoomos_settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       VARCHAR(255) NOT NULL,
    description VARCHAR(500)
);

INSERT INTO zoomos_settings (key, value, description) VALUES
    ('default.drop_threshold',         '10',  'Порог падения inStock, %'),
    ('default.error_growth_threshold', '30',  'Порог роста ошибок, %'),
    ('default.baseline_days',          '7',   'Дней для baseline-анализа'),
    ('default.min_absolute_errors',    '5',   'Мин. ошибок для срабатывания WARNING'),
    ('default.trend_drop_threshold',   '30',  'Тренд: порог падения, %'),
    ('default.trend_error_threshold',  '100', 'Тренд: порог роста ошибок, %');
