-- Настройки планировщика обслуживания (хранятся в zoomos_settings как key-value)
INSERT INTO zoomos_settings (key, value, description) VALUES
    ('maint.enabled',                  'false',         'Глобальное включение планировщика обслуживания'),
    ('maint.fileArchive.enabled',      'true',          'Архивирование файлов включено'),
    ('maint.fileArchive.cron',         '0 0 2 * * *',   'Cron архивирования файлов (Spring 6-field: сек мин час день мес день_нед)'),
    ('maint.fileArchive.lastRunAt',    '',               'Последний запуск архивирования'),
    ('maint.dbCleanup.enabled',        'true',          'Очистка БД включена'),
    ('maint.dbCleanup.cron',           '0 0 3 * * SUN', 'Cron очистки БД'),
    ('maint.dbCleanup.lastRunAt',      '',               'Последний запуск очистки БД'),
    ('maint.healthCheck.enabled',      'true',          'Проверка здоровья системы включена'),
    ('maint.healthCheck.cron',         '0 0 * * * *',   'Cron проверки здоровья'),
    ('maint.healthCheck.lastRunAt',    '',               'Последний запуск проверки здоровья'),
    ('maint.perfAnalysis.enabled',     'true',          'Анализ производительности включён'),
    ('maint.perfAnalysis.cron',        '0 0 1 * * MON', 'Cron анализа производительности'),
    ('maint.perfAnalysis.lastRunAt',   '',               'Последний запуск анализа'),
    ('maint.fullMaintenance.enabled',  'true',          'Полное обслуживание включено'),
    ('maint.fullMaintenance.cron',     '0 0 4 1 * *',   'Cron полного обслуживания'),
    ('maint.fullMaintenance.lastRunAt','',               'Последний запуск полного обслуживания')
ON CONFLICT (key) DO NOTHING;
