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
run-utf8.bat                      # Windows batch script with UTF-8 encoding
# or
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
- `CurlStrategy`, `PlaywrightStrategy` - Strategies for bypass anti-bot systems

## HTTP Redirect Utility

### Purpose
Processes CSV/Excel files containing URLs to resolve final URLs after HTTP redirects (301, 302, 307, 308).

### Strategy Pattern for Anti-Bot Bypass
1. **CurlStrategy** (Priority 1): System curl commands for maximum compatibility
2. **PlaywrightStrategy** (Priority 2): Headless browser for blocked sites
3. **HttpClientStrategy** (Priority 3): Java HTTP Client fallback

### Key Components
- `RedirectFinderController` - Web interface for file upload/processing
- `RedirectFinderService` - Core business logic
- `RedirectStrategy` - Interface for processing strategies
- `RedirectResult` - Result model with URL, redirect count, status

### Configuration
```java
// Maximum ~950 lines of code total
// Uses KISS principle - minimal classes, straightforward logic
// MVP approach - basic functionality only
```

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