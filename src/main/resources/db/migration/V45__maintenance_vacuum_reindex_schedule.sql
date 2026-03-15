-- Настройки расписания для VACUUM FULL и REINDEX
INSERT INTO zoomos_settings (key, value, description) VALUES
    ('maint.vacuum.enabled',    'false',         'VACUUM FULL по расписанию'),
    ('maint.vacuum.cron',       '0 0 3 * * SUN', 'Cron VACUUM FULL (воскресенье 03:00)'),
    ('maint.vacuum.lastRunAt',  '',              'Последний запуск VACUUM'),
    ('maint.reindex.enabled',   'false',         'REINDEX по расписанию'),
    ('maint.reindex.cron',      '0 0 4 * * SUN', 'Cron REINDEX (воскресенье 04:00)'),
    ('maint.reindex.lastRunAt', '',              'Последний запуск REINDEX')
ON CONFLICT (key) DO NOTHING;
