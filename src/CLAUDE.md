# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 is a Spring Boot 3.2.3 application for client-based file processing with import/export functionality. It provides asynchronous processing of Excel and CSV files with configurable templates, statistics analysis, and a web interface for managing clients and operations.

## Key Architecture Components

### Core Modules
- **Client Management**: Client entities with associated file operations and templates
- **Import System**: Asynchronous file import with validation, duplicate checking, and error handling
- **Export System**: Multi-format export (CSV, XLSX) with template-based field mapping and statistics
- **Template Engine**: Configurable import/export templates with field mappings and filters
- **Statistics Engine**: Export statistics analysis with threshold-based reporting
- **Dashboard**: Real-time operation monitoring with WebSocket updates

### Technology Stack
- Spring Boot 3.2.3 with Java 17
- PostgreSQL database with Flyway migrations
- Thymeleaf templates for web UI
- Apache POI for Excel processing
- OpenCSV for CSV handling
- WebSocket for real-time updates
- Lombok for code generation

## Development Commands

## AI Assistant Guidelines

* Разработка ведется на ОС Windows
* Ты должен общаться на русском языке
* Не редактируй .env файл - лишь говори какие переменные нужно туда добавить
* Используй Context7 для доступа к документациям библиотек
* Для релизации любых фич с использованием интеграций с внешними арі библиотеками изучай документации с помощью context7 инструмента
* Если есть изменения на фронтенде, то в конце проверь что фронт работает, открыв его через рlaywrigh

### Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Build JAR file
mvn clean package

# Run tests (if any exist)
mvn test
```

### Database
- PostgreSQL connection configured in `application.properties`
- Flyway migrations in `src/main/resources/db/migration/`
- Default connection: `jdbc:postgresql://localhost:5432/zoomos_v4`

## Navigation Architecture

The application uses a sophisticated breadcrumb system (`BreadcrumbAdvice`) that works with URL constants (`UrlConstants`) to provide consistent navigation. The architecture is moving from tab-based navigation to separate pages:

### URL Structure
- `/clients` - Client list
- `/clients/{clientId}` - Client overview
- `/clients/{clientId}/import` - Import page
- `/clients/{clientId}/export` - Export page
- `/clients/{clientId}/templates` - Template management
- `/clients/{clientId}/statistics` - Statistics page
- `/clients/{clientId}/operations` - Operation history

### Template URLs
- `/clients/{clientId}/import/templates` - Import templates
- `/clients/{clientId}/export/templates` - Export templates
- Template CRUD: `/templates/{templateId}/edit`, `/templates/{templateId}/view`

## Service Architecture Patterns

### Async Processing
- Import: `AsyncImportService` with configurable thread pool (`import.async.*` properties)
- Export: `AsyncExportService` with configurable thread pool (`export.async.*` properties)
- Progress tracking via WebSocket and database sessions

### Strategy Pattern
- Export strategies: `DefaultExportStrategy`, `SimpleReportExportStrategy`, `TaskReportExportStrategy`
- Factory: `ExportStrategyFactory` for strategy selection

### Error Handling
- Global exception handling via `GlobalExceptionHandler` and `ImportExceptionHandler`
- Custom exceptions: `FileOperationException`, `ImportException`, `TemplateValidationException`
- Structured error messages in `ErrorMessages`

## File Processing Flow

### Import Process
1. Upload files to `/clients/{clientId}/import`
2. Analysis at `/import/{clientId}/analyze` 
3. Template selection and import start at `/import/{clientId}/start`
4. Progress monitoring at `/import/status/{operationId}`

### Export Process
1. Start export at `/export/client/{clientId}`
2. Template and operation selection at `export/start`
3. Progress monitoring at `/operations/status` with `operationId`

### Statistics Analysis
1. Setup at `/statistics/client/{clientId}` → `statistics/setup`
2. Analysis execution at `/statistics/analyze`
3. Results display at `statistics/results`

## Configuration Highlights

### Import Settings
- `import.batch-size=500` - Processing batch size
- `import.max-memory-percentage=60` - Memory usage limit
- `import.file-analysis.sample-rows=100` - File analysis sample size

### Export Settings
- `export.async.threshold-rows=10000` - Async processing threshold
- `export.batch-size=1000` - Export batch size
- `export.xlsx.max-rows=1048576` - Excel row limit

### File Locations
- Upload directory: `data/upload`
- Export results: `data/upload/exports`
- Import staging: `data/upload/imports`
- Temporary files: `data/temp`

## Development Notes

### Current Refactoring (per refactor.md)
The application is transitioning from tab-based to page-based navigation. Key changes involve:
- Converting client tabs to separate pages
- Implementing template CRUD operations
- Maintaining existing business logic while updating UI structure
- Ensuring breadcrumb consistency across new page structure

### Database Entities
- Main entities: `Client`, `ImportSession`, `ExportSession`, `ImportTemplate`, `ExportTemplate`
- Metadata tracking: `FileMetadata`, `FileOperation`
- Statistics: `ExportStatistics` with configurable analysis
- Error tracking: `ImportError` with categorization

### WebSocket Integration
- Progress updates via WebSocket for long-running operations
- Configuration in `WebSocketConfig`
- Client-side JavaScript in `main.js` for real-time updates

## Testing
Currently uses H2 in-memory database for testing. Test configuration should use `test` profile with appropriate properties.