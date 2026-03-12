---
name: flyway
description: "Quickly create next numbered Flyway migration file (V*.sql) for Zoomos v4. Use for simple migrations: new tables, columns, indexes, constraints. For complex migrations requiring DB analysis use database-maintenance-expert agent instead. Triggers: '/flyway', 'создай миграцию', 'новая миграция', 'нужен SQL файл миграции'."
disable-model-invocation: true
argument-hint: "[migration description]"
allowed-tools: Read, Glob, Write
---

Отвечай на русском языке.

Create next Flyway migration for Zoomos v4.

Current migrations:
!`ls -1 /e/workspace/zoomos_v4/src/main/resources/db/migration/ 2>/dev/null | grep "^V" | sort -V | tail -5`

## Steps

1. Find next version: parse last V{N}__ filename, increment N
2. If $ARGUMENTS provided, use as description (convert to snake_case)
   Else ask: "What does this migration do?"
3. File: `src/main/resources/db/migration/V{N+1}__{snake_case_description}.sql`

## SQL Template
```sql
-- V{N}: {Human readable description}
-- {today's date}

{SQL here}
```

## SQL Guidelines
- Use `IF NOT EXISTS` for safety
- New tables: include `id BIGSERIAL PRIMARY KEY, created_at TIMESTAMP DEFAULT NOW()`
- New columns: include `DEFAULT value` if NOT NULL
- Indexes: `CREATE INDEX IF NOT EXISTS idx_{table}_{column} ON {table}({column})`
- Follow existing V*.sql patterns in the project

After creating, show file path and full SQL for review.
Note: restart server or run `mvn flyway:migrate` to apply.
