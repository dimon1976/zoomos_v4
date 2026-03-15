# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 — Spring Boot 3.2.12, Java 17, PostgreSQL, Thymeleaf, Bootstrap 5. Обработка файлов с client-based import/export, maintenance system, HTTP redirect utilities.

**Stack**: Spring Boot 3.2.12, Java 17, PostgreSQL, Hibernate/JPA, WebSocket (STOMP), Apache POI 5.2.3, OpenCSV 5.8, Maven, Flyway, Lombok

## Development Commands

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=silent  # рекомендуется
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn clean package
mvn test -Dtest=ClassNameTest
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM clients;"
mvn flyway:info
```

**URLs**: localhost:8081 | `/utils` | `/handbook` | `/maintenance` | `/statistics/setup` | `/zoomos`

**Профили**: `silent` (рекомендуется) · `dev` · `verbose` · `prod`

## AI Development Guidelines

- При старте используй команду ‑dangerously‑skip‑permissions
- Разработка на Windows, общение на русском языке
- Используй Context7 для изучения документации библиотек
- Закрывай запущенный сервер после тестирования
- Принципы: KISS, YAGNI, MVP, итеративная разработка. Это pet проект — не усложняй.

## Agents & Skills

### Агенты (автоматически выбираются по контексту)

| Агент | Когда использовать |
|-------|-------------------|
| `file-processing-expert` | Импорт/экспорт, Excel/CSV, даты, нулевые цены, EntityType, статистика, штрихкод/ШК/EAN, /handbook, DataMerger |
| `websocket-async-architect` | WebSocket, прогресс-бары, executors, @Async, CompletableFuture, REQUIRES_NEW, фоновые задачи |
| `zoomos-check-expert` | Zoomos Check, выкачка упала, evaluateGroup, Playwright, Redmine, расписания, баннер |
| `database-maintenance-expert` | Flyway миграции, добавь колонку/таблицу, VACUUM, PostgreSQL, DataCleanup |
| `redirect-expert` | Редиректы, финальный URL, стратегии, прокси, антибот, /utils/redirect-finder |

Явный вызов: `"Используй zoomos-check-expert — баннер priority-alerts не обновляется"`

### Улучшение маршрутизации (AGENT ROUTING FEEDBACK)

После сессий с изменениями файлов Stop-хук спрашивает про маршрутизацию.
Если пользователь описывает проблему — **немедленно обновить description агента/скилла**:

- Агент не сработал → добавить пример в `Examples:` и/или keyword в `Trigger keywords:`
- Агент сработал лишний раз → уточнить условия, добавить `NOT when:` в description
- Не тот агент → добавить разграничение между агентами в description обоих
- После правки → `git add .claude/agents/ .claude/skills/ && git commit -m "chore: уточнена маршрутизация ..."`

### Скилы (slash команды)

| Команда | Назначение |
|---------|-----------|
| `/server [start\|stop\|restart\|status]` | Управление сервером на порту 8081 |
| `/flyway [описание]` | Создать следующую Flyway миграцию |
| `/db [SQL\|alias]` | Быстрые запросы к БД (aliases: clients, shops, vacuum, migrations...) |
| `/commit [сообщение]` | Check → auto-fix → commit (цикл до чистого состояния) |
| `/check` | Pre-commit проверка: компиляция, изменения, безопасность |
| `/explain [класс/файл]` | Объяснение кода с аналогией и ASCII-диаграммой |
| `/zoomos-check [shop\|list]` | Последние проверки для магазина |

## Documentation Rules (ОБЯЗАТЕЛЬНО)

**Вся документация хранится в папке `docs/`** — один файл (или подпапка) на каждое функциональное направление.

### Текущие файлы документации

| Файл | Направление |
|------|-------------|
| [`docs/zoomos-check.md`](docs/zoomos-check.md) | Zoomos Check — проверка выкачки, evaluateGroup, тренды, Redmine |

### Правила

1. **После любых изменений** в функционале — обновить соответствующий файл в `docs/`.
2. **При добавлении нового направления** — создать новый файл `docs/{название}.md` и добавить строку в таблицу выше.
3. Документация должна описывать: назначение, URL-маршруты, схему БД, ключевую логику, форматы сообщений, ключевые файлы.
4. Это **обязательный последний шаг** любой задачи наравне с коммитом.

## Recent Changes (2026)

### 2026-03
- **Клиенты: is_active + sort_order; Zoomos Settings** — поля `is_active`, `sort_order` в `clients`, таблица `zoomos_settings` (key-value глобальные настройки Zoomos Check). Flyway V43. `ZoomosSettingsService`.
- **Zoomos Check — Привязка к клиентам** — `ZoomosShop.client_id` FK → `clients`, страница `/zoomos/clients`, автосвязка по имени. Flyway V42. Priority alerts детализированы (город, сообщение, runId).
- **Redmine интеграция** — Создание/редактирование задач в tt.zoomos.by со страницы результатов. Flyway V39–V40.

### 2026-02
- **Zoomos Check — Расписание + Приоритетные сайты** — Cron per-shop (`ZoomosSchedulerService`), флаг `is_priority`, глобальный баннер. Flyway V32.
- **Zoomos Check — Baseline тренд-анализ** — Исторический анализ за N дней, TREND_WARNING. Flyway V29.
- **Zoomos Check — CSV для ИТ** — Формат CSV с `;` (Сайт;Город;Тип;Сообщение;История).
- **Barcode Handbook** — Справочник ШК+имена+ссылки, JDBC batch-импорт. Flyway V21.

## Configuration

```properties
server.port=8081
spring.datasource.url=jdbc:postgresql://localhost:5432/zoomos_v4
spring.jpa.hibernate.ddl-auto=none
spring.servlet.multipart.max-file-size=1200MB
maintenance.scheduler.enabled=false   # DISABLED by default
database.cleanup.auto-vacuum.enabled=true
```

**Maintenance cron** (включить через `maintenance.scheduler.enabled=true`):
```properties
maintenance.scheduler.file-archive.cron=0 0 2 * * *       # Daily 02:00
maintenance.scheduler.database-cleanup.cron=0 0 3 * * SUN  # Sunday 03:00
maintenance.scheduler.health-check.cron=0 0 * * * *        # Hourly
```

## Package Structure

```
src/main/java/com/java/
├── config/     controller/     service/     dto/     model/entity/     constants/
```

**Key services**: `AsyncImportService`, `AsyncExportService`, `ImportProcessorService`, `ExportProcessorService`, `ClientService`, `FileAnalyzerService`, `RedirectFinderService`, `ZoomosCheckService`

**Thread Pool Executors** (AsyncConfig.java): `importTaskExecutor` · `exportTaskExecutor` · `fileAnalysisExecutor` · `utilsTaskExecutor` · `redirectTaskExecutor` (10min) · `cleanupTaskExecutor` (30min)

## Async Import Processing

Паттерн: синхронное создание сессии → фоновая обработка.

- `AsyncImportService.startImport()` ([AsyncImportService.java:64](src/main/java/com/java/service/imports/AsyncImportService.java#L64)) — выполняется синхронно, создаёт FileOperation+ImportSession, запускает фон через `TransactionSynchronization.afterCommit()`
- `ImportController.startImport()` ([ImportController.java:164-240](src/main/java/com/java/controller/ImportController.java#L164-L240)) — ждёт futures через `CompletableFuture.allOf()` (2 сек), graceful degradation при timeout

## HTTP Redirect Utility

**Стратегии** (приоритет): `CurlStrategy` → `PlaywrightStrategy` → `HttpClientStrategy`

**ВАЖНО — CurlStrategy**: НЕ использовать User-Agent headers (goldapple.ru и др. блокируют). Ручное следование редиректам вместо `curl -L`. Сохранять `initialRedirectCode` (301/302), не финальный статус.

**Proxy** ([application.properties](src/main/resources/application.properties)): `redirect.proxy.enabled=false`. Rotating proxies: `data/config/proxy-list.txt` (формат `host:port:user:pass`).

**Режимы**: sync (< 50 URL, скачать файл) / async (WebSocket `/topic/redirect-progress/{operationId}`)

## WebSocket Topics

- `/topic/progress/{operationId}` — импорт/экспорт
- `/topic/redirect-progress/{operationId}` — redirect processing
- `/topic/cleanup-progress/{operationId}` — очистка БД
- `/topic/notifications` — maintenance

State persistence: `sessionStorage` на клиенте.

## Import Date Handling (CRITICAL)

`DataFormatter` POI применяет US локаль → даты ломаются. Авто-исправление для STRING полей (productAdditional*, competitorAdditional*, competitorTime/Date):

| Excel | БД |
|-------|-----|
| `20.10.2025` | `20.10.2025` |
| `20.10.2025 6:32:00` | `20.10.2025 06:32` |

Реализация: [ImportProcessorService.java:716-741](src/main/java/com/java/service/imports/ImportProcessorService.java#L716-L741)

Нулевые цены (0.0) → пустые ячейки в Excel: [XlsxFileGenerator.java:305-316](src/main/java/com/java/service/exports/generator/XlsxFileGenerator.java#L305-L316)

## Database Maintenance

**Auto-VACUUM** после удаления ≥ 1M записей (`VACUUM ANALYZE`, async по умолчанию). [DataCleanupService.java:599-677](src/main/java/com/java/service/maintenance/DataCleanupService.java)

**Async Cleanup**: `cleanupTaskExecutor`, батчи по 10000, rollback на уровне батча, 7 дней минимум хранения. Прогресс через WebSocket.

## Statistics System

**Ключевые детали**:
- **Номера операций**: использовать `export.id`, не `fileOperation.id`
- **TASK-номера**: `extractTaskNumberFromSession()` → `sourceOperationIds → av_data.product_additional1` ([ExportStatisticsService.java:214-250](src/main/java/com/java/service/statistics/ExportStatisticsService.java#L214-L250))
- Multi-dimensional: `statisticsFilterFields` JSON array на уровне шаблона
- Excel экспорт: 2 листа — "Статистика" + "Тренды" (↑↓= индикаторы, [StatisticsExcelExportService.java:318-406](src/main/java/com/java/service/statistics/StatisticsExcelExportService.java#L318-L406))
- API: `POST /statistics/analyze`, `GET /statistics/filter-values`

## Data Merger Utility

`/utils/data-merger` — слияние продуктов с аналогами + ссылки. Ключевые компоненты: `DataMergerController`, `DataMergerService`, `DataMergerFieldMapping`. Использует существующие `FileAnalyzerService` и `FileGeneratorService`.

## Zoomos Check (Проверка выкачки)

Проверяет полноту выкачки с `export.zoomos.by` за период. Парсинг через Playwright headless Chrome.

### URLs
- `/zoomos` — список магазинов
- `/zoomos/check/results/{runId}` — вердикт + детали
- `/zoomos/check/history` — история
- `/zoomos/sites` — справочник сайтов + приоритет
- `/zoomos/schedule` — cron-расписания
- `/zoomos/api/priority-alerts` — JSON проблем приоритетных сайтов

### Database Schema (Flyway V23–V32)
```sql
zoomos_check_runs
  id, shop_id, date_from, date_to, time_from, time_to  -- "HH:mm" или null
  status (RUNNING/COMPLETED/FAILED)
  ok_count, warning_count, error_count, not_found_count
  drop_threshold (default 10%), error_growth_threshold (default 30%)
  baseline_days (V29), started_at, completed_at

zoomos_parsing_stats
  id, check_run_id, site_name, city_name, server_name, client_name
  start_time, finish_time, total_products, in_stock, error_count
  completion_total, parsing_duration, check_type (API/ITEM), is_finished
  parsing_id, category_count, completion_percent, parsing_duration_minutes
  is_baseline (V29)

-- ВАЖНО: entity ZoomosKnownSite → таблица zoomos_sites (не zoomos_known_sites!)
zoomos_sites
  id, site_name (UNIQUE), check_type (API/ITEM), is_priority BOOLEAN DEFAULT FALSE

zoomos_shop_schedules
  id, shop_id (UNIQUE FK → zoomos_shops), cron_expression, is_enabled
  time_from, time_to, drop_threshold, error_growth_threshold, baseline_days
  date_offset_from (default -1), date_offset_to (default 0)
  last_run_at, created_at, updated_at
```

### Логика оценки (`ZoomosCheckService.evaluateGroup`)

Сравниваются только **последние две** выкачки (newest vs prev):

| Условие | Статус | Issue |
| ------- | ------ | ----- |
| Падение "В наличии" > `dropThreshold`% | **ERROR** | "Падение 'В наличии': N → M (−X%)" |
| "В наличии": было >0, стало 0 | **ERROR** | "В наличии: N → 0 (−100%)" |
| Рост ошибок > `errorGrowthThreshold`% | WARNING | "Рост ошибок: N → M (+X%)" |
| Ошибок не было, появились > 10 | WARNING | "Ошибки парсинга: 0 → N" |
| Падение числа товаров > `dropThreshold`% | WARNING | "Падение товаров: N → M (−X%)" |
| 100% выкачка, но всегда 0 товаров | WARNING | "100% выкачка, нет товаров — нужна проверка" |
| Всегда нули в "В наличии" (особенность сайта) | OK | — |

**`canDeliver = false`** только при ERROR или NOT_FOUND. Счётчики OK/Warn/Error вычисляются динамически в контроллере из `siteCityStatuses` (не из `run.warningCount` в БД).

### Фильтрация по времени (timeFrom / timeTo)

- Нижняя граница: `startTime >= rangeStart`
- Верхняя граница: `finishTime <= rangeEnd` (null finishTime → используется startTime)
- Реализация: `ZoomosCheckService.filterByTime()`

### Парсинг URL и авторизация

URL: `{baseUrl}/shops-parser/{site}/parsing-history?upd={ts}&dateFrom=...&dateTo=...&shop={shopParam}&onlyFinished=1`
- API-тип: `shop=-`, фильтр по пустому полю "Клиент"
- ITEM-тип: `shop={shopName}`
- Куки (`ZoomosSession`), автообновление при редиректе на `/login`
- cityId из строки "3509 - Вологда" → "3509" для прямых ссылок на историю

**Chart data**: передавать только примитивы в `chartData` (List<Map>), не JPA-объекты → иначе `LazyInitializationException`. `startTime` как epoch millis.

**localStorage**: ключи `checkDateFrom-{shopId}`, `checkDateTo-{shopId}`, `checkTimeFrom-{shopId}`, `checkTimeTo-{shopId}` — независимые для каждого магазина.

### Расписание (`ZoomosSchedulerService`)

`ThreadPoolTaskScheduler` (bean `zoomosSchedulerTaskScheduler`, 3 потока). Cron: Unix 5 полей → Spring 6 полей (добавляется `"0 "` в начало). `dateOffsetFrom` default -1 (вчера), `dateOffsetTo` default 0 (сегодня).

### Приоритетные сайты

Toggle: `POST /zoomos/sites/{id}/priority` или `POST /zoomos/sites/by-name/priority` (создаёт запись если нет). Баннер в `layout/main.html`: fetch `/zoomos/api/priority-alerts` при загрузке, тихо игнорирует ошибки.

### Ключевые файлы

| Файл | Назначение |
|------|-----------|
| [ZoomosCheckService.java](src/main/java/com/java/service/ZoomosCheckService.java) | Playwright-парсинг, `evaluateGroup()`, `filterByTime()`, WebSocket прогресс |
| [ZoomosAnalysisController.java](src/main/java/com/java/controller/ZoomosAnalysisController.java) | `/zoomos/*` роуты, schedule CRUD, priority API |
| [ZoomosParserService.java](src/main/java/com/java/service/ZoomosParserService.java) | Магазины и city_ids |
| [ZoomosSchedulerService.java](src/main/java/com/java/service/ZoomosSchedulerService.java) | Cron-расписания |
| [ZoomosKnownSite.java](src/main/java/com/java/model/entity/ZoomosKnownSite.java) | @Table zoomos_sites, поле isPriority |
| [check-results.html](src/main/resources/templates/zoomos/check-results.html) | Страница результатов (вердикт, issues, детали, графики) |
| [layout/main.html](src/main/resources/templates/layout/main.html) | Глобальный priority-alerts баннер |

Flyway: V23 (базовые таблицы) · V24 (пороги) · V25 (zoomos_sites + parsing_stats) · V26 (time_from/to) · V32 (расписание + приоритет)

## Barcode Handbook (Справочник штрихкодов)

`/handbook` — ШК → наименования → ссылки ритейлеров. Обогащение рабочих файлов готовыми URL.

### Database Schema (Flyway V21)
```sql
bh_products  -- id, barcode (UNIQUE nullable), brand, manufacturer_code
bh_names     -- id, product_id, name, source · UNIQUE(product_id, name)
bh_urls      -- id, product_id, url, domain, site_name, source · UNIQUE(product_id, url)
bh_domains   -- id, domain (UNIQUE), is_active, url_count, description
```

### Import Types
- **`BH_BARCODE_NAME`**: `barcode|name|brand|manufacturerCode`, несколько ШК через запятую → хранятся отдельно
- **`BH_NAME_URL`**: `name|brand|url|siteName`, UPSERT доменов автоматически
- Реализация: JDBC batch (~50-100x быстрее JPA), `EntityPersistenceService` → `BarcodeHandbookService`

### Barcode Normalization (`BarcodeUtils`)
`normalize()` — пробелы/NBSP/управляющие символы, EAN-14 → EAN-13 (удаляет ведущий 0 у 14-значных). [BarcodeUtils.java](src/main/java/com/java/util/BarcodeUtils.java)

### Search Flow
`GET /handbook/search` → configure (колонки + домены) → `POST /handbook/search/process` → XLSX/CSV. Одна входная строка → N выходных (по числу URL). Строки без совпадений не включаются.

### Key Files

| Файл | Назначение |
|------|-----------|
| [BarcodeHandbookService.java](src/main/java/com/java/service/handbook/BarcodeHandbookService.java) | Импорт, поиск, домены |
| [BarcodeHandbookController.java](src/main/java/com/java/controller/BarcodeHandbookController.java) | `/handbook/*` |
| [BarcodeUtils.java](src/main/java/com/java/util/BarcodeUtils.java) | Нормализация ШК |
| [EntityType.java](src/main/java/com/java/model/enums/EntityType.java) | BH_BARCODE_NAME, BH_NAME_URL |
| [EntityPersistenceService.java](src/main/java/com/java/service/imports/handlers/EntityPersistenceService.java) | Switch cases для BH типов |

## Redmine Интеграция

Создание/редактирование задач в tt.zoomos.by со страницы `/zoomos/check/results/{runId}`. Одна задача на домен.

### Конфигурация
```properties
redmine.base-url=https://tt.zoomos.by/
redmine.api-key=<key>
redmine.project-id=19
redmine.tracker-id=4
redmine.status-id=10
redmine.priority-id=2
redmine.assigned-to-id=70
```
При пустом `base-url` или `api-key` — интеграция отключена.

### КРИТИЧНО: Особенности сервера tt.zoomos.by

Сервер возвращает HTTP 404 с пустым телом на POST/PUT/DELETE, но операции **выполняются успешно** (Apache + mod_jk + Phusion Passenger).

Workaround:
- `postIgnoring404()` / `putIgnoring404()` — не бросают исключение при 404 с пустым телом
- После POST: поиск через `findRecentIssueBySubject()` (GET `?subject=~{site}&sort=created_on:desc`)

### API Endpoints (`/zoomos/redmine/`)

| Метод | URL | Назначение |
|-------|-----|-----------|
| GET | `/options` | Трекеры, статусы, приоритеты, кастомные поля |
| GET | `/check-batch?sites=...` | Параллельный поиск задач (8 потоков) |
| GET | `/check?site=...` | Задачи для одного сайта |
| GET | `/issue/{id}` | Детали + последние 3 комментария |
| POST | `/create` | Создание задачи |
| PUT | `/update/{id}` | Обновление + комментарий |

### Database Schema (Flyway V39–V40)
```sql
zoomos_redmine_issues
  id, site_name (UNIQUE), issue_id, issue_status, is_closed, issue_url, created_at, updated_at
```

### Статус-цвета и логика страницы
- `btn-danger` — открытая задача, `btn-success` — закрытая (по `is_closed`)
- Рендер: сначала из БД (быстро), затем async JS `check-batch` для всех проблемных сайтов
- `replaceAllSiteBtns(site, issue)` — обновляет кнопки во ВСЕХ строках одного домена
- Кастомные поля: "В чем ошибка" (`cfError`), "Способ выкачки" (`cfMethod`), "Вариант настройки" (`cfVariant`)

### Ключевые файлы

| Файл | Назначение |
|------|-----------|
| [RedmineService.java](src/main/java/com/java/service/RedmineService.java) | Вся бизнес-логика |
| [ZoomosRedmineController.java](src/main/java/com/java/controller/ZoomosRedmineController.java) | REST endpoints |
| [RedmineConfig.java](src/main/java/com/java/config/RedmineConfig.java) | Конфиг |
| [check-results.html](src/main/resources/templates/zoomos/check-results.html) | Модал + JS batch-check |

## Code References

Используй паттерн `file_path:line_number` при ссылках на код.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
# currentDate
Today's date is 2026-02-21.
