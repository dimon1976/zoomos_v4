# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 is a Spring Boot 3.2.12 file processing application with client-based import/export functionality, maintenance system, and utilities for HTTP redirect processing. Built with Java 17, PostgreSQL, and modern web technologies.

### Technology Stack
- **Backend**: Spring Boot 3.2.12, Java 17, PostgreSQL, Hibernate/JPA, WebSocket (STOMP)
- **Frontend**: Thymeleaf, Bootstrap 5.3.0, JavaScript ES6+, Font Awesome
- **File Processing**: Apache POI 5.2.3 (Excel), OpenCSV 5.8 (CSV), Universal Character Detection
- **Build System**: Maven with Spring Boot parent
- **Development**: Spring DevTools, Lombok, Flyway migrations

## Development Commands

```bash
# Быстрый старт (рекомендуется)
mvn spring-boot:run -Dspring-boot.run.profiles=silent

# Другие профили
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=verbose

# Build
mvn clean compile
mvn clean package
mvn test
mvn test -Dtest=ClassNameTest

# Database
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM clients;"
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM file_operations;"
mvn flyway:info
```

### Доступ к приложению
- **Главная**: http://localhost:8081
- **Утилиты**: http://localhost:8081/utils
- **Справочник**: http://localhost:8081/handbook
- **Обслуживание**: http://localhost:8081/maintenance
- **Статистика**: http://localhost:8081/statistics/setup
- **Zoomos Check**: http://localhost:8081/zoomos

### Основные разделы
1. **Import/Export** (главная страница) - Импорт и экспорт файлов для клиентов
2. **Handbook** (`/handbook`) - Справочник штрихкодов: накопление базы товаров и обогащение файлов ссылками
3. **Utilities** (`/utils`) - HTTP Redirect Finder, Data Merger, Barcode Match, URL Cleaner
4. **Maintenance** (`/maintenance`) - Файловые операции, очистка БД, мониторинг системы
5. **Statistics** (`/statistics/setup`) - Анализ и сравнение операций экспорта
6. **Zoomos Check** (`/zoomos`) - Проверка выкачки: парсинг export.zoomos.by, вердикт по наличию товаров

## Application Profiles

- **silent** (recommended): Minimal logging, fast startup, clean output
- **dev**: Moderate logging with DevTools enabled
- **verbose**: Maximum debugging with SQL logging and TRACE level
- **prod**: Production optimized with file logging

## Recent Changes & Features

### 2026-02

- **Zoomos Check — Расписание + Приоритетные сайты** — Cron-расписания автопроверок per-shop (`ZoomosSchedulerService`, `ThreadPoolTaskScheduler`). Флаг `is_priority` в справочнике сайтов. Глобальный баннер в layout при проблемах приоритетных сайтов. Flyway V30. См. секцию ниже.
- **Zoomos Check — Baseline тренд-анализ** — Исторический анализ метрик выкачек за N дней. TREND_WARNING issues (не влияют на canDeliver). Flyway V29, поле `baselineDays` на форме. См. секцию ниже.
- **Zoomos Check — CSV для ИТ** — «Текст для ИТ» теперь в формате CSV с разделителем `;` (Сайт;Город;Тип;Сообщение;История).
- **Zoomos Check** — Проверка выкачки export.zoomos.by: парсинг истории через Playwright, вердикт готовности отчёта, сводка ИТ с копированием. Flyway V23–V28, 2 новые таблицы. См. секцию ниже.
- **Barcode Handbook** - Справочник штрихкодов: накопление базы товаров (ШК+имена+ссылки) и обогащение рабочих файлов ссылками. Flyway V21, 4 таблицы, JDBC batch-импорт, поиск по ШК/имени/URL
- **Import TimeoutException Fix** - Устранён TimeoutException при одновременном импорте нескольких файлов

### 2026-01

- **Statistics Display Fix** - Исправлено дублирование номеров операций и TASK-номеров на странице статистики
- **Memory Optimization** - Оптимизация потребления памяти приложения (JVM heap, HikariCP, Hibernate, Thread Pools)

### 2025-11
- **Excel Export with Trends Sheet** - Двухлистовый Excel экспорт со страницей трендов (↑↓= индикаторы)

### 2025-10
- **Template-Based Operations Filtering** - Фильтрация операций экспорта по выбранному шаблону
- **Smart Date Format Detection** - Автоматическое определение формата дат для STRING полей (dd.MM.yyyy)
- **Empty Cells for Zero Prices** - Пустые ячейки вместо "0.00" при экспорте
- **Auto-VACUUM after Cleanup** - Автоматическая оптимизация PostgreSQL после массовых удалений
- **Async Data Cleanup with WebSocket** - Асинхронная очистка БД с прогрессом в реальном времени
- **Multi-Dimensional Statistics** - Фильтрация статистики по произвольным полям (stock status, category, etc.)

### 2025-09
- **Data Merger Utility** - Слияние данных из двух файлов с настраиваемым маппингом колонок
- **HTTP Redirect Utility Enhancements** - Двухрежимная обработка (sync/async) с WebSocket уведомлениями

## Architecture Overview

### Core Application Structure
```
src/main/java/com/java/
├── config/          # Configuration classes (WebSocket, Async, Security)
├── controller/      # REST and MVC controllers
├── service/         # Business logic services
├── dto/            # Data transfer objects
├── model/entity/   # JPA entities
└── constants/      # Application constants
```

### Key Service Categories

**File Processing Services**
- `FileAnalyzerService` - File structure analysis and validation
- `AsyncImportService` / `AsyncExportService` - Asynchronous processing
- `ImportProcessorService` / `ExportProcessorService` - Core processing logic

**Client and Template Services**
- `ClientService` - Client management CRUD operations
- `ImportTemplateService` / `ExportTemplateService` - Template management
- `TemplateValidationService` - Template field validation

**Maintenance System Services**
- `MaintenanceSchedulerService` - Automated tasks with @Scheduled annotations
- `FileManagementService` - File archiving and disk space analysis
- `DatabaseMaintenanceService` - Database cleanup and optimization
- `SystemHealthService` - System monitoring and health checks

**Utility Services**
- `RedirectFinderService` - HTTP redirect processing utility
- `AsyncRedirectService` - Asynchronous redirect processing with WebSocket progress
- `CurlStrategy`, `PlaywrightStrategy`, `HttpClientStrategy` - Strategy pattern for anti-bot bypass
- `BarcodeMatchService`, `UrlCleanerService`, `LinkExtractorService` - Additional utilities

## Async Import Processing

### Architecture Pattern

Система импорта использует паттерн **синхронного создания сессии + фоновой обработки**, идентичный ExportService.

1. **AsyncImportService.startImport()** ([AsyncImportService.java:64](src/main/java/com/java/service/imports/AsyncImportService.java#L64))
   - Выполняется **синхронно** в HTTP потоке (без @Async)
   - Создаёт FileOperation и ImportSession немедленно, возвращает `CompletableFuture.completedFuture(session)` сразу
   - Запускает фоновую обработку через `TransactionSynchronization.afterCommit()`

2. **ImportController.startImport()** ([ImportController.java:164-240](src/main/java/com/java/controller/ImportController.java#L164-L240))
   - Ждёт все futures параллельно через `CompletableFuture.allOf()` (2 секунды на все файлы)
   - Graceful degradation при timeout

**Конфигурация** ([application-silent.properties:46-49](src/main/resources/application-silent.properties#L46-L49)):
```properties
import.async.core-pool-size=2
import.async.max-pool-size=5
import.async.queue-capacity=50
```

**HikariCP Pool** ([application.properties:26-32](src/main/resources/application.properties#L26-L32)):
```properties
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
```

## HTTP Redirect Utility

### Purpose
Processes CSV/Excel files containing URLs to resolve final URLs after HTTP redirects (301, 302, 307, 308).

### Strategy Pattern for Anti-Bot Bypass
1. **CurlStrategy** (Priority 1): System curl commands for maximum compatibility
2. **PlaywrightStrategy** (Priority 2): Headless browser for blocked sites
3. **HttpClientStrategy** (Priority 3): Java HTTP Client fallback

### Key Components
- `RedirectFinderController` - Web interface with dual processing modes (/process and /process-async endpoints)
- `RedirectFinderService` - Core business logic with sync and async request preparation
- `AsyncRedirectService` - Dedicated async processing with comprehensive WebSocket progress tracking
- `RedirectStrategy` - Interface implemented by three processing strategies with intelligent fallback
- `RedirectResult` - Result model with URL, redirect count, HTTP status, and processing metadata
- `RedirectProgressDto` - Progress tracking DTO for real-time WebSocket updates
- `RedirectProcessingRequest` - Request model for async processing with URL data and configuration
- `RedirectUrlData` - Individual URL data model supporting ID and model fields

### Critical Implementation Details
**CurlStrategy User-Agent Issue**: Do NOT use User-Agent headers - many sites (goldapple.ru) block redirects when browser User-Agent is present. Manual redirect following is implemented instead of `curl -L`.

**HTTP Status Code Handling**: Capture the initial redirect HTTP code (301/302) not the final status (200). Use `initialRedirectCode` variable to preserve the first redirect status.

**JavaScript Redirect Detection**: PlaywrightStrategy successfully handles sites like Yandex Market where redirects occur via JavaScript rather than HTTP headers. Essential for e-commerce and dynamic sites.

**Dual Processing Modes**:
- **Synchronous**: Direct file download after processing, suitable for files under 50 URLs
- **Asynchronous**: Background processing with WebSocket notifications via `/topic/redirect-progress/{operationId}`

### Proxy Configuration for Geo-Blocking Bypass

**Configuration** ([application.properties](src/main/resources/application.properties)):
```properties
redirect.proxy.enabled=false
redirect.proxy.server=host:port
redirect.proxy.username=your_username
redirect.proxy.password=your_password
redirect.proxy.type=HTTP  # HTTP или SOCKS5

redirect.proxy.rotating.enabled=false
redirect.proxy.rotating.pool-size=5
redirect.proxy.rotating.pool-file=data/config/proxy-list.txt
```

Rotating proxies: файл `data/config/proxy-list.txt`, формат `host:port:username:password` на строку. Пример: [proxy-example.txt](data/config/proxy-example.txt).

**Key Components**: `ProxyConfig`, `ProxyPoolManager`, интеграция в `PlaywrightStrategy` через `BrowserType.LaunchOptions`. UI checkbox "Использовать прокси" на странице [redirect-finder-configure.html](src/main/resources/templates/utils/redirect-finder-configure.html). Proxy используется только когда CurlStrategy не справляется, backward compatible.

## Configuration System

### Key Application Properties
```properties
# Server configuration
server.port=8081

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/zoomos_v4
spring.jpa.hibernate.ddl-auto=none  # Important: Disabled for production

# File handling
spring.servlet.multipart.max-file-size=1200MB
application.upload.dir=data/upload
application.export.dir=data/upload/exports

# Maintenance system (DISABLED by default for safety)
maintenance.scheduler.enabled=false
```

### Thread Pool Configuration
```properties
# Import processing
import.async.core-pool-size=1
import.async.max-pool-size=2

# Export processing
export.async.core-pool-size=2
export.async.max-pool-size=4
```

**Thread Pool Executors** (configured in AsyncConfig.java):
- `importTaskExecutor`: Import processing (configurable via properties)
- `exportTaskExecutor`: Export processing (configurable via properties)
- `fileAnalysisExecutor`: File analysis operations (2-4 threads)
- `utilsTaskExecutor`: General utilities (1-2 threads)
- `redirectTaskExecutor`: HTTP redirect processing (1-3 threads, 10min timeout)
- `cleanupTaskExecutor`: Database cleanup operations (1-2 threads, 30min timeout)

## Development Guidelines

### Code Conventions
- **KISS Principle**: Keep it simple - minimize classes and complexity
- **MVP Approach**: Basic functionality first, avoid over-engineering
- **Fail Fast**: Quick error validation and immediate feedback
- **Lombok**: Use @Data, @Builder, @Slf4j for cleaner code

### Project Structure
- Follow existing package structure in `com.java.*`
- Place utilities in `controller/utils/`, `service/utils/`, `dto/utils/`
- Use existing services like `FileAnalyzerService`, `FileGeneratorService`
- Maintain consistent naming conventions

## Maintenance System

### Scheduled Tasks (Disabled by Default)
```properties
# Enable with: maintenance.scheduler.enabled=true
maintenance.scheduler.file-archive.cron=0 0 2 * * *      # Daily at 02:00
maintenance.scheduler.database-cleanup.cron=0 0 3 * * SUN # Weekly Sunday 03:00
maintenance.scheduler.health-check.cron=0 0 * * * *      # Hourly
```

### Web Interface
- `/maintenance` - Main dashboard
- `/maintenance/files` - File operations
- `/maintenance/database` - Database maintenance
- `/maintenance/system` - System health monitoring

## Error Handling

### Exception Hierarchy
- `FileOperationException` - Base for file processing errors
- `ImportException` - Import-specific errors
- `TemplateValidationException` - Template configuration errors

### Global Error Handling
- `GlobalExceptionHandler` - Application-wide exception handling
- `ImportExceptionHandler` - Specialized import error handling

## WebSocket Integration

Real-time updates for:
- File processing progress (`/topic/progress/{operationId}`)
- Redirect processing progress (`/topic/redirect-progress/{operationId}`)
- Data cleanup progress (`/topic/cleanup-progress/{operationId}`)
- Maintenance system notifications (`/topic/notifications`)
- Client-side JavaScript handles connections and reconnection
- State persistence via `sessionStorage` for operation recovery

## AI Development Guidelines

- При старте используй команду ‑dangerously‑skip‑permissions
- Разработка ведется на ОС Windows
- Общение на русском языке
- Используй Context7 для изучения документации библиотек
- Закрывай запущенный сервер после тестирования
- Следуй принципам KISS, YAGNI, MVP, итеративная разработка
- Не усложняй код без необходимости - это pet проект

## Database Maintenance & Auto-VACUUM

### Auto-VACUUM after Data Cleanup

**Configuration** ([application.properties:45-48](src/main/resources/application.properties#L45-L48)):
- `database.cleanup.auto-vacuum.enabled=true`
- `database.cleanup.auto-vacuum.threshold-records=1000000` - Триггер при удалении ≥ 1M записей
- `database.cleanup.auto-vacuum.run-async=true` - Асинхронное выполнение

Автоматически запускается `VACUUM ANALYZE` после удаления ≥ 1M записей. Async режим (default) не блокирует пользователя. Улучшает производительность запросов на 20-40% после крупных удалений.

**Key Methods** ([DataCleanupService.java](src/main/java/com/java/service/maintenance/DataCleanupService.java)):
- `performAutoVacuum()` - Controls async/sync execution (lines 599-621)
- `executeVacuumAnalyze()` - Executes VACUUM ANALYZE for each table (lines 623-656)
- `getTableNameForEntityType()` - Maps entity type to table name (lines 658-677)

### Asynchronous Data Cleanup with WebSocket Progress

Асинхронная очистка через `cleanupTaskExecutor` (1-2 потока, 30 мин timeout). Безопасное пакетное удаление (10000 записей/батч) с rollback на уровне каждого батча.

**Архитектура**:
- `executeCleanupAsync()` — `@Async("cleanupTaskExecutor")` для UI ([DataCleanupController.java:110-173](src/main/java/com/java/controller/DataCleanupController.java#L110-L173)): генерирует `operationId` и немедленно возвращает клиенту
- `executeCleanup()` — синхронный, для Scheduler
- Прогресс через WebSocket `/topic/cleanup-progress/{operationId}`, state recovery через `sessionStorage`
- Удаление через временные таблицы + `@Transactional(propagation = REQUIRES_NEW)` на батч

**Safety**: минимум 7 дней хранения данных, подтверждение через ввод "CONFIRM", rollback на уровне батча. После удаления ≥ 1M записей автоматически запускается VACUUM ANALYZE.

## Data Merger Utility

### Purpose
Utility for merging product data from two sources: source products with analogs and link data with product URLs.

### Key Components
- `DataMergerController` - Web interface at `/utils/data-merger` for file upload and processing
- `DataMergerService` - Core business logic for data merging and validation
- `DataMergerFieldMapping` - Configuration DTO for column mapping between files
- Dynamic column mapping UI with real-time file preview
- Integration with existing `FileAnalyzerService` and `FileGeneratorService`

### Features
- **Dual File Processing**: Handles source file (products+analogs) and links file separately
- **Dynamic Column Mapping**: Users select which columns map to required fields
- **Data Expansion**: Creates multiple output records for each analog-link combination
- **Export Integration**: Uses existing CSV/Excel export system for consistent output

## Statistics System

### Export Statistics with Multi-Dimensional Filtering

Система сравнения операций экспорта с фильтрацией по произвольным полям (stock status, category, brand).

**Database Layer** (Flyway V15):
- `filter_field_name` VARCHAR(255), `filter_field_value` VARCHAR(255)
- Composite index `idx_export_statistics_filter` (export_session_id, filter_field_name, filter_field_value)
- Хранит общую статистику (filter_field_name = NULL) и фильтрованную

**Repository** (`ExportStatisticsRepository`):
- `findBySessionIdsWithoutFilter()` — общая статистика
- `findBySessionIdsAndFilter()` — фильтрованная статистика
- `findDistinctFilterValues()` / `findDistinctFilterFields()` — доступные фильтры

**Service** (`ExportStatisticsService`):
- `calculateComparison(request)` — общая статистика
- `calculateComparison(request, filterField, filterValue)` — фильтрованная

**API**: `POST /statistics/analyze`, `GET /statistics/filter-values` → Map<fieldName, List<values>>

**Multi-Dimensional Statistics**: каждая сессия хранит общую статистику + по каждому уникальному значению фильтра.
- Формула: `total_records = groups × metrics × (1 + unique_filter_values)`
- Конфигурация: `statisticsFilterFields` JSON array на уровне шаблона, например `["competitorStockStatus", "productCategory"]`

**Key Components**:
- `ExportStatisticsWriterService` — сохраняет статистику при экспорте (lines 180-250)
- `ExportStatisticsController` — API статистики (lines 74-119, 308-345)
- `StatisticsRequestDto`, `StatisticsComparisonDto`
- `statistics/results.html` — таблица сравнения с панелью фильтров (lines 387-421, 630-840)

### Excel Export with Trends Sheet

Двухлистовый Excel: **"Статистика"** (детальные метрики) + **"Тренды"** (изменения с индикаторами).

**Trends sheet format** ([StatisticsExcelExportService.java:318-406](src/main/java/com/java/service/statistics/StatisticsExcelExportService.java#L318-L406)):
```
Группа    | Метрика           | Операция #1  | Операция #2
----------|-------------------|--------------|-------------
ОБЩЕЕ     | PRICE             | ↑ (+5.2%)   | ↓ (-2.1%)
Группа А  | DATE_MODIFICATIONS| ↓ (-4.0%)   | ↑ (+2.5%)
```
- ↑ зелёный — рост, ↓ красный/оранжевый — падение, = серый — стабильно, "-" — нет предыдущего значения
- Данные из `MetricValue.changeType` и `MetricValue.changePercentage`, без доп. запросов к БД
- Фильтры results страницы применяются автоматически

### Template-Based Operations Filtering

При выборе шаблона на `/statistics/setup` показываются только операции этого шаблона (клиентская фильтрация).

- `filterExportsByTemplate(templateId)` — показывает/скрывает `.export-card` по `data-template-id`
- Атрибуты: `data-template-id` на radio inputs шаблонов и на div.export-card операций

### Unique Operation and TASK Numbers

- **Номера операций**: использовать `export.id` (exportSessionId), не `fileOperation.id` — гарантирует уникальность между setup и results страницами
- **TASK-номера**: `extractTaskNumberFromSession()` извлекает из первой операции `sourceOperationIds → av_data.product_additional1` ([ExportStatisticsService.java:214-250](src/main/java/com/java/service/statistics/ExportStatisticsService.java#L214-L250))
- `ExportSession.getTaskNumber()` — `@Transient` метод для setup.html, извлекает из имени файла через regex

## Import Date Handling (CRITICAL)

### Smart Date Format Detection for STRING Fields

Применяется для полей productAdditional*, competitorAdditional*, competitorTime, competitorDate.

**Проблема**: `DataFormatter` POI применяет US локаль → `'20.10.2025 6:32:00'` становится `'10/20/25 6:32'`.

**Решение** ([ImportProcessorService.java:716-741](src/main/java/com/java/service/imports/ImportProcessorService.java#L716-L741)):

| Excel значение | Результат в БД |
|----------------|----------------|
| `20.10.2025` | `20.10.2025` |
| `20.10.2025 06:32` | `20.10.2025 06:32` |
| `20.10.2025 06:32:45` | `20.10.2025 06:32:45` |
| `20.10.2025  6:32:00` | `20.10.2025 06:32` (унификация) |

Применяется только для **STRING** полей. Для LocalDateTime/Date — стандартная обработка POI.

### Empty Cells for Zero Prices in Export

Нулевые цены (0.0) → пустые ячейки в Excel ([XlsxFileGenerator.java:305-316](src/main/java/com/java/service/exports/generator/XlsxFileGenerator.java#L305-L316)): `numValue == 0.0` → `cell.setBlank()`.

## Zoomos Check (Проверка выкачки)

### Purpose
Автоматическая проверка полноты выкачки данных с `export.zoomos.by` за выбранный период. Помогает быстро понять — можно ли отдавать отчёт клиенту или есть проблемы с наличием товаров.

### URL
- **Главная**: `/zoomos` — список магазинов, запуск проверки
- **Результаты**: `/zoomos/check/results/{runId}` — вердикт + детали
- **История**: `/zoomos/check/history` — все прошлые проверки
- **Справочник сайтов**: `/zoomos/sites` — список известных сайтов + приоритет
- **Расписание**: `/zoomos/schedule` — управление cron-расписаниями по магазинам
- **Priority Alerts API**: `/zoomos/api/priority-alerts` — JSON-список проблем приоритетных сайтов

### Database Schema (Flyway V23–V30)
```sql
zoomos_check_runs  -- запись о каждой проверке
  id, shop_id, date_from, date_to, time_from, time_to  -- time_from/time_to: "HH:mm" или null
  status (RUNNING/COMPLETED/FAILED)
  ok_count, warning_count, error_count, not_found_count
  drop_threshold (default 10%), error_growth_threshold (default 30%)
  baseline_days (V29), started_at, completed_at

zoomos_parsing_stats  -- одна строка из таблицы истории парсинга
  id, check_run_id, site_name, city_name, server_name, client_name
  start_time, finish_time, total_products, in_stock, error_count
  completion_total, parsing_duration, check_type (API/ITEM), is_finished
  parsing_id, category_count, completion_percent, parsing_duration_minutes
  is_baseline (V29)

zoomos_sites  -- справочник известных сайтов zoomos (V25, таблица zoomos_sites!)
  id, site_name (UNIQUE), check_type (API/ITEM)
  is_priority BOOLEAN DEFAULT FALSE  (V30)

zoomos_shop_schedules  -- расписания автопроверок (V30)
  id, shop_id (UNIQUE FK → zoomos_shops), cron_expression, is_enabled
  time_from, time_to, drop_threshold, error_growth_threshold, baseline_days
  date_offset_from (default -1), date_offset_to (default 0)
  last_run_at, created_at, updated_at
```

### Логика оценки (`ZoomosCheckService.evaluateGroup`)

Сравниваются только **последние две** выкачки (newest vs prev) — текущее состояние:

| Условие | Статус | Issue |
| ------- | ------ | ----- |
| Падение "В наличии" > `dropThreshold`% | **ERROR** | "Падение 'В наличии': N → M (−X%)" |
| "В наличии": было >0, стало 0 | **ERROR** | "В наличии: N → 0 (−100%)" |
| Рост ошибок парсинга > `errorGrowthThreshold`% | WARNING | "Рост ошибок: N → M (+X%)" |
| Ошибок не было, появились > 10 | WARNING | "Ошибки парсинга: 0 → N" |
| Падение числа товаров > `dropThreshold`% | WARNING | "Падение товаров: N → M (−X%)" |
| 100% выкачка, но всегда 0 товаров | WARNING | "100% выкачка, нет товаров — нужна проверка" |
| Всегда нули в "В наличии" (особенность сайта) | OK (игнор) | — |

**Одиночная запись:** выкачка не завершена (completionPercent < 100) → WARNING. Завершена (100%), но 0 товаров → WARNING.

**`canDeliver = false`** (нельзя отдавать отчёт) только при наличии ERROR или NOT_FOUND (нет данных за период).

**Счётчики OK/Warn/Error** вычисляются **динамически** в контроллере из `siteCityStatuses` (не из `run.warningCount` в БД), чтобы всегда совпадать с отображаемыми issues. `run.warningCount` используется только внутри check-service и при сохранении в БД.

### Страница результатов (`check-results.html`)

Четыре блока:
1. **Вердикт** — зелёный "Отчёт готов" / красный "Есть проблемы", период (с временем если задано), счётчики OK/Warn/Err
2. **На что обратить внимание** — список issues с кнопками **Матчинг** и **История** (прямая ссылка на нужный город). Внизу — **текст для ИТ** с кнопкой копирования и ссылками на историю:
   ```
   site1.ru:
     3612 - Нижний Новгород — В наличии: 500 → 0 (−100%)
       История: {baseUrl}/shops-parser/site1.ru/parsing-history?...&cityId=3612
   site2.ru:
     Нет данных за указанный период
   ```
3. **Детали по сайтам** — все свёрнуты, кнопки "Раскрыть всё" / "Свернуть всё". Внутри каждого сайта — подгруппы по городам с таблицей выкачек DESC по времени. Стрелки ↑↓ для изменений.
4. **Графики динамики** — Chart.js, lazy load при первом раскрытии. Три dataset: Товаров / В наличии / Ошибки.

### Фильтрация по времени (timeFrom / timeTo)

Позволяет проверить только выкачки, **завершённые** в указанном временном диапазоне:
- `timeFrom` (необязательно) — нижняя граница по `startTime`, дефолт 00:00
- `timeTo` (необязательно) — верхняя граница по `finishTime` (когда данные реально готовы), дефолт 23:59

**Фильтр в `ZoomosCheckService.filterByTime()`:**
- Нижняя граница: `startTime >= rangeStart`
- Верхняя граница: `finishTime <= rangeEnd` (если `finishTime == null` — используется `startTime`)
- Строки с обоими null — пропускаются через фильтр

**Пример**: выкачка стартовала в 02:01, закончила в 11:51. При `timeTo=11:06` — отфильтрована (финиш 11:51 > 11:06).

**localStorage**: `checkTimeFrom-{shopId}` / `checkTimeTo-{shopId}` — независимые для каждого магазина.

### Расписание автопроверок (`/zoomos/schedule`)

`ZoomosSchedulerService` управляет динамическими cron-задачами через `ThreadPoolTaskScheduler` (bean `zoomosSchedulerTaskScheduler`, 3 потока).

**Логика:**
- `@PostConstruct init()` — загружает все enabled расписания при старте
- `saveAndReschedule(schedule)` — сохраняет в БД, отменяет старую задачу, планирует новую
- `toggleEnabled(shopId)` — переключает is_enabled и перепланирует
- Cron: Unix-формат 5 полей (`мин час день мес нед`) → Spring 6 полей (добавляется `"0 "` в начало)
- `runCheck(schedule)` — вычисляет dateFrom/dateTo через `today.plusDays(offset)`, запускает `checkService.runCheck(...)`, обновляет `lastRunAt`

**Поля ZoomosShopSchedule:**
- `dateOffsetFrom` (default -1) = вчера, `dateOffsetTo` (default 0) = сегодня
- Один магазин — одно расписание (UNIQUE shop_id)

### Приоритетные сайты и глобальный баннер

**Флаг `is_priority`** в `zoomos_sites` (entity `ZoomosKnownSite`). Toggle через:
- `POST /zoomos/sites/{id}/priority` — по id (из `/zoomos/sites`)
- `POST /zoomos/sites/by-name/priority` — по siteName, создаёт запись если нет

**API `GET /zoomos/api/priority-alerts`:**
1. Загружает все priority-сайты (`findAllByIsPriorityTrue()`)
2. Берёт последние COMPLETED run за сегодня (`findCompletedToday(startOfDay)`)
3. Для каждого run — смотрит stats по priority-сайтам, оценивает `evaluateGroup()`
4. Возвращает `[{siteName, severity, issueCount}]` (пустой массив при ошибке)

**Баннер** в `layout/main.html` (между navbar и main): `d-none` по умолчанию, появляется только если API вернул непустой список. Inline JS fetch при загрузке каждой страницы, тихо игнорирует ошибки.

**Важно:** Entity `ZoomosKnownSite` → таблица `zoomos_sites` (не `zoomos_known_sites`!).

### Ключевые файлы

| Файл | Назначение |
|------|-----------|
| [ZoomosCheckService.java](src/main/java/com/java/service/ZoomosCheckService.java) | Playwright-парсинг, `evaluateGroup()`, `filterByTime()`, WebSocket прогресс |
| [ZoomosAnalysisController.java](src/main/java/com/java/controller/ZoomosAnalysisController.java) | `/zoomos/*` роуты, schedule CRUD, priority-alerts API, priority toggle |
| [ZoomosParserService.java](src/main/java/com/java/service/ZoomosParserService.java) | Управление магазинами и city_ids, двунаправленная синхронизация настроек |
| [ZoomosSchedulerService.java](src/main/java/com/java/service/ZoomosSchedulerService.java) | Cron-расписания: @PostConstruct, saveAndReschedule, toggleEnabled |
| [ZoomosCheckRun.java](src/main/java/com/java/model/entity/ZoomosCheckRun.java) | JPA entity проверки (включая timeFrom/timeTo, baselineDays) |
| [ZoomosParsingStats.java](src/main/java/com/java/model/entity/ZoomosParsingStats.java) | JPA entity строки статистики |
| [ZoomosKnownSite.java](src/main/java/com/java/model/entity/ZoomosKnownSite.java) | JPA entity справочника сайтов (@Table zoomos_sites), поле isPriority |
| [ZoomosShopSchedule.java](src/main/java/com/java/model/entity/ZoomosShopSchedule.java) | JPA entity расписания магазина |
| [check-results.html](src/main/resources/templates/zoomos/check-results.html) | Страница результатов (4 блока) |
| [index.html](src/main/resources/templates/zoomos/index.html) | Список магазинов + кнопка "Расписание" |
| [sites.html](src/main/resources/templates/zoomos/sites.html) | Справочник сайтов + кнопка приоритета (звёздочка) |
| [schedule.html](src/main/resources/templates/zoomos/schedule.html) | Управление расписаниями, JS-based сохранение строк |
| [layout/main.html](src/main/resources/templates/layout/main.html) | Глобальный priority-alerts баннер |
| [V23__create_zoomos_check_tables.sql](src/main/resources/db/migration/V23__create_zoomos_check_tables.sql) | Flyway: базовые таблицы |
| [V24__add_check_thresholds.sql](src/main/resources/db/migration/V24__add_check_thresholds.sql) | Flyway: пороги dropThreshold/errorGrowthThreshold |
| [V25__zoomos_sites_and_parsing_stats_update.sql](src/main/resources/db/migration/V25__zoomos_sites_and_parsing_stats_update.sql) | Flyway: zoomos_sites + доп. поля parsing_stats |
| [V26__add_time_range_to_check_runs.sql](src/main/resources/db/migration/V26__add_time_range_to_check_runs.sql) | Flyway: time_from, time_to в check_runs |
| [V30__add_schedule_and_priority.sql](src/main/resources/db/migration/V30__add_schedule_and_priority.sql) | Flyway: is_priority в zoomos_sites, CREATE zoomos_shop_schedules |

### Технические детали

**Парсинг** (Playwright headless Chrome):
- URL формат: `{baseUrl}/shops-parser/{site}/parsing-history?upd={ts}&dateFrom=DD.MM.YYYY&dateTo=DD.MM.YYYY&launchDate=&shop={shopParam}&site=&cityId=&address=&accountId=&server=&onlyFinished=1`
- API-тип: `shop=-` (глобальные выкачки) + фильтр: поле "Клиент" должно быть **пустым** (чужие выкачки игнорируются)
- ITEM-тип: `shop={shopName}`
- Авторизация через куки (`ZoomosSession`), автообновление при редиректе на `/login`

**Ссылки на историю** (`Матчинг` / `История`):
- URL: `/shops-parser/{site}/parsing-history?...&cityId={cityId}` — с параметром cityId для прямого перехода на нужный город
- cityId извлекается из строки вида "3509 - Вологда" → "3509"
- Те же ссылки текстом в "Текст для ИТ"

**Inline JS данные для чартов**: передаются через `chartData` (List<Map>) — только примитивы, без JPA-объектов (иначе `LazyInitializationException`). `startTime` как epoch millis.

**localStorage**: ключи `checkDateFrom-{shopId}`, `checkDateTo-{shopId}`, `checkTimeFrom-{shopId}`, `checkTimeTo-{shopId}` — независимые для каждого магазина. Проверки запускаются в фоне (без авторедиректа), чтобы параллельно запускать несколько клиентов.

### Testing Status
✅ Playwright-парсинг API и ITEM сайтов
✅ Вердикт-блок (canDeliver логика)
✅ Группировка по сайту → городу
✅ Текст для ИТ с копированием и URL историй
✅ Графики Chart.js (lazy)
✅ Фоновые параллельные проверки (без авторедиректа)
✅ Независимые даты и время по shopId
✅ Фильтрация по временному диапазону (finishTime для верхней границы)
✅ Ссылки на историю с cityId
✅ API: фильтрация по пустому полю "Клиент"
✅ Расписание автопроверок (cron per-shop, ZoomosSchedulerService)
✅ Приоритетные сайты (isPriority toggle в /zoomos/sites)
✅ Глобальный баннер приоритетных сайтов (layout/main.html)

## Barcode Handbook (Справочник штрихкодов)

### Purpose
Централизованная база знаний о товарах: штрихкоды → наименования → ссылки на страницы товаров у ритейлеров. Позволяет обогащать рабочие файлы (список ID + ШК) готовыми URL из справочника.

### Database Schema (Flyway V21)

```sql
bh_products  -- центральная сущность товара
  id, barcode (UNIQUE nullable), brand, manufacturer_code, created_at, updated_at

bh_names     -- наименования товара (один продукт = много имён)
  id, product_id → bh_products, name, source
  UNIQUE(product_id, name)

bh_urls      -- ссылки товара (один продукт = много ссылок)
  id, product_id → bh_products, url, domain, site_name, source
  UNIQUE(product_id, url)

bh_domains   -- реестр доменов с флагом активности
  id, domain (UNIQUE), is_active, url_count, description
```

### Import Types

Используется **существующая система импорта** (шаблоны + AsyncImportService). Создаёшь шаблон с EntityType и импортируешь файл как обычно.

**`BH_BARCODE_NAME`** — файл `штрихкод | наименование | бренд`:
- Поля: `barcode`, `name`, `brand`, `manufacturerCode`
- Несколько ШК через запятую (`"A,B"`) → хранятся отдельно
- UPSERT продуктов по barcode, INSERT имён (ON CONFLICT DO NOTHING)
- Реализация: `BarcodeHandbookService.persistBarcodeNameBatch()` — JDBC batch

**`BH_NAME_URL`** — файл `наименование | url | site_name`:
- Поля: `name`, `brand`, `url`, `siteName`
- Продукты находятся/создаются по имени; UPSERT доменов автоматически
- Реализация: `BarcodeHandbookService.persistNameUrlBatch()` — JDBC batch

### Import Performance

Реализован через **JDBC batch** (не JPA per-row):
- `batchUpdate` + `ON CONFLICT DO NOTHING/DO UPDATE`
- Один `SELECT ... IN (...)` для поиска существующих продуктов
- ~50-100x быстрее JPA подхода (1M строк за минуты, не часы)
- `EntityPersistenceService` вызывает `saveBhBarcodeNameBatch` / `saveBhNameUrlBatch` с целым батчем

### Barcode Normalization (`BarcodeUtils`)

**Файл**: [BarcodeUtils.java](src/main/java/com/java/util/BarcodeUtils.java)

- `parseAndNormalize(String raw)` — разбивает по запятой, нормализует каждый ШК
- `normalize(String raw)` — убирает пробелы/NBSP/управляющие символы, удаляет ведущий 0 у 14-значных EAN-14 → EAN-13

Используется при импорте (`persistBarcodeNameBatch`) и при поиске (`searchAndExport`, `searchForUi`).

### Search & Export Flow

**URL**: `GET /handbook/search` → загрузка файла → `GET /handbook/search/configure` → настройка → `POST /handbook/search/process` → скачать результат

1. Загружается CSV/XLSX с рабочим файлом (ID + ШК/наименования)
2. На странице configure выбираются колонки (idColumn, barcodeColumn, nameColumn) и фильтр доменов
3. `searchAndExport()` (`@Transactional(readOnly=true)`):
   - Нормализует все ШК из файла через `BarcodeUtils.parseAndNormalize()`
   - Один batch `SELECT ... WHERE barcode IN (...)` → Map<barcode, BhProduct>
   - Для строк без найденного ШК — поиск по имени (`nameRepo.findByNameIgnoreCase`)
   - Один batch `SELECT urls WHERE product_id IN (...)` с опциональным фильтром по доменам
   - Строки без совпадений **не включаются** в результат
   - Результат: XLSX или CSV с колонками `ID | Штрихкод | Наименование | Бренд | Домен | URL`
   - Одна входная строка → N выходных строк (по числу найденных URL)

**CSV чтение**: OpenCSV с `CSVParserBuilder` (корректная обработка кавычек)

### UI Lookup (AJAX поиск на главной странице)

**URL**: `GET /handbook` — страница со встроенным поиском

**Endpoint**: `GET /handbook/lookup?q=<запрос>` → JSON

Поиск одновременно по трём полям:
1. Точное совпадение по штрихкоду (после нормализации)
2. LIKE `%запрос%` по наименованиям (`bh_names`)
3. LIKE `%запрос%` по URL (`bh_urls`)

Таблица результатов: Штрихкод | Бренд | Наименования | Домен/URL | Найдено по (бейдж ШК/Наим./URL)

### Domain Management

**URL**: `GET /handbook/domains`

- Список всех доменов из `bh_domains`, отсортированных по `url_count DESC`
- Toggle активности (отключённые домены не попадают в фильтр при поиске)
- AJAX-поиск доменов: `GET /handbook/domains/search?q=`
- Домены создаются автоматически при импорте `BH_NAME_URL`

### Key Files

| Файл | Назначение |
|------|-----------|
| [V21__create_barcode_handbook.sql](src/main/resources/db/migration/V21__create_barcode_handbook.sql) | Flyway миграция (4 таблицы) |
| [BarcodeHandbookService.java](src/main/java/com/java/service/handbook/BarcodeHandbookService.java) | Весь бизнес-код: импорт, поиск, домены |
| [BarcodeHandbookController.java](src/main/java/com/java/controller/BarcodeHandbookController.java) | REST/MVC контроллер `/handbook/*` |
| [BarcodeUtils.java](src/main/java/com/java/util/BarcodeUtils.java) | Нормализация и разбивка штрихкодов |
| [BhProduct.java](src/main/java/com/java/model/entity/BhProduct.java) | JPA entity продукта |
| [BhName.java](src/main/java/com/java/model/entity/BhName.java) | JPA entity наименования |
| [BhUrl.java](src/main/java/com/java/model/entity/BhUrl.java) | JPA entity ссылки |
| [BhDomain.java](src/main/java/com/java/model/entity/BhDomain.java) | JPA entity домена |
| [EntityType.java](src/main/java/com/java/model/enums/EntityType.java) | Добавлены BH_BARCODE_NAME, BH_NAME_URL |
| [EntityFieldService.java](src/main/java/com/java/service/EntityFieldService.java) | Добавлены поля для BH типов (UI dropdown) |
| [EntityPersistenceService.java](src/main/java/com/java/service/imports/handlers/EntityPersistenceService.java) | Switch cases для BH типов |
| [handbook/index.html](src/main/resources/templates/handbook/index.html) | Главная + AJAX поиск |
| [handbook/search-configure.html](src/main/resources/templates/handbook/search-configure.html) | Настройка поиска (маппинг колонок + домены) |

## Code References

When referencing specific functions or pieces of code include the pattern `file_path:line_number` to allow the user to easily navigate to the source code location.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
# currentDate
Today's date is 2026-02-21.
