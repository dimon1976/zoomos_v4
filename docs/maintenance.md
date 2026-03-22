# Система обслуживания (Maintenance)

Автоматизированное и ручное обслуживание системы: управление файлами, очистка БД, диагностика, расписание задач.

## URL-маршруты

| URL | Метод | Назначение |
|-----|-------|-----------|
| `/maintenance` | GET | Главный дашборд обслуживания |
| `/maintenance/config` | GET | Экспорт/Импорт конфигурации |
| `/maintenance/config/export` | POST | Скачать JSON-файл конфигурации |
| `/maintenance/config/preview` | POST | Анализ JSON-файла без сохранения (возвращает `ConfigImportPreviewDto`) |
| `/maintenance/config/import` | POST | Выполнить импорт из JSON-файла (возвращает `ConfigImportResultDto`) |
| `/maintenance/files` | GET | Управление файлами |
| `/maintenance/files/archives` | GET | JSON: список ZIP-архивов |
| `/maintenance/database` | GET | Обслуживание БД |
| `/maintenance/schedule` | GET | Страница расписания задач |
| `/maintenance/schedule/save` | POST | Сохранить настройки расписания |
| `/maintenance/schedule/trigger/{key}` | POST | Ручной запуск задачи |
| `/maintenance/cleanup` | POST | Полная очистка системы |
| `/maintenance/system/health` | GET | JSON: состояние системы |
| `/maintenance/system/resources` | GET | JSON: ресурсы системы |

## Расписание задач

### Хранение настроек

Расписание хранится в таблице `zoomos_settings` (ключи `maint.*`). Изменения применяются без перезапуска.

| Ключ | Тип | Описание |
|------|-----|---------|
| `maint.{key}.enabled` | boolean | Включение конкретной задачи |
| `maint.{key}.cron` | string | Cron-выражение (6 полей). День недели: 1=Вс, 2=Пн, 3=Вт, 4=Ср, 5=Чт, 6=Пт, 7=Сб |
| `maint.{key}.lastRunAt` | string | Дата/время последнего запуска (`yyyy-MM-dd HH:mm:ss`) |

### Задачи планировщика

| Ключ | Название | Cron по умолчанию |
|------|----------|-----------------|
| `fileArchive` | Архивирование файлов | `0 0 2 * * *` (ежедневно 02:00) |
| `dbCleanup` | Очистка базы данных | `0 0 3 * * SUN` (воскресенье 03:00) |
| `healthCheck` | Проверка здоровья системы | `0 0 * * * *` (ежечасно) |
| `perfAnalysis` | Анализ производительности | `0 0 1 * * MON` (понедельник 01:00) |
| `vacuum` | VACUUM FULL (дефрагментация) | `0 0 3 * * SUN` (воскресенье 03:00) |
| `reindex` | REINDEX (перестройка индексов) | `0 0 4 * * SUN` (воскресенье 04:00) |
| `fullMaintenance` | Полное обслуживание | `0 0 4 1 * *` (1-е число месяца 04:00) |

### Ключевые классы

| Класс | Назначение |
|-------|-----------|
| [MaintenanceSchedulerService.java](../src/main/java/com/java/service/MaintenanceSchedulerService.java) | Динамическое планирование, `rescheduleAll()`, `triggerTask()` |
| [MaintenanceController.java](../src/main/java/com/java/controller/MaintenanceController.java) | Все `/maintenance/*` роуты |
| [DataCleanupService.java](../src/main/java/com/java/service/maintenance/DataCleanupService.java) | Очистка данных, батчи, VACUUM |
| [FileManagementService.java](../src/main/java/com/java/service/maintenance/FileManagementService.java) | Архивирование файлов |
| [DatabaseMaintenanceService.java](../src/main/java/com/java/service/maintenance/DatabaseMaintenanceService.java) | Анализ производительности запросов |
| [SystemHealthService.java](../src/main/java/com/java/service/maintenance/SystemHealthService.java) | Мониторинг CPU/RAM/диска |
| [FileUtils.java](../src/main/java/com/java/util/FileUtils.java) | `formatBytes()` — форматирование размеров |

## Схема данных (Flyway V44)

```sql
-- Настройки расписания хранятся в zoomos_settings (V43+)
-- Пример ключей:
SELECT key, value FROM zoomos_settings WHERE key LIKE 'maint.%';
```

## Ключевая логика

### Динамическое перепланирование

```
POST /maintenance/schedule/save
  → settingsService.saveAll(map)          -- ON CONFLICT DO UPDATE
  → maintenanceSchedulerService.rescheduleAll()
      → читает maint.* из zoomos_settings
      → unschedule старых задач
      → schedule новых (toSpringCron + CronTrigger)
```

### Ручной запуск задачи

```
POST /maintenance/schedule/trigger/{key}
  → maintenanceSchedulerService.triggerTask(key)
  → taskScheduler.execute(getTaskRunnable(key))  -- async, не блокирует HTTP
  → recordLastRun(key)                           -- обновляет maint.{key}.lastRunAt
```

### toSpringCron()

Поддерживает оба формата — 5-польный Unix и 6-польный Spring:
```
"0 2 * * *"    → "0 0 2 * * *"  (добавляется "0 " секунды)
"0 0 2 * * *"  → "0 0 2 * * *"  (без изменений)
```

### Очистка данных (DataCleanupService)

- Удаление по умолчанию: записи старше 120 дней (`database.maintenance.cleanup.old-data.days`)
- Батчи по `batchSize` записей (без ограничения сверху)
- Rollback на уровне батча
- Auto-VACUUM после удаления ≥ 1M записей

### Утилита FileUtils

`FileUtils.formatBytes(long bytes)` — единая точка форматирования байтов в читаемый вид.
Используется в: `DataCleanupService`, `DatabaseMaintenanceService`, `FileManagementService`.

## Экспорт/Импорт конфигурации

Страница `/maintenance/config` позволяет переносить настройки между серверами (dev → prod и обратно).

### Что экспортируется (опционально)

| Секция | Сущности |
|--------|---------|
| `clients` | `Client` + `ImportTemplate` + `ExportTemplate` (с полями и фильтрами) |
| `zoomosShops` | `ZoomosShop` + `ZoomosCityId` |
| `schedules` | `ZoomosShopSchedule` (при импорте всегда `isEnabled=false`) |
| `knownSites` | `ZoomosKnownSite` |
| `cityDirectory` | `ZoomosCityName` + `ZoomosCityAddress` |

### Стратегия импорта

**Upsert по бизнес-ключу** (без полного удаления и пересоздания):
- `Client` → по `name` (case-insensitive)
- `ImportTemplate` / `ExportTemplate` → по `(clientName, templateName)`
- `ZoomosShop` → по `shopName`
- `ZoomosCityId` → по `(shopId, siteName)`
- `ZoomosShopSchedule` → по `(shopId, label)`
- `ZoomosKnownSite` → по `siteName`
- `ZoomosCityName` → по `cityId`

### Универсальность

Все DTO помечены `@JsonIgnoreProperties(ignoreUnknown = true)`:
- Старый JSON-файл (без новых полей) → новые поля получат значение `null` / дефолт из Entity.
- Новое поле в Entity → достаточно добавить его в DTO, старые файлы продолжат работать.

### Ключевые классы

| Класс | Назначение |
|-------|-----------|
| [ConfigImportExportController.java](../src/main/java/com/java/controller/ConfigImportExportController.java) | 3 эндпоинта: page, export, preview, import |
| [ConfigExportService.java](../src/main/java/com/java/service/maintenance/ConfigExportService.java) | Сборка DTO из БД |
| [ConfigImportService.java](../src/main/java/com/java/service/maintenance/ConfigImportService.java) | Preview + upsert-импорт |
| `dto/config/` | 16 DTO-классов с `@JsonIgnoreProperties(ignoreUnknown = true)` |

## Thread Pool

Bean `maintenanceTaskScheduler` (`AsyncConfig.java`): 2 потока, префикс `maintenance-sched-`.
