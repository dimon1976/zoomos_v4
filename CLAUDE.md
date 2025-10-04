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
- Maintenance system notifications (`/topic/notifications`)
- Client-side JavaScript handles connections and reconnection

## AI Development Guidelines

- При старте используй команду ‑dangerously‑skip‑permissions
- Разработка ведется на ОС Windows
- Общение на русском языке
- Используй Context7 для изучения документации библиотек
- Закрывай запущенный сервер после тестирования
- Следуй принципам KISS, YAGNI, MVP, итеративная разработка
- Не усложняй код без необходимости - это pet проект

## Current State Notes

**Recently Completed Data Merger Utility Development** (as of 2025-09-22):
- **Complete Implementation**: Data Merger utility with dual-file processing capability
- **Enhanced File Processing**: Merges source products with link data using configurable column mapping
- **Strategy Integration**: Uses existing FileAnalyzerService and FileGeneratorService architecture
- **User Interface**: Bootstrap-based form with dynamic column mapping and progress tracking
- **Error Handling**: Comprehensive validation and user-friendly error messages
- **Testing**: Real-world testing with sample data files and various formats

**Previously Completed HTTP Redirect Utility Enhancements** (as of 2025-09-07):
- **Async Processing**: Complete dual-mode architecture - background processing for large files and quick sync mode for small files
- **Enhanced UI**: Two processing modes with clear explanations and user guidance
- **Strategy Pattern**: Fully tested three-tier system (CurlStrategy → PlaywrightStrategy → HttpClientStrategy)
- **Real-world Testing**: Successfully handles complex sites like Yandex Market with JavaScript redirects
- **Parameter Documentation**: Detailed tooltips explaining timeout and delay settings
- **Export Integration**: Full integration with FileGeneratorService for consistent CSV/Excel export
- **WebSocket Notifications**: Real-time progress tracking and completion alerts

**Proven Capabilities**:
- **Anti-Bot Bypass**: Successfully processes e-commerce sites with complex protection (Yandex Market, Goldapple)
- **JavaScript Redirects**: Playwright strategy handles JS-based redirects that curl cannot detect  
- **Large File Processing**: Async mode allows processing thousands of URLs while working on other tasks
- **Encoding Handling**: Properly handles UTF-8 files with international characters
- **Production Ready**: Comprehensive error handling, progress tracking, and user feedback

**Architecture Insights**:
- Uses dedicated `redirectTaskExecutor` thread pool (1-3 threads) for HTTP operations
- Manual redirect following instead of curl -L to avoid User-Agent detection
- Preserves initial redirect HTTP codes (301/302) rather than final status (200)
- Configurable delay between requests (0-5 seconds) to prevent rate limiting
- Intelligent strategy escalation based on response analysis
- Seamless integration with existing async/WebSocket notification system

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

**Recently Completed** (as of 2025-10-04):

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

**JavaScript Integration**:
```javascript
// Loads available filter fields and values via API
loadFilterFields() - Fetches /statistics/filter-values?templateId=X&sessionIds=1,2,3

// Dynamic UI updates
onFilterFieldChange() - Populates value dropdown when field selected
onFilterValueChange() - Enables "Apply" button when value selected

// Form submission (POST)
applyFilter() - Submits form with templateId, sessionIds, filterField, filterValue
resetFilter() - Submits form without filter parameters
```

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

**Template Configuration**:
```json
{
  "statisticsGroupField": "product_additional4",
  "statisticsCountFields": ["competitor_price", "competitorPromotionalPrice"],
  "statisticsFilterFields": ["competitorStockStatus"]
}
```

**Result**:
- General statistics: Shows totals for all products
- Filtered by "В наличии": Shows metrics only for products in stock
- Filtered by "Нет в наличии": Shows metrics only for out-of-stock products

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

### Testing Checklist

✅ Шаблон с фильтрацией создаётся корректно
✅ Экспорт сохраняет многомерную статистику в БД
✅ API `/statistics/filter-values` возвращает корректные данные
✅ Панель фильтров показывается только при наличии `statisticsFilterFields`
✅ Динамическая загрузка значений работает
✅ Применение фильтра перезагружает с корректными данными
✅ Индикатор активного фильтра отображается
✅ Сброс фильтра возвращает к общей статистике
✅ Множественные операции корректно сравниваются
✅ Данные в БД соответствуют формуле расчёта

## Code References

When referencing specific functions or pieces of code include the pattern `file_path:line_number` to allow the user to easily navigate to the source code location.

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.