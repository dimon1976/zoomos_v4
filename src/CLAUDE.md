# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 is a Spring Boot 3.2.12 application for client-based file processing with import/export functionality. It provides asynchronous processing of Excel and CSV files with configurable templates, statistics analysis, and a web interface for managing clients and operations.

## Development Commands

### Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Build JAR file
mvn clean package

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=ClassNameTest

# Run application with specific profiles
mvn spring-boot:run -Dspring-boot.run.profiles=silent
mvn spring-boot:run -Dspring-boot.run.profiles=verbose
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Quick start with existing JAR (fastest)
java -jar target/file-processing-app-1.0-SNAPSHOT.jar --spring.profiles.active=silent

# Run with batch scripts (Windows)
start-dev.bat
```

### Database
- PostgreSQL connection: `jdbc:postgresql://localhost:5432/zoomos_v4`
- Flyway migrations in `src/main/resources/db/migration/`
- Test profile uses H2 in-memory database
- **Important**: JPA DDL auto is disabled (`spring.jpa.hibernate.ddl-auto=none`)

### Application Profiles
- **dev** (default): Moderate logging, DevTools enabled, reduced batch sizes
- **silent**: Minimal logging for testing, same performance as dev
- **verbose**: Maximum debugging with SQL logging and TRACE level
- **prod**: Production optimized with file logging and maximum performance

## Architecture Overview

### Core Processing Flow
The application follows a request-driven processing model with three main flows:

1. **Import Flow**: Upload → Analyze → Template Selection → Async Processing → Status Monitoring
2. **Export Flow**: Template Selection → Operation Selection → Async Processing → File Generation
3. **Statistics Flow**: Setup → Analysis → Results Visualization

### Key Architectural Patterns

**Async Processing Architecture**
- Dual async executors: `ImportExecutor-*` and `ExportExecutor-*` thread pools
- Progress tracking via WebSocket (`WebSocketConfig`) and database sessions
- Configurable thread pools via `import.async.*` and `export.async.*` properties

**Strategy Pattern for Export Processing**
- `ExportStrategyFactory` selects between strategies: `DefaultExportStrategy`, `SimpleReportExportStrategy`, `TaskReportExportStrategy`
- Each strategy handles different export requirements and data formats

**Template-Based Processing**
- `ImportTemplate` and `ExportTemplate` entities with field mappings and filters
- Templates are client-specific and support cloning
- Field validation through `TemplateValidationService`

### Navigation Architecture

**URL Structure** (defined in `UrlConstants`)
```
/clients                           # Client list
/clients/{clientId}                # Client overview (main page)
/clients/{clientId}/import         # Import page
/clients/{clientId}/export         # Export page
/clients/{clientId}/templates      # Template management
/clients/{clientId}/statistics     # Client statistics
/clients/{clientId}/operations     # Operation history
/clients/{clientId}/import/templates/{templateId}  # Template CRUD
/maintenance                       # Maintenance system overview
/maintenance/files                 # File maintenance operations
/maintenance/database              # Database maintenance operations  
/maintenance/system                # System health monitoring
/maintenance/operations            # Scheduled maintenance operations
```

**Breadcrumb System**
- `BreadcrumbAdvice` provides consistent navigation breadcrumbs
- Works with URL constants to maintain navigation state
- Currently transitioning from tab-based to page-based navigation

### Service Layer Organization

**Client Services**
- `ClientService` - Client management and CRUD operations
- `DashboardService` - Cross-client statistics and monitoring

**Processing Services**
- `AsyncImportService` / `AsyncExportService` - Asynchronous file processing
- `ImportProcessorService` / `ExportProcessorService` - Core processing logic
- `FileAnalyzerService` - File structure analysis and validation

**Template Services**
- `ImportTemplateService` / `ExportTemplateService` - Template management
- `TemplateValidationService` - Template field validation

**Data Services**
- `EntityPersistenceService` - Database operations for imported data
- `DuplicateCheckService` - Duplicate detection and handling
- `ExportDataService` - Data retrieval for exports

**Maintenance Services**
- `MaintenanceSchedulerService` - Automated maintenance tasks with @Scheduled annotations
- `MaintenanceNotificationService` - Real-time notifications via WebSocket
- `DatabaseMaintenanceService` - Database optimization and cleanup operations
- `FileMaintenanceService` - File system maintenance and archival
- `SystemHealthService` - System health monitoring and diagnostics

## Configuration System

### Performance Tuning
```properties
# Import processing
import.batch-size=500
import.max-memory-percentage=60
import.timeout-minutes=60

# Export processing  
export.async.threshold-rows=10000
export.batch-size=1000
export.xlsx.max-rows=1048576

# Thread pool configuration
import.async.core-pool-size=1
import.async.max-pool-size=2
export.async.core-pool-size=2
export.async.max-pool-size=4
```

### File Handling
```properties
# File size limits
spring.servlet.multipart.max-file-size=1200MB
spring.servlet.multipart.max-request-size=1200MB

# Directory structure
application.upload.dir=data/upload
application.export.dir=data/upload/exports
application.temp.dir=data/temp
```

### Maintenance System Configuration
```properties
# Maintenance scheduler (disabled by default for safety)
maintenance.scheduler.enabled=false

# Scheduled tasks with cron expressions
maintenance.scheduler.file-archive.cron=0 0 2 * * *           # Daily at 02:00
maintenance.scheduler.database-cleanup.cron=0 0 3 * * SUN     # Weekly Sunday at 03:00
maintenance.scheduler.health-check.cron=0 0 * * * *           # Hourly
maintenance.scheduler.performance-analysis.cron=0 0 1 * * MON # Weekly Monday at 01:00
maintenance.scheduler.full-maintenance.cron=0 0 4 1 * *       # Monthly 1st at 04:00

# File and database maintenance
file.management.archive.enabled=true
file.management.archive.max.size.gb=5
database.maintenance.cleanup.old-data.days=120
database.maintenance.performance.slow-query-threshold=1000

# System health thresholds
system.health.cpu.warning-threshold=80.0
system.health.memory.warning-threshold=85.0
system.health.disk.warning-threshold=90.0
```

## Data Model

### Core Entities
- `Client` - Central entity linking all operations
- `FileOperation` - Tracks all import/export operations with status and progress
- `ImportSession` / `ExportSession` - Operation-specific data and configuration
- `ImportTemplate` / `ExportTemplate` - Processing templates with field mappings

### Statistics and Monitoring
- `ExportStatistics` - Analysis results with configurable thresholds
- `ImportError` - Error tracking with categorization
- `FileMetadata` - File analysis results and structure information

### Repository Pattern
- All repositories extend `JpaRepository` with `JpaSpecificationExecutor`
- Custom queries use native SQL for PostgreSQL-specific features
- Statistics queries use aggregation functions and window functions

## Error Handling Strategy

**Exception Hierarchy**
- `FileOperationException` - Base for all file processing errors
- `ImportException` - Import-specific errors
- `TemplateValidationException` - Template configuration errors

**Global Error Handling**
- `GlobalExceptionHandler` - Application-wide exception handling
- `ImportExceptionHandler` - Specialized import error handling
- `ErrorMessages` - Centralized error message constants

## WebSocket Integration

Real-time progress updates for long-running operations:
- Progress updates broadcast to `/topic/progress/{operationId}`
- Client-side JavaScript in `main.js` handles WebSocket connections
- Automatic reconnection and error handling

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.2.3 with Java 17
- **Database**: PostgreSQL with Flyway migrations
- **File Processing**: Apache POI 5.2.3 (Excel), OpenCSV 5.8 (CSV)
- **Character Detection**: juniversalchardet 2.4.0
- **Template Engine**: Thymeleaf with Java 8 Time extras
- **WebSockets**: Spring WebSocket with SockJS/STOMP
- **Development**: Spring DevTools, Lombok

### Key Dependencies
```xml
<apache.poi.version>5.2.3</apache.poi.version>
<opencsv.version>5.8</opencsv.version>
<juniversalchardet.version>2.4.0</juniversalchardet.version>
```

### Testing Stack
- Spring Boot Test with H2 in-memory database for tests
- Test profile automatically switches to H2 from PostgreSQL
- Test classes in `src/test/java/com/java/service/`
- Integration tests include FileAnalyzer, ImportBackslash, Normalization, and Operations

## Development Environment

### Application Profiles
- **verbose** (default): Standard logging, detailed output
- **silent**: Minimal logging for production-like environment

### Server Configuration
- **Port**: 8081 (configured in application.properties)
- **Hot Reload**: Spring DevTools enabled with LiveReload
- **Thymeleaf**: Cache disabled for development

## AI Assistant Guidelines

* Разработка ведется на ОС Windows
* Ты должен общаться на русском языке
* Не редактируй .env файл - лишь говори какие переменные нужно туда добавить
* Используй Context7 для доступа к документациям библиотек
* Для релизации любых фич с использованием интеграций с внешними арі библиотеками изучай документации с помощью context7 инструмента
* Если есть изменения на фронтенде, то в конце проверь что фронт работает, открыв его через рlaywrigh
* Это мой pet проект, не нужно стремится усложнять и использовать какие-то сложные паттерны проектирования.
* Если чего-то не знаешь, не придумывай, так и говори.
* Обязательно закрывать запущенный сервер после тестирования
* Проектируем код по принципам KISS, YAGNI, MVP, Fail Fast, итеративная разработка.

## Current State Notes

**Recently Completed** (as of 2025-08-30)
- **Maintenance System**: Complete automated maintenance system with web interface
  - Web interface at `/maintenance` with 5 pages (index, files, database, system, operations)
  - Scheduled tasks with configurable cron expressions  
  - Real-time WebSocket notifications for maintenance operations
  - Profile-based configuration for dev/prod environments
  - Safety: scheduler disabled by default (`maintenance.scheduler.enabled=false`)
- **Database Configuration**: JPA DDL auto disabled for production stability
- **Navigation**: Completed transition from tab-based to page-based navigation
- **Template CRUD**: Full CRUD operations implemented with proper URL structure

**Refactoring Completed**
- Eliminated controller duplication with `ControllerUtils`
- Merged async configurations (`AsyncConfig`) 
- Simplified template services with `TemplateUtils`
- Consolidated exception handlers in `GlobalExceptionHandler`
- Created base classes for file generators (`AbstractFileGenerator`)
- Removed unnecessary dependencies (WebJars, Java8Time extras)

**Navigation Flow**
1. **Import**: Upload → Analyze (`/import/{clientId}/analyze`) → Template Selection → Start (`/import/{clientId}/start`) → Status (`/import/status/{operationId}`)
2. **Export**: Export Page → Start Form (`/export/client/{clientId}`) → Status (`/export/status/{operationId}`)
3. **Statistics**: Setup (`/statistics/client/{clientId}`) → Analyze (`/statistics/analyze`) → Results (`/statistics/results`)

**JavaScript Patterns**
- Client ID extraction from URL paths rather than Thymeleaf inlining
- Global variables for shared state between functions
- Error handling with user-friendly messages and HTTP status codes
- Automatic data refresh every 30 seconds for statistics pages