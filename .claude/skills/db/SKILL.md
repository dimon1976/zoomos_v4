---
name: db
description: "Run quick PostgreSQL queries against zoomos_v4. Triggers: 'посмотри в БД', 'сколько записей', 'покажи данные из таблицы', 'запрос к базе', 'проверь таблицу'. Aliases: clients, shops, av_data, operations, migrations, vacuum, schedules, redmine, bloat."
disable-model-invocation: true
argument-hint: "[SQL query | alias: clients|shops|av_data|operations|migrations|vacuum|schedules|redmine|bloat]"
allowed-tools: Bash
---

Run psql queries against zoomos_v4 (postgres:root@127.0.0.1:5432).

## If $ARGUMENTS is a SQL query
`PGPASSWORD=root psql -U postgres -d zoomos_v4 -c "$ARGUMENTS"`

## Aliases (shortcuts)
- `clients`    → `SELECT id, name, region FROM clients ORDER BY name`
- `shops`      → `SELECT id, name, is_enabled, is_priority FROM zoomos_shops ORDER BY name`
- `av_data`    → `SELECT COUNT(*) as total FROM av_data`
- `operations` → `SELECT id, status, entity_type, created_at FROM file_operations ORDER BY created_at DESC LIMIT 20`
- `migrations` → `SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10`
- `vacuum`     → `SELECT schemaname, tablename, last_vacuum, last_autovacuum, n_dead_tup, n_live_tup FROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 15`
- `schedules`  → `SELECT ss.*, sh.name as shop_name FROM zoomos_shop_schedules ss JOIN zoomos_shops sh ON ss.shop_id=sh.id`
- `redmine`    → `SELECT site_name, issue_id, issue_status, is_closed, updated_at FROM zoomos_redmine_issues ORDER BY updated_at DESC LIMIT 20`
- `bloat`      → `SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size FROM pg_tables WHERE schemaname='public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC LIMIT 10`

Format output cleanly. If result is empty, say so explicitly.
