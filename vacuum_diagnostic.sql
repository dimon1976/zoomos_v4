-- Диагностика проблемы с VACUUM
-- Скрипт для понимания, почему таблицы всё ещё показывают мёртвые кортежи

-- 1. Проверка текущей статистики по таблицам (данные из pg_stat_user_tables)
SELECT
    schemaname || '.' || relname as table_name,
    n_live_tup as live_tuples,
    n_dead_tup as dead_tuples,
    CASE
        WHEN n_live_tup + n_dead_tup > 0
        THEN ROUND((n_dead_tup::numeric / (n_live_tup + n_dead_tup) * 100), 2)
        ELSE 0
    END as dead_pct,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND n_dead_tup > 0
ORDER BY n_dead_tup DESC;

-- 2. Проверка настроек autovacuum для таблиц
SELECT
    schemaname || '.' || relname as table_name,
    n_live_tup,
    n_dead_tup,
    CASE
        WHEN n_live_tup + n_dead_tup > 0
        THEN ROUND((n_dead_tup::numeric / (n_live_tup + n_dead_tup) * 100), 2)
        ELSE 0
    END as dead_pct,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||relname)) as total_size
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND (n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0) * 100) > 10
ORDER BY dead_pct DESC;

-- 3. Проверка активных и долгоживущих транзакций (могут блокировать VACUUM)
SELECT
    pid,
    usename,
    application_name,
    state,
    age(clock_timestamp(), xact_start) as transaction_age,
    age(clock_timestamp(), query_start) as query_age,
    query
FROM pg_stat_activity
WHERE state != 'idle'
  AND pid != pg_backend_pid()
ORDER BY xact_start NULLS LAST;

-- 4. Проверка размера таблиц и bloat
SELECT
    t.schemaname || '.' || t.tablename as table_name,
    pg_size_pretty(pg_total_relation_size(t.schemaname||'.'||t.tablename)) as total_size,
    pg_size_pretty(pg_relation_size(t.schemaname||'.'||t.tablename)) as table_size,
    pg_size_pretty(pg_total_relation_size(t.schemaname||'.'||t.tablename) - pg_relation_size(t.schemaname||'.'||t.tablename)) as indexes_size,
    s.n_live_tup,
    s.n_dead_tup,
    CASE
        WHEN s.n_live_tup + s.n_dead_tup > 0
        THEN ROUND((s.n_dead_tup::numeric / (s.n_live_tup + s.n_dead_tup) * 100), 2)
        ELSE 0
    END as dead_pct
FROM pg_tables t
LEFT JOIN pg_stat_user_tables s ON s.relname = t.tablename AND s.schemaname = t.schemaname
WHERE t.schemaname = 'public'
  AND pg_relation_size(t.schemaname||'.'||t.tablename) > 1048576  -- более 1MB
ORDER BY pg_total_relation_size(t.schemaname||'.'||t.tablename) DESC
LIMIT 15;

-- 5. Проверка глобальных настроек autovacuum
SHOW autovacuum;
SHOW autovacuum_vacuum_threshold;
SHOW autovacuum_vacuum_scale_factor;
SHOW autovacuum_analyze_threshold;
SHOW autovacuum_analyze_scale_factor;

-- 6. Информация о последнем VACUUM для критических таблиц
SELECT
    schemaname || '.' || relname as table_name,
    GREATEST(last_vacuum, last_autovacuum) as last_vacuum_time,
    GREATEST(last_analyze, last_autoanalyze) as last_analyze_time,
    n_dead_tup,
    n_live_tup
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND relname IN ('clients', 'export_templates', 'file_operations', 'import_sessions',
                   'export_sessions', 'export_statistics', 'import_templates',
                   'import_template_fields', 'file_metadata', 'export_template_filters')
ORDER BY n_dead_tup DESC;
