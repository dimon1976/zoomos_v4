-- V7__add_statistics_settings_to_export_templates.sql

-- Добавляем поля для настройки статистики в шаблоны экспорта
ALTER TABLE export_templates
    ADD COLUMN enable_statistics BOOLEAN DEFAULT FALSE,
    ADD COLUMN statistics_count_fields TEXT, -- JSON массив полей для подсчета
    ADD COLUMN statistics_group_field VARCHAR(255), -- Поле для группировки
    ADD COLUMN statistics_filter_fields TEXT; -- JSON массив полей для фильтрации

-- Таблица для хранения результатов статистики
CREATE TABLE export_statistics (
                                   id BIGSERIAL PRIMARY KEY,
                                   export_session_id BIGINT NOT NULL,
                                   group_field_name VARCHAR(255) NOT NULL,
                                   group_field_value VARCHAR(255) NOT NULL,
                                   count_field_name VARCHAR(255) NOT NULL,
                                   count_value BIGINT NOT NULL DEFAULT 0,
                                   filter_conditions TEXT, -- JSON условий фильтрации
                                   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_export_statistics_session FOREIGN KEY (export_session_id)
                                       REFERENCES export_sessions(id) ON DELETE CASCADE
);

-- Индексы для быстрого поиска
CREATE INDEX idx_export_statistics_session ON export_statistics(export_session_id);
CREATE INDEX idx_export_statistics_group ON export_statistics(group_field_name, group_field_value);
CREATE INDEX idx_export_statistics_count_field ON export_statistics(count_field_name);

-- Таблица для настроек отклонений (глобальные настройки)
CREATE TABLE statistics_settings (
                                     id BIGSERIAL PRIMARY KEY,
                                     setting_key VARCHAR(100) NOT NULL UNIQUE,
                                     setting_value VARCHAR(255) NOT NULL,
                                     description TEXT,
                                     updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Вставляем настройки по умолчанию
INSERT INTO statistics_settings (setting_key, setting_value, description) VALUES
                                                                              ('deviation_percentage_warning', '10', 'Процент отклонения для предупреждения (желтый)'),
                                                                              ('deviation_percentage_critical', '20', 'Процент отклонения для критического уровня (красный)'),
                                                                              ('statistics_max_operations', '10', 'Максимальное количество операций для сравнения');