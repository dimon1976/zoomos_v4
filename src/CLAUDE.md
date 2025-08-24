# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 is a Spring Boot 3.2.3 application for client-based file processing with import/export functionality. It provides asynchronous processing of Excel and CSV files with configurable templates, statistics analysis, and a web interface for managing clients and operations.

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

# Run application with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=silent
```

### Database
- PostgreSQL connection: `jdbc:postgresql://localhost:5432/zoomos_v4`
- Flyway migrations in `src/main/resources/db/migration/`
- Test profile uses H2 in-memory database

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

## AI Assistant Guidelines

## AI Assistant Guidelines

* Разработка ведется на ОС Windows
* Ты должен общаться на русском языке
* Не редактируй .env файл - лишь говори какие переменные нужно туда добавить
* Используй Context7 для доступа к документациям библиотек
* Для релизации любых фич с использованием интеграций с внешними арі библиотеками изучай документации с помощью context7 инструмента
* Если есть изменения на фронтенде, то в конце проверь что фронт работает, открыв его через рlaywrigh

## Current State Notes

**Active Refactoring** (per `refactor.md`)
- Converting from tab-based navigation to separate pages
- Implementing template CRUD operations
- URL structure follows `UrlConstants` definitions
- All existing business logic is preserved during UI restructuring

**JavaScript Patterns**
- Client ID extraction from URL paths rather than Thymeleaf inlining
- Global variables for shared state between functions
- Error handling with user-friendly messages and HTTP status codes
- Automatic data refresh every 30 seconds for statistics pages