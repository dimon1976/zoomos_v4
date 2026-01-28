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

## Quick Start (5 минут)

### Запуск приложения
```bash
# Быстрый старт с минимальным логированием (рекомендуется)
mvn spring-boot:run -Dspring-boot.run.profiles=silent
```

### Доступ к приложению
- **Главная страница**: http://localhost:8081
- **Утилиты**: http://localhost:8081/utils
- **Система обслуживания**: http://localhost:8081/maintenance
- **Анализ статистики**: http://localhost:8081/statistics/setup

### Основные разделы
1. **Import/Export** (главная страница) - Импорт и экспорт файлов для клиентов
2. **Utilities** (`/utils`) - HTTP Redirect Finder, Data Merger, Barcode Match, URL Cleaner
3. **Maintenance** (`/maintenance`) - Файловые операции, очистка БД, мониторинг системы
4. **Statistics** (`/statistics/setup`) - Анализ и сравнение операций экспорта

### Быстрая проверка работоспособности
```bash
# Проверка подключения к БД
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM clients;"

# Просмотр статуса миграций
mvn flyway:info
```

## Development Commands

### Build and Run
```bash
# Quick start (preferred method)
mvn spring-boot:run -Dspring-boot.run.profiles=silent

# Build and test
mvn clean compile                 # Compile project
mvn clean package                 # Build JAR file
mvn test                         # Run all tests
mvn test -Dtest=ClassNameTest    # Run specific test

# Run with different profiles
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=verbose
```

### Application Access
- **Main URL**: http://localhost:8081
- **Dashboard**: http://localhost:8081/
- **Maintenance System**: http://localhost:8081/maintenance
- **Utilities**: http://localhost:8081/utils

### Database Commands
```bash
# PostgreSQL connection
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM clients;"
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM file_operations;"

# View Flyway migration status
mvn flyway:info
```

## Application Profiles

- **silent** (recommended): Minimal logging, fast startup, clean output
- **dev**: Moderate logging with DevTools enabled
- **verbose**: Maximum debugging with SQL logging and TRACE level
- **prod**: Production optimized with file logging

## Recent Changes & Features

### 2026-01

- **Statistics Display Fix** - Исправлено дублирование номеров операций и TASK-номеров на странице статистики

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

### Key Features
- **Anti-Bot Bypass** - Трёхуровневая стратегия (Curl → Playwright → HttpClient) для обработки защищённых сайтов
- **WebSocket Real-time Updates** - Прогресс операций, уведомления, восстановление состояния через sessionStorage
- **Thread Pool Architecture** - 6 специализированных executor'ов для разных задач
- **Flyway Migrations** - Версионирование схемы БД с автоматической миграцией

Детальное описание каждой фичи см. в соответствующих разделах ниже.

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
- `RedirectExportTemplate` - Template for seamless integration with export system
- `RedirectProgressDto` - Progress tracking DTO for real-time WebSocket updates and user notifications
- `RedirectProcessingRequest` - Request model for async processing with URL data and configuration
- `RedirectUrlData` - Individual URL data model supporting ID and model fields

### Critical Implementation Details
**CurlStrategy User-Agent Issue**: Do NOT use User-Agent headers - many sites (goldapple.ru) block redirects when browser User-Agent is present. Manual redirect following is implemented instead of `curl -L`.

**HTTP Status Code Handling**: Capture the initial redirect HTTP code (301/302) not the final status (200). Use `initialRedirectCode` variable to preserve the first redirect status.

**JavaScript Redirect Detection**: PlaywrightStrategy successfully handles sites like Yandex Market where redirects occur via JavaScript rather than HTTP headers. Essential for e-commerce and dynamic sites.

**Dual Processing Modes**: 
- **Synchronous**: Direct file download after processing, suitable for files under 50 URLs
- **Asynchronous**: Background processing with WebSocket notifications, allows multitasking

**Asynchronous Architecture**: Fully integrated with existing async system using dedicated `redirectTaskExecutor` thread pool and WebSocket notifications via `/topic/redirect-progress/{operationId}`.

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

### Testing Strategy
```java
// Test with real data examples
@Test
public void testGoldAppleRedirect() {
    // https://goldapple.ru/qr/19000180719
    // Expected: redirect to elixir-precious product page
}
```

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

Система автоматического выполнения `VACUUM ANALYZE` после массового удаления данных для оптимизации производительности PostgreSQL.

**Configuration** ([application.properties:45-48](src/main/resources/application.properties#L45-L48)):
- `database.cleanup.auto-vacuum.enabled=true` - Включение/выключение
- `database.cleanup.auto-vacuum.threshold-records=1000000` - Триггер при удалении ≥ 1M записей
- `database.cleanup.auto-vacuum.run-async=true` - Асинхронное выполнение

**Integration** ([DataCleanupService.java:154-157](src/main/java/com/java/service/maintenance/DataCleanupService.java#L154-L157)):
- Automatically triggers VACUUM ANALYZE after deleting ≥ 1M records
- Async mode (default): runs in background via `CompletableFuture`
- Sync mode: waits for completion before returning result
- Supports all entity types: AV_DATA, IMPORT_SESSIONS, EXPORT_SESSIONS, etc.

**Key Methods**:
- `performAutoVacuum()` - Controls async/sync execution (lines 599-621)
- `executeVacuumAnalyze()` - Executes VACUUM ANALYZE for each table (lines 623-656)
- `getTableNameForEntityType()` - Maps entity type to table name (lines 658-677)

**Performance Impact**:
- For 50M row table with 10M deletions: ~15-25 minutes VACUUM time
- Improves query performance by 20-40% after large cleanups
- Does NOT block table - application continues working
- Clears dead tuples and updates table statistics

**Documentation**:
- Full guide: [VACUUM_AFTER_CLEANUP_GUIDE.md](VACUUM_AFTER_CLEANUP_GUIDE.md)
- Quick start: [VACUUM_QUICK_START.md](VACUUM_QUICK_START.md)

**Recommendation**: For tables with bloat > 30% after large cleanups, manually run REINDEX via `/maintenance/database` UI once per month.

### Asynchronous Data Cleanup with WebSocket Progress

Система асинхронной очистки устаревших данных с отображением прогресса в реальном времени через WebSocket и возможностью восстановления состояния при возврате на страницу.

**Architecture** ([AsyncConfig.java:152-167](src/main/java/com/java/config/AsyncConfig.java#L152-L167)):
- Thread pool: `cleanupTaskExecutor` (1-2 потока, 30 мин timeout)
- Выделенный executor для долгих операций очистки БД

**Key Components**:

1. **DataCleanupService** - Два режима работы:
   - `executeCleanup()` - Синхронный метод для MaintenanceController и Scheduler
   - `executeCleanupAsync()` - Асинхронный метод с `@Async("cleanupTaskExecutor")` для UI
   - `executeCleanupSync()` - Приватный метод с основной логикой

2. **Safe Batch Deletion** ([DataCleanupService.java:330-380](src/main/java/com/java/service/maintenance/DataCleanupService.java#L330-L380)):
   - Создание временной таблицы с ID для удаления
   - Удаление батчами через `cleanupAvDataBatch()` с `@Transactional(propagation = REQUIRES_NEW)`
   - Rollback возможен на уровне каждого батча
   - Прямая отправка WebSocket сообщений (без CompletableFuture)

3. **Controller Behavior** ([DataCleanupController.java:110-173](src/main/java/com/java/controller/DataCleanupController.java#L110-L173)):
   - Генерирует `operationId` и немедленно возвращает клиенту
   - Запускает async обработку через `executeCleanupAsync()` (non-blocking)
   - Клиент подключается к WebSocket для получения прогресса

4. **UI Progress Display** ([database.html:211-272](src/main/resources/templates/maintenance/database.html#L211-L272)):
   - Прогресс отображается **на странице**, не в модальном окне
   - Форма скрывается при запуске операции
   - Автоматическая прокрутка к прогрессу (`scrollIntoView`)
   - WebSocket подписка на `/topic/cleanup-progress/{operationId}`

5. **State Persistence** ([database.html:586-637](src/main/resources/templates/maintenance/database.html#L586-L637)):
   - Сохранение `operationId` в `sessionStorage` при запуске операции
   - Автоматическое восстановление прогресса при возврате на страницу
   - Очистка состояния при завершении операции
   - Состояние живёт только в рамках текущей сессии браузера

**Key Features**:
- ✅ **Асинхронное выполнение** - пользователь может работать в других вкладках
- ✅ **WebSocket прогресс** - обновления в реальном времени без polling
- ✅ **Безопасное удаление** - временные таблицы + транзакции на уровне батча
- ✅ **Восстановление состояния** - при возврате на страницу прогресс автоматически показывается
- ✅ **sessionStorage** - состояние живёт только в рамках сессии браузера
- ✅ **Progress на странице** - не блокирует работу, можно переходить между разделами

**Safety Guarantees**:
- Минимальный срок хранения данных - 7 дней (защита от случайного удаления)
- Предпросмотр перед выполнением с детализацией по типам данных
- Подтверждение через ввод "CONFIRM"
- Rollback на уровне каждого батча при ошибках
- Логирование всех операций с operationId

**Performance**:
- Выделенный пул потоков `cleanupTaskExecutor` (1-2 потока, 30 мин timeout)
- Удаление батчами (по умолчанию 10000 записей)
- Прямая отправка WebSocket без дополнительных потоков
- Оптимизация производительности через временные таблицы

**Integration with Auto-VACUUM**:
- После удаления ≥ 1M записей автоматически запускается `VACUUM ANALYZE`
- Очищает мёртвые кортежи и обновляет статистику таблиц
- Работает асинхронно, не блокирует пользователя

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
- **Error Handling**: Comprehensive validation with user-friendly error messages

## Export Statistics with Multi-Dimensional Filtering

### Purpose
Advanced statistics system for analyzing export operations with multi-dimensional filtering capabilities. Allows tracking and comparing export metrics across multiple operations, with ability to filter statistics by arbitrary field values (e.g., stock status, category, brand).

### Architecture

**Database Layer** (Flyway V15):
- `filter_field_name` VARCHAR(255) - Name of the filter field
- `filter_field_value` VARCHAR(255) - Value of the filter field
- Composite index `idx_export_statistics_filter` for optimal query performance
- Stores both general statistics (filter_field_name = NULL) and filtered statistics

**Repository Layer** (`ExportStatisticsRepository`):
- `findBySessionIdsWithoutFilter()` - Retrieves general statistics
- `findBySessionIdsAndFilter()` - Retrieves filtered statistics by field and value
- `findDistinctFilterValues()` - Gets unique filter values for a field
- `findDistinctFilterFields()` - Gets all available filter fields

**Service Layer** (`ExportStatisticsService`):
- Method overloading for backward compatibility
- `calculateComparison(request)` - General statistics
- `calculateComparison(request, filterField, filterValue)` - Filtered statistics
- Automatic deviation detection with configurable warning/critical thresholds

**API Layer** (`ExportStatisticsController`):
- `POST /statistics/analyze` - Analyze statistics with optional filter parameters
- `GET /statistics/filter-values` - Returns available filter fields and their values as JSON Map

**UI Layer** (`results.html`):
- Interactive filter panel with dynamic field/value selection
- Real-time loading of filter options via AJAX
- Active filter indicator with quick reset button
- POST-based form submission for filter application

### Key Features

**Multi-Dimensional Statistics**:
- Each export session stores general statistics (no filter) + statistics for each unique filter value
- Example: If `statisticsFilterFields = ["competitorStockStatus"]` and there are 2 unique values ("В наличии", "Нет в наличии"), then each group/metric creates 3 records: 1 general + 2 filtered
- Formula: `total_records = groups × metrics × (1 + unique_filter_values)`

**Interactive Filtering**:
- Users can filter comparison view by any configured field
- Dynamic loading of available filter values through API
- Single active filter at a time (not multi-select)
- Seamless switching between general and filtered views

**Configuration**:
- Template-level configuration via `statisticsFilterFields` JSON array
- Example: `["competitorStockStatus", "productCategory"]`
- Only configured fields appear in filter panel
- No filtering panel if `statisticsFilterFields` is null or empty

### Implementation Details

**JavaScript Integration** ([results.html:630-840](src/main/resources/templates/statistics/results.html#L630-L840)):
- `loadFilterFields()` - Загружает доступные поля и значения фильтров через API
- `onFilterFieldChange()` - Обновляет dropdown значений при выборе поля
- `applyFilter()` / `resetFilter()` - POST запросы с/без параметров фильтрации

**Data Flow**:
1. Export execution → `ExportStatisticsWriterService` saves multi-dimensional statistics
2. User opens comparison → Controller passes request/filterField/filterValue to view
3. JavaScript loads filter options → API returns Map<fieldName, List<values>>
4. User selects filter → POST request with all parameters
5. Service retrieves filtered data → `findBySessionIdsAndFilter()`
6. View renders filtered comparison table

**Performance Considerations**:
- Composite index on (export_session_id, filter_field_name, filter_field_value)
- JPQL queries with ORDER BY for consistent sorting
- Single query to fetch all filter values per field
- Client-side caching of filter data in JavaScript

### Usage Example
Template конфигурация с `statisticsFilterFields: ["competitorStockStatus"]` создаёт:
- Общую статистику для всех продуктов
- Фильтрованную статистику по каждому уникальному значению (например, "В наличии", "Нет в наличии")

### Key Components

**Services**:
- `ExportStatisticsWriterService` - Saves multi-dimensional statistics during export (lines 180-250)
- `ExportStatisticsService` - Retrieves and compares statistics with filter support (lines 85-120)
- `StatisticsSettingsService` - Manages global statistics settings

**Controllers**:
- `ExportStatisticsController` - Handles statistics requests and filter API (lines 74-119, 308-345)

**DTOs**:
- `StatisticsRequestDto` - Request parameters (templateId, sessionIds, thresholds)
- `StatisticsComparisonDto` - Comparison results with alert levels

**Views**:
- `statistics/results.html` - Interactive comparison table with filter panel (lines 387-421, 630-840)

### Testing Status
✅ Полностью протестировано - шаблоны, экспорт, фильтрация, API, UI работают корректно

### Excel Export with Trends Sheet

#### Purpose
Enhanced Excel export for statistics with dedicated "Trends" sheet showing metric changes across operations using visual symbols (↑↓=) and percentage changes.

#### Architecture

**Two-Sheet Excel Structure**:
1. **"Статистика"** - Main comparison table with detailed metrics
2. **"Тренды"** - Simplified trends view with change indicators

**Trends Sheet Format** (StatisticsExcelExportService.java:318-406):
```
Группа    | Метрика           | Операция #1  | Операция #2  | ...
----------|-------------------|--------------|--------------|-----
ОБЩЕЕ     | PRICE             | ↑ (+5.2%)   | ↓ (-2.1%)   | ...
Группа А  | DATE_MODIFICATIONS| ↓ (-4.0%)   | ↑ (+2.5%)   | ...
```

**Key Methods**:
- `writeTrendsSheet()` - Creates trends sheet with headers and data (lines 318-406)
- `formatTrendCell()` - Formats cell as "↑ (+5.2%)" or "↓ (-2.1%)" (lines 420-453)
- `determineTrendCellStyle()` - Applies color coding (green/red/gray) (lines 462-484)
- `extractMetricsByName()` - Organizes metrics by name across operations (lines 492-521)

**Visual Indicators**:
- ↑ (U+2191) + green background - Growth (WARNING/CRITICAL levels)
- ↓ (U+2193) + red/orange background - Decline (WARNING/CRITICAL levels)
- = (U+003D) + gray background - Stable (no significant change)
- "-" - No previous value for comparison (first operation)

#### Features

**Automatic Filtering**:
- Trends sheet automatically reflects active filters from results page
- Uses same data source as main statistics table
- Filter parameters passed through existing `exportToExcel()` function
- No UI changes required - works seamlessly with current implementation

**Styling Integration**:
- Reuses existing `ExcelStyles` from main sheet
- Color scheme matches main statistics table:
  - Light green (#d1e7dd) - Growth warning
  - Lime (#badbcc) - Growth critical
  - Light orange (#fff3cd) - Decline warning
  - Coral (#f8d7da) - Decline critical

**Sheet Configuration**:
- Freeze panes: First 2 rows (headers) and first 2 columns (Group + Metric)
- Auto-sized columns with 15% padding
- Fixed width for Group (30 chars) and Metric (25 chars) columns

#### Implementation Details

**Export Flow**:
1. User clicks "Экспорт в Excel" button (results.html:305, 687)
2. JavaScript sends POST to `/statistics/export/excel` with filters (results.html:1105-1153)
3. Controller calls `StatisticsExcelExportService.generateExcel()` (ExportStatisticsController.java:468-510)
4. Service creates two sheets:
   - Sheet 1: Statistics (existing functionality)
   - Sheet 2: Trends (new functionality)
5. Browser downloads file with both sheets

**Data Source**:
- Same `StatisticsComparisonDto` used for both sheets
- Trends extracted from `MetricValue.changeType` and `MetricValue.changePercentage`
- No additional database queries required

#### Usage

**Existing Workflow**:
1. Navigate to http://localhost:8081/statistics/setup
2. Select template and operations
3. Click "Анализировать"
4. (Optional) Apply filter on results page
5. Click "Экспорт в Excel" - file now contains TWO sheets

**Files Modified**:
- `StatisticsExcelExportService.java` - Added trends sheet generation
  - Modified `generateExcel()` to create two sheets (lines 45-67)
  - Added 5 new private methods for trends functionality

**No Breaking Changes**:
- Main statistics sheet unchanged
- Existing export API unchanged
- UI buttons and JavaScript unchanged
- Filter functionality works automatically

#### Testing
- ✅ Базовая интеграция завершена
- ⏳ Ручное тестирование (с фильтром и без)

## Import Date Handling (CRITICAL)

### Smart Date Format Detection for STRING Fields

Система автоматически определяет формат даты/времени при импорте Excel файлов для сохранения в STRING поля (productAdditional*, competitorAdditional*, competitorTime, competitorDate).

**Проблема**: `DataFormatter` от Apache POI применяет US локаль и преобразует русский формат `'20.10.2025 6:32:00'` в американский `'10/20/25 6:32'`.

**Решение** ([ImportProcessorService.java:716-741](src/main/java/com/java/service/imports/ImportProcessorService.java#L716-L741)):
- Определяет формат на основе точности данных (есть ли часы/минуты/секунды)
- Выбирает один из трёх форматов: `dd.MM.yyyy`, `dd.MM.yyyy HH:mm`, или `dd.MM.yyyy HH:mm:ss`
- Компактное хранение - не добавляет лишние `:00` если секунды отсутствуют

**Автоматическая адаптация формата**:

| Excel значение | Date объект | Результат в БД |
|----------------|-------------|----------------|
| `20.10.2025` | 00:00:00 | `20.10.2025` |
| `20.10.2025 06:32` | 06:32:00 | `20.10.2025 06:32` |
| `20.10.2025 06:32:45` | 06:32:45 | `20.10.2025 06:32:45` |
| `20.10.2025  6:32:00` | 06:32:00 | `20.10.2025 06:32` (унификация) |

**Преимущества**:
- ✅ Компактное хранение - не добавляет лишние `:00` для секунд
- ✅ Работает с разными файлами одновременно (с секундами и без)
- ✅ Всегда русский формат `dd.MM.yyyy`
- ✅ Без потери точности данных

**ВАЖНО**:
- Для полей типа **STRING** - применяется умное форматирование
- Для полей типа **LocalDateTime** или **Date** - используется стандартная обработка POI
- Не применяется никакая обработка для обычных текстовых строк

**Коммиты**:
- `8b45813` - Исправление конвертации дат в русский формат
- `869c50c` - Умное определение формата даты/времени

### Empty Cells for Zero Prices in Export

При экспорте данных в Excel нулевые цены (0.0) отображаются как **пустые ячейки** вместо "0.00".

**Реализация** ([XlsxFileGenerator.java:305-316](src/main/java/com/java/service/exports/generator/XlsxFileGenerator.java#L305-L316)):
- Проверка `numValue == 0.0` → `cell.setBlank()`
- Улучшенная читаемость экспортируемых файлов
- Легче отличить отсутствующие цены от реальных нулевых значений

## Statistics Operations Filtering

### Template-Based Operation Filtering

Система фильтрации операций экспорта по выбранному шаблону на странице анализа статистики. При выборе шаблона показываются только операции, выполненные с этим шаблоном.

**Architecture**:

**Controller Layer** ([ExportStatisticsController.java:53-98](src/main/java/com/java/controller/ExportStatisticsController.java#L53-L98)):
- Метод `showStatisticsSetup()` загружает все операции клиента с включённой статистикой
- Фильтрация по конкретному шаблону происходит на клиентской стороне (JavaScript)
- Упрощена логика загрузки - убрана избыточная серверная фильтрация

**Frontend Layer** ([setup.html:106-184](src/main/resources/templates/statistics/setup.html#L106-L184)):
- Блок операций скрыт по умолчанию (`display: none`)
- Добавлены информационные сообщения:
  - `#noTemplateMessage` - "Сначала выберите шаблон экспорта"
  - `#noOperationsMessage` - "Нет операций экспорта для выбранного шаблона"
  - `#operationsList` - контейнер со списком операций

**JavaScript Integration** ([setup.html:274-368](src/main/resources/templates/statistics/setup.html#L274-L368)):
- Обработчик события `change` на radio buttons шаблонов
- Функция `filterExportsByTemplate(templateId)` показывает/скрывает операции по `data-template-id`
- Автоматическое обновление состояния чекбокса "Выбрать все"

**Key Features**:
- ✅ **Client-side filtering** - быстрая работа без перезагрузки страницы
- ✅ **Smart visibility** - блок операций появляется только при выборе шаблона
- ✅ **Informative messages** - понятные подсказки для пользователя
- ✅ **Select all handling** - чекбокс "Выбрать все" работает только с видимыми операциями
- ✅ **Visual feedback** - визуальное выделение выбранного шаблона

**Implementation Details**:
1. Radio button получает класс `.template-radio` и атрибут `data-template-id`
2. При выборе срабатывает событие `change` → вызов `filterExportsByTemplate()`
3. Функция фильтрует `.export-card` по атрибуту `data-template-id`
4. Видимые операции показываются, остальные скрываются
5. Снимается выбор со скрытых операций
6. Обновляется состояние чекбокса "Выбрать все"

**Data Attributes**:
- Шаблоны: `data-template-id` на input radio
- Операции: `data-template-id` и `data-export-id` на div.export-card

**Коммит**: `a2d8f1d` - feat: фильтрация операций экспорта по выбранному шаблону статистики

### Statistics Display - Unique Operation and TASK Numbers

Исправлено дублирование номеров операций и TASK-номеров на страницах setup и results статистики.

**Проблема:**

- Номера операций дублировались из-за использования `fileOperation.id` (не уникален для разных сессий)
- TASK-номера дублировались из-за извлечения из общих sourceOperationIds вместо конкретной операции

**Решение:**

**1. Уникальные номера операций** ([results.html:516](src/main/resources/templates/statistics/results.html#L516), [setup.html:157](src/main/resources/templates/statistics/setup.html#L157)):

- Заменено `fileOperation.id` → `export.id` (exportSessionId)
- Гарантирует уникальность между setup и results страницами
- Совместимо со всеми клиентами

**2. Правильные TASK-номера** ([ExportStatisticsService.java:214-250](src/main/java/com/java/service/statistics/ExportStatisticsService.java#L214-L250)):

- Метод `extractTaskNumberFromSession()` использует ту же логику что `ExportProcessorService`
- Извлекает из **первой операции** sourceOperationIds → `av_data.product_additional1`
- Не зависит от формата имени файла экспорта
- Каждая операция экспорта получает свой TASK-номер

**3. Вспомогательный метод** ([ExportSession.java:90-98](src/main/java/com/java/model/entity/ExportSession.java#L90-L98)):

- Добавлен `@Transient` метод `getTaskNumber()` для удобного отображения на setup.html
- Извлекает TASK из имени файла через regex (для обратной совместимости)

**Key Features:**

- ✅ Консистентные номера между setup и results страницами
- ✅ Независимость от формата имени файла
- ✅ Использование того же источника данных что при экспорте
- ✅ Обратная совместимость для клиентов без TASK-номеров

**Пример отображения:**

```text
Setup page:
  Операция #123          Операция #456
  TASK-00008125         TASK-00007969

Results page:
  Операция #123   | Операция #456   ← exportSessionId (уникальны)
  TASK-00008125   | TASK-00007969   ← Разные TASK-номера
```

## Code References

When referencing specific functions or pieces of code include the pattern `file_path:line_number` to allow the user to easily navigate to the source code location.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.