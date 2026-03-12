---
name: zoomos-check
description: "Show recent Zoomos Check results or list shops. Triggers: 'последние проверки для магазина', 'статус проверки', 'покажи историю проверок', 'list shops', '/zoomos-check'."
disable-model-invocation: true
argument-hint: "[shop-name | list]"
allowed-tools: Bash, Read
---

Отвечай на русском языке.

Trigger or inspect Zoomos Check for: $ARGUMENTS

Current shops in DB:
!`PGPASSWORD=root psql -U postgres -d zoomos_v4 -t -c "SELECT id, name, is_enabled, is_priority FROM zoomos_shops ORDER BY name" 2>/dev/null`

## If $ARGUMENTS is "list"
Show the shops table above. Done.

## If $ARGUMENTS is a shop name
1. Find shop ID from the list above
2. Guide user to: http://localhost:8081/zoomos (if server running)
3. Or trigger via REST: explain that check runs are started via ZoomosAnalysisController
4. Check recent runs:
`PGPASSWORD=root psql -U postgres -d zoomos_v4 -c "SELECT id, status, started_at, completed_at, ok_count, warning_count, error_count FROM zoomos_check_runs WHERE shop_id=(SELECT id FROM zoomos_shops WHERE name ILIKE '%$ARGUMENTS%' LIMIT 1) ORDER BY started_at DESC LIMIT 5"`

## Check server status first
If server is not running (check port 8081), remind: run `/server start` first.
