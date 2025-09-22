# Zoomos v4 - Специализированные агенты

Данный документ описывает 10 специализированных агентов для кодовой базы Zoomos v4, их особенности и примеры использования.

## 📋 Обзор агентов

| Агент | Область | Приоритет | Основные задачи |
|-------|---------|-----------|-----------------|
| performance-optimizer | Производительность | Высокий | Thread pools, memory, SQL оптимизация |
| template-wizard | Шаблоны | Средний | Import/Export шаблоны и валидация |
| database-maintenance-specialist | База данных | Высокий | PostgreSQL и Flyway миграции |
| error-analyzer | Обработка ошибок | Высокий | Анализ исключений и error handling |
| websocket-enhancer | Real-time | Средний | WebSocket уведомления и прогресс |
| file-processing-expert | Файлы | Высокий | CSV/Excel обработка и оптимизация |
| async-architecture-specialist | Асинхронность | Средний | Async операции и координация |
| security-auditor | Безопасность | Средний | Security audit и валидация |
| ui-modernizer | Frontend | Низкий | Thymeleaf и UI улучшения |
| monitoring-dashboard-builder | Мониторинг | Низкий | Дашборды и системный мониторинг |

---

## 🔧 Детальное описание агентов

### 1. performance-optimizer

**Специализация:** Оптимизация производительности файловой обработки и системных ресурсов

**Ключевые области:**
- `AsyncConfig.java` - настройка thread pools (importTaskExecutor, exportTaskExecutor, redirectTaskExecutor)
- Memory management для больших файлов (лимит 1.2GB)
- PostgreSQL query optimization и connection pooling
- WebSocket performance для real-time уведомлений

**Особенности применения к Zoomos v4:**
```java
// Анализирует и оптимизирует thread pool конфигурации
@Value("${import.async.core-pool-size:1}")
private int importCorePoolSize;

// Оптимизирует batch размеры для больших файлов
import.batch-size=500  →  оптимальное значение на основе memory profiling

// Настраивает connection pool для PostgreSQL
spring.datasource.hikari.maximum-pool-size=20
```

**Практические примеры:**
- Оптимизация обработки Excel файлов размером 500MB+ в `ImportProcessorService`
- Тюнинг thread pool для одновременной обработки 10+ export операций
- Решение memory leak в `FileAnalyzerService` при анализе множественных файлов
- Оптимизация SQL запросов в `StatisticsService` для быстрой генерации отчетов

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 2. template-wizard

**Специализация:** Автоматизация создания и управления шаблонами import/export

**Ключевые области:**
- `ImportTemplateService` и `ExportTemplateService`
- `TemplateValidationService` - валидация полей и маппинга
- `ExportStrategyFactory` - управление стратегиями экспорта
- Нормализаторы: `BrandNormalizer`, `CurrencyNormalizer`, `VolumeNormalizer`

**Особенности применения к Zoomos v4:**
```java
// Создание шаблонов на основе анализа FileAnalyzerService
FileStructureDto structure = fileAnalyzerService.analyzeFile(file);
ImportTemplate template = createTemplateFromStructure(structure);

// Интеграция с existing validation
@Valid @NotNull ImportTemplateFieldDto fieldDto
TemplateValidationService.validateFieldMapping(fieldDto);

// Использование ExportStrategy pattern
ExportStrategy strategy = exportStrategyFactory.getStrategy(exportType);
```

**Практические примеры:**
- Автоматическое создание import шаблонов для новых клиентов на основе их файловой структуры
- Создание specialized шаблонов для e-commerce товаров с barcode validation
- Миграция template конфигураций между staging и production
- Создание export шаблонов с custom Excel styling через `ExcelStyleFactory`

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 3. database-maintenance-specialist

**Специализация:** PostgreSQL администрирование и Flyway миграции

**Ключевые области:**
- Flyway migrations в `src/main/resources/db/migration/`
- `DatabaseMaintenanceService` - автоматическая очистка и оптимизация
- PostgreSQL performance tuning и indexing
- HikariCP connection pool оптимизация

**Особенности применения к Zoomos v4:**
```sql
-- Создание Flyway миграций для новых индексов
-- V1.15__optimize_file_operations_queries.sql
CREATE INDEX CONCURRENTLY idx_file_operations_status_created
ON file_operations(status, created_at) WHERE status IN ('COMPLETED', 'FAILED');

-- Оптимизация cleanup procedures
DELETE FROM import_errors WHERE created_at < NOW() - INTERVAL '120 days';
```

**Практические примеры:**
- Создание индексов для улучшения производительности `ClientService.findOperationsByStatus()`
- Flyway миграции для добавления новых полей в `ImportTemplate` entity
- Оптимизация slow queries в statistics generation (dashboard analytics)
- Автоматизация cleanup старых `FileOperation` записей через scheduled tasks

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 4. error-analyzer

**Специализация:** Анализ и улучшение системы обработки ошибок

**Ключевые области:**
- Exception hierarchy: `FileOperationException`, `ImportException`, `TemplateValidationException`
- `GlobalExceptionHandler` и `ImportExceptionHandler`
- Async error handling с WebSocket notifications
- `ImportError` entity и error categorization

**Особенности применения к Zoomos v4:**
```java
// Анализ error patterns в ImportProcessorService
@ExceptionHandler(ImportException.class)
public ResponseEntity<ErrorResponse> handleImportException(ImportException ex) {
    // Улучшенная категоризация ошибок
    ErrorType errorType = determineErrorType(ex);
    notificationService.sendErrorNotification(operationId, errorType);
}

// WebSocket error notifications
websocketTemplate.convertAndSend("/topic/error/" + operationId, errorDto);
```

**Практические примеры:**
- Улучшение error recovery в `AsyncImportService` при частичных сбоях импорта
- Категоризация ошибок в `RedirectFinderService` по типам (timeout, SSRF, invalid URL)
- Создание user-friendly error messages для template validation failures
- Анализ error patterns в async операциях для улучшения retry logic

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 5. websocket-enhancer

**Специализация:** Real-time уведомления и WebSocket оптимизация

**Ключевые области:**
- `WebSocketConfig.java` STOMP configuration
- Progress channels: `/topic/progress/{operationId}`, `/topic/redirect-progress/{operationId}`
- `NotificationService` и real-time messaging
- Client-side JavaScript WebSocket integration

**Особенности применения к Zoomos v4:**
```java
// Оптимизация WebSocket конфигурации
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic", "/queue")
          .setHeartbeatValue(new long[]{10000, 20000}); // оптимизация heartbeat
}

// Enhanced progress tracking
ProgressDto progress = ProgressDto.builder()
    .operationId(operationId)
    .percentage(completed * 100 / total)
    .currentFile(filename)
    .estimatedTimeRemaining(estimateTimeRemaining())
    .build();
```

**Практические примеры:**
- Улучшение granularity progress tracking для больших Excel файлов (каждые 1000 строк)
- Реализация reconnection logic для unstable WebSocket connections
- Оптимизация message frequency для better performance (batch updates)
- Добавление real-time notifications для maintenance system events

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 6. file-processing-expert

**Специализация:** CSV/Excel обработка и оптимизация файловых форматов

**Ключевые области:**
- `FileAnalyzerService` - анализ структуры и encoding detection
- Apache POI оптимизация для Excel files
- OpenCSV configuration и custom delimiters
- `XlsxFileGenerator`, `CsvFileGenerator` с memory-efficient streaming

**Особенности применения к Zoomos v4:**
```java
// Оптимизация Apache POI для больших файлов
Workbook workbook = new SXSSFWorkbook(1000); // streaming для memory efficiency

// Character encoding detection improvement
Charset detectedCharset = UniversalDetector.detectCharset(inputStream);
if (detectedCharset == null) {
    detectedCharset = StandardCharsets.UTF_8; // fallback
}

// Custom CSV configuration
CSVFormat csvFormat = CSVFormat.DEFAULT
    .withDelimiter(';')
    .withQuote('"')
    .withFirstRecordAsHeader();
```

**Практические примеры:**
- Оптимизация memory usage для Excel файлов 100K+ строк в `ImportProcessorService`
- Улучшение character encoding detection для international content (кириллица)
- Реализация streaming processing для very large files (избежание OutOfMemoryError)
- Добавление support для additional Excel features (формулы, chart data)

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 7. async-architecture-specialist

**Специализация:** Асинхронная архитектура и координация операций

**Ключевые области:**
- `AsyncConfig.java` с 5 специализированными executors
- `AsyncImportService`, `AsyncExportService`, `AsyncRedirectService`
- `BaseProgressService` и progress coordination
- `OperationDeletionService` для cleanup

**Особенности применения к Zoomos v4:**
```java
// Thread pool optimization на основе workload analysis
@Bean("importTaskExecutor")
public TaskExecutor importTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);    // dynamic based on CPU cores
    executor.setMaxPoolSize(maxPoolSize);      // based on memory available
    executor.setQueueCapacity(queueCapacity);  // based on operation frequency
    return executor;
}

// Improved operation coordination
@Async("importTaskExecutor")
public CompletableFuture<ImportResult> processImport(ImportRequest request) {
    // Enhanced cancellation support and resource cleanup
}
```

**Практические примеры:**
- Тюнинг executor configurations на основе actual workload patterns
- Реализация proper cancellation для long-running redirect operations
- Улучшение coordination между multiple concurrent async operations
- Оптимизация resource usage across concurrent import/export processes

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 8. security-auditor

**Специализация:** Security audit и защита от уязвимостей

**Ключевые области:**
- File upload security и path traversal protection
- `UrlSecurityValidator` для SSRF protection в redirect processing
- Input validation в template processing
- Access control и authorization

**Особенности применения к Zoomos v4:**
```java
// Enhanced file upload validation
@Component
public class FileSecurityValidator {
    private static final Set<String> ALLOWED_EXTENSIONS =
        Set.of(".xlsx", ".csv", ".xls");

    public void validateFile(MultipartFile file) {
        validateFileType(file);
        validateFileSize(file);
        scanForMaliciousContent(file);
    }
}

// SSRF protection в RedirectFinderService
public boolean isUrlSafe(String url) {
    return !isPrivateNetwork(url) &&
           !isLocalhost(url) &&
           isHttpsOrHttp(url);
}
```

**Практические примеры:**
- Hardening file upload validation против malicious files
- Улучшение SSRF protection в `PlaywrightStrategy` и `CurlStrategy`
- Добавление input sanitization в template field processing
- Audit authorization logic для sensitive maintenance operations

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 9. ui-modernizer

**Специализация:** Frontend улучшения и пользовательский интерфейс

**Ключевые области:**
- Thymeleaf templates оптимизация
- Bootstrap 5.3.0 components и responsive design
- JavaScript ES6+ и WebSocket client
- `BreadcrumbAdvice` и navigation improvements

**Особенности применения к Zoomos v4:**
```html
<!-- Enhanced progress visualization -->
<div class="progress" style="height: 25px;">
    <div class="progress-bar progress-bar-striped progress-bar-animated"
         th:style="'width: ' + ${progress.percentage} + '%'">
        <span th:text="${progress.currentFile}">Processing file...</span>
    </div>
</div>

<!-- Mobile-optimized file upload -->
<div class="col-12 col-md-6">
    <div class="card border-primary">
        <div class="card-body">
            <input type="file" class="form-control"
                   accept=".xlsx,.csv,.xls" multiple>
        </div>
    </div>
</div>
```

**Практические примеры:**
- Улучшение mobile responsiveness для file processing interfaces
- Создание reusable Bootstrap components для common operations
- Enhanced progress bars с detailed information (ETA, current file, speed)
- Accessibility improvements и keyboard navigation support

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

### 10. monitoring-dashboard-builder

**Специализация:** Системный мониторинг и создание дашбордов

**Ключевые области:**
- `SystemHealthService` и comprehensive health checks
- `MaintenanceSchedulerService` с automated tasks
- `DashboardService` и real-time statistics
- Performance metrics collection и visualization

**Особенности применения к Zoomos v4:**
```java
// Enhanced system health monitoring
@Component
public class EnhancedSystemHealthService {
    public SystemHealthDto getDetailedHealth() {
        return SystemHealthDto.builder()
            .cpuUsage(getCpuUsage())
            .memoryUsage(getMemoryUsage())
            .diskUsage(getDiskUsage())
            .databaseHealth(checkDatabaseHealth())
            .activeOperations(getActiveOperationsCount())
            .threadPoolStatus(getThreadPoolStatus())
            .build();
    }
}

// Real-time dashboard updates
@Scheduled(fixedRate = 30000) // каждые 30 секунд
public void updateDashboardMetrics() {
    DashboardStatsDto stats = calculateCurrentStats();
    websocketTemplate.convertAndSend("/topic/dashboard/stats", stats);
}
```

**Практические примеры:**
- Создание comprehensive dashboard для monitoring всех async operations
- Реализация alerting system для critical system events
- Enhanced maintenance scheduling с predictive maintenance
- Performance metrics visualization для optimization insights

**Инструменты:** Read, Edit, MultiEdit, Bash, Grep, Glob

---

## 🔄 Параллельное выполнение агентов

### Принципы параллельной работы

Агенты можно запускать параллельно используя Task tool с multiple tool calls в одном сообщении. Это максимизирует производительность и позволяет решать комплексные задачи.

### Совместимые пары агентов

#### 1. Performance + Async Architecture
```
Одновременная оптимизация производительности и асинхронной архитектуры
```

**Пример использования:**
```markdown
Мне нужно оптимизировать систему для обработки 50+ одновременных import операций.
Запусти параллельно performance-optimizer и async-architecture-specialist.
```

**Результат:**
- performance-optimizer анализирует memory usage и database connections
- async-architecture-specialist оптимизирует thread pools и coordination
- Синергия: улучшенная пропускная способность системы

#### 2. Template + File Processing
```
Совместная работа над шаблонами и файловой обработкой
```

**Пример использования:**
```markdown
Клиент загружает новый формат Excel файлов с нестандартной структурой.
Запусти параллельно template-wizard и file-processing-expert.
```

**Результат:**
- template-wizard создает новые шаблоны валидации
- file-processing-expert оптимизирует парсинг нового формата
- Синергия: seamless integration нового файлового формата

#### 3. Error Analysis + WebSocket Enhancement
```
Улучшение error handling и real-time notifications
```

**Пример использования:**
```markdown
Пользователи жалуются на непонятные ошибки и отсутствие уведомлений об ошибках.
Запусти параллельно error-analyzer и websocket-enhancer.
```

**Результат:**
- error-analyzer улучшает error messages и categorization
- websocket-enhancer добавляет real-time error notifications
- Синергия: comprehensive error communication system

#### 4. Database + Security
```
Database maintenance с security audit
```

**Пример использования:**
```markdown
Нужно провести полное обслуживание базы данных с проверкой безопасности.
Запусти параллельно database-maintenance-specialist и security-auditor.
```

**Результат:**
- database-maintenance-specialist оптимизирует queries и создает migrations
- security-auditor проверяет SQL injection vulnerabilities
- Синергия: secure и optimized database operations

### Примеры параллельного запуска

#### Сценарий 1: Комплексная оптимизация производительности
```markdown
Система работает медленно под нагрузкой. Нужна комплексная оптимизация.

Запусти параллельно:
1. performance-optimizer - анализ thread pools и memory usage
2. async-architecture-specialist - оптимизация async coordination
3. database-maintenance-specialist - оптимизация SQL queries
```

**Код запуска:**
```markdown
// Пользователь отправляет одно сообщение с тремя Task calls

Task 1: performance-optimizer
Анализирует AsyncConfig.java, memory usage в FileAnalyzerService, connection pooling

Task 2: async-architecture-specialist
Оптимизирует coordination между ImportExecutor и ExportExecutor

Task 3: database-maintenance-specialist
Создает индексы для ClientService queries и оптимизирует cleanup procedures
```

#### Сценарий 2: Новый клиент с особыми требованиями
```markdown
Новый крупный клиент с уникальными файловыми форматами и высокими требованиями к безопасности.

Запусти параллельно:
1. template-wizard - создание специализированных шаблонов
2. file-processing-expert - поддержка новых форматов
3. security-auditor - дополнительные security checks
```

#### Сценарий 3: Modernization sprint
```markdown
Спринт по модернизации пользовательского интерфейса и мониторинга.

Запусти параллельно:
1. ui-modernizer - улучшение frontend компонентов
2. websocket-enhancer - enhanced real-time features
3. monitoring-dashboard-builder - новые дашборды мониторинга
```

#### Сценарий 4: Critical issue resolution
```markdown
Критическая проблема: ошибки в async операциях с потерей данных.

Запусти параллельно:
1. error-analyzer - анализ error patterns и recovery
2. async-architecture-specialist - исправление async coordination
3. database-maintenance-specialist - data integrity checks
```

### Координация между агентами

#### Shared context
Агенты работают с общей кодовой базой и могут ссылаться на изменения друг друга:

```java
// performance-optimizer создает новую конфигурацию
@Value("${import.async.optimized-batch-size:2000}")
private int optimizedBatchSize;

// async-architecture-specialist использует эту конфигурацию
executor.setCorePoolSize(calculateOptimalCoreSize(optimizedBatchSize));
```

#### Conflict resolution
При конфликтующих изменениях приоритет имеет агент с более высоким priority:
1. Высокий: performance-optimizer, database-maintenance-specialist, error-analyzer
2. Средний: template-wizard, websocket-enhancer, async-architecture-specialist
3. Низкий: ui-modernizer, monitoring-dashboard-builder

#### Communication patterns
Агенты могут reference работу друг друга:

```markdown
performance-optimizer: "Создал оптимизированную конфигурацию thread pool"
async-architecture-specialist: "Использую новую конфигурацию для coordination logic"
```

### Best practices параллельного запуска

#### 1. Логическая группировка
Объединяй агентов по логическим областям:
- **Performance cluster**: performance-optimizer + async-architecture-specialist + database-maintenance-specialist
- **User experience cluster**: ui-modernizer + websocket-enhancer + error-analyzer
- **Data processing cluster**: template-wizard + file-processing-expert + security-auditor

#### 2. Dependency awareness
Учитывай зависимости между агентами:
- database-maintenance-specialist должен завершиться до performance-optimizer
- template-wizard создает основу для file-processing-expert
- error-analyzer результаты нужны для websocket-enhancer

#### 3. Resource coordination
Координируй использование ресурсов:
- Только один агент изменяет AsyncConfig.java одновременно
- Database schema changes только через database-maintenance-specialist
- WebSocket configuration только через websocket-enhancer

#### 4. Testing coordination
Тестируй результаты комплексно:
```bash
# После parallel execution нескольких агентов
mvn clean compile                    # проверка компиляции
mvn spring-boot:run -Dspring-boot.run.profiles=silent  # проверка запуска
# функциональное тестирование измененных компонентов
```

### Мониторинг параллельной работы

```markdown
## Parallel Execution Status

✅ performance-optimizer: Thread pools optimized (AsyncConfig.java)
🔄 async-architecture-specialist: Coordination improvements in progress
⏳ database-maintenance-specialist: Creating indexes for ClientService

## Integration Points:
- AsyncConfig.java: Modified by performance-optimizer, used by async-architecture-specialist
- Database queries: Optimized by database-maintenance-specialist, monitored by performance-optimizer
```

Параллельное выполнение агентов позволяет эффективно решать комплексные задачи, ускоряет разработку и обеспечивает comprehensive approach к улучшению системы.

---

## 🤖 agent-orchestrator - Мета-агент управления экосистемой

**Специализация:** Интеллектуальное управление и координация всех 10 специализированных агентов Zoomos v4

### 🎯 Ключевые возможности управления агентами

#### 1. **Анализ запросов и выбор агентов**
Агент-оркестратор анализирует пользовательские запросы и определяет оптимальный набор агентов для выполнения задач:

```yaml
# Матрица принятия решений
request_analysis:
  single_agent_triggers:
    - "оптимизировать производительность" → performance-optimizer
    - "создать шаблон импорта" → template-wizard
    - "исправить WebSocket" → websocket-enhancer
    - "добавить миграцию" → database-maintenance-specialist

  multi_agent_triggers:
    - "медленная обработка файлов" → performance-optimizer + file-processing-expert + async-architecture-specialist
    - "новый клиент с особыми требованиями" → template-wizard + security-auditor + file-processing-expert
    - "критический баг в async операциях" → error-analyzer + async-architecture-specialist + database-maintenance-specialist
    - "модернизация UI с real-time уведомлениями" → ui-modernizer + websocket-enhancer + monitoring-dashboard-builder
```

#### 2. **Интеллектуальные функции архитектурного анализа**

**Понимание архитектуры Zoomos v4:**
```java
// Оркестратор знает ключевые компоненты и их взаимосвязи
architecture_knowledge:
  core_configs:
    - AsyncConfig.java (5 thread pools: import, export, fileAnalysis, utils, redirect)
    - WebSocketConfig.java (STOMP endpoints, heartbeat configuration)
    - SecurityConfig.java (file upload validation, SSRF protection)

  service_hierarchy:
    file_processing:
      - ImportProcessorService → AsyncImportService → BaseProgressService
      - ExportProcessorService → AsyncExportService → FileGeneratorService
      - FileAnalyzerService → character encoding, structure analysis

    async_coordination:
      - 5 specialized executors with different configurations
      - WebSocket progress tracking (/topic/progress/{operationId})
      - Operation cancellation and cleanup mechanisms

    utilities:
      - RedirectFinderService → 3 strategies (Curl, Playwright, HttpClient)
      - AsyncRedirectService → WebSocket progress (/topic/redirect-progress/{operationId})
      - BarcodeMatchService, UrlCleanerService, LinkExtractorService
```

**Распознавание сложности задач:**
```yaml
complexity_assessment:
  simple_tasks:
    indicators: ["добавить поле", "исправить отображение", "изменить текст"]
    agent_count: 1
    execution_mode: "sequential"

  moderate_tasks:
    indicators: ["оптимизировать", "улучшить", "добавить функциональность"]
    agent_count: 2-3
    execution_mode: "parallel_compatible"

  complex_tasks:
    indicators: ["переделать", "модернизировать", "интегрировать", "критический баг"]
    agent_count: 4-6
    execution_mode: "orchestrated_workflow"

  emergency_tasks:
    indicators: ["не работает", "падает", "потеря данных", "security breach"]
    agent_count: 3-5
    execution_mode: "emergency_parallel"
    priority: "CRITICAL"
```

#### 3. **Оркестрация сложных workflow**

**Создание планов выполнения:**
```yaml
# Пример: Полная модернизация производительности системы
workflow_performance_modernization:
  phase_1_foundation: # Последовательное выполнение
    agents: [database-maintenance-specialist]
    tasks:
      - "Анализ slow queries в ClientService и StatisticsService"
      - "Создание индексов для FileOperation и ImportError таблиц"
      - "Оптимизация HikariCP connection pool"
    estimated_time: "30-45 минут"

  phase_2_parallel_optimization: # Параллельное выполнение
    agents: [performance-optimizer, async-architecture-specialist, file-processing-expert]
    tasks:
      performance-optimizer:
        - "Анализ memory usage в ImportProcessorService"
        - "Оптимизация AsyncConfig.java thread pools"
        - "Tuning WebSocket connection handling"
      async-architecture-specialist:
        - "Улучшение coordination между executors"
        - "Оптимизация BaseProgressService"
        - "Enhanced cancellation logic"
      file-processing-expert:
        - "Streaming processing для Excel файлов 100K+ строк"
        - "Memory-efficient Apache POI configuration"
        - "Character encoding optimization"
    estimated_time: "45-60 минут"
    dependencies: "phase_1_foundation"

  phase_3_validation: # Финальная проверка
    agents: [error-analyzer, monitoring-dashboard-builder]
    tasks:
      - "Тестирование производительности под нагрузкой"
      - "Создание performance dashboard"
      - "Validation error handling improvements"
    estimated_time: "20-30 минут"
    dependencies: "phase_2_parallel_optimization"
```

**Мониторинг прогресса workflow:**
```markdown
## 🔄 Workflow Status: Performance Modernization

### Phase 1: Foundation (✅ COMPLETED - 35 min)
- ✅ database-maintenance-specialist: Индексы созданы, connection pool оптимизирован
  - Файлы: V1.20__optimize_file_operations.sql, application.properties:78-82
  - Результат: Query performance улучшен на 40%

### Phase 2: Parallel Optimization (🔄 IN PROGRESS - 25/60 min)
- ✅ performance-optimizer: AsyncConfig.java optimized
  - Файлы: AsyncConfig.java:21-28, application.properties:45-52
  - Результат: Thread pools размеры увеличены на 50%
- 🔄 async-architecture-specialist: Coordination improvements (80% done)
  - Файлы: BaseProgressService.java, AsyncImportService.java:145-160
  - Текущий прогресс: Enhanced cancellation logic implemented
- ⏳ file-processing-expert: Waiting for memory analysis results

### Phase 3: Validation (⏳ PENDING)
- Ожидается завершение Phase 2

### Integration Points Detected:
- ⚠️ AsyncConfig.java: Modified by performance-optimizer, needs review by async-architecture-specialist
- ✅ Database indexes: Created by database-maintenance, used by performance-optimizer
```

#### 4. **Движок принятия решений**

**Матрица приоритетов агентов:**
```yaml
agent_priority_matrix:
  critical_system_issues:
    primary: [error-analyzer, database-maintenance-specialist, security-auditor]
    secondary: [performance-optimizer, async-architecture-specialist]
    rationale: "Стабильность и безопасность превыше всего"

  performance_optimization:
    primary: [performance-optimizer, async-architecture-specialist]
    secondary: [database-maintenance-specialist, file-processing-expert]
    rationale: "Системная производительность требует координации async и DB"

  new_feature_development:
    primary: [template-wizard, file-processing-expert]
    secondary: [websocket-enhancer, ui-modernizer]
    rationale: "Функциональность сначала, UX потом"

  client_onboarding:
    primary: [template-wizard, security-auditor, file-processing-expert]
    secondary: [ui-modernizer, monitoring-dashboard-builder]
    rationale: "Безопасность и функциональность для нового клиента"
```

**Стратегии разрешения конфликтов:**
```yaml
conflict_resolution:
  file_modification_conflicts:
    AsyncConfig.java:
      priority_agent: "performance-optimizer"
      coordination_rule: "async-architecture-specialist gets review rights"
      resolution_strategy: "sequential_with_review"

    WebSocketConfig.java:
      priority_agent: "websocket-enhancer"
      coordination_rule: "ui-modernizer coordinates client-side changes"
      resolution_strategy: "parallel_with_communication"

    ImportProcessorService.java:
      priority_agent: "file-processing-expert"
      coordination_rule: "performance-optimizer provides memory constraints"
      resolution_strategy: "collaborative_implementation"

  overlapping_functionality:
    error_handling:
      primary_agent: "error-analyzer"
      supporting_agents: ["websocket-enhancer", "ui-modernizer"]
      coordination: "error-analyzer defines strategy, others implement notifications"

    async_operations:
      primary_agent: "async-architecture-specialist"
      supporting_agents: ["performance-optimizer", "file-processing-expert"]
      coordination: "architecture defines patterns, others optimize within patterns"
```

**Оптимизация ресурсов:**
```yaml
resource_optimization:
  parallel_execution_rules:
    safe_combinations:
      - [template-wizard, security-auditor]  # Разные области кода
      - [ui-modernizer, database-maintenance-specialist]  # Frontend vs Backend
      - [websocket-enhancer, file-processing-expert]  # Communication vs Processing

    requires_coordination:
      - [performance-optimizer, async-architecture-specialist]  # Shared AsyncConfig.java
      - [error-analyzer, websocket-enhancer]  # Shared error notification logic
      - [file-processing-expert, template-wizard]  # Shared validation logic

    sequential_only:
      - database-maintenance-specialist → performance-optimizer  # DB first, then tuning
      - security-auditor → template-wizard  # Security rules before templates
      - error-analyzer → monitoring-dashboard-builder  # Error analysis before dashboards
```

#### 5. **Практические сценарии оркестрации**

**Сценарий 1: Новый крупный клиент (E-commerce)**
```markdown
## Запрос пользователя:
"Крупный e-commerce клиент хочет интегрироваться. У них файлы с 500K товарами,
special barcode валидация, высокие требования к безопасности и real-time уведомления о прогрессе."

## Анализ оркестратора:
- Сложность: HIGH (4-5 агентов)
- Категория: client_onboarding + performance_optimization
- Критические области: template creation, file processing, security, performance

## План выполнения:
### Phase 1: Security Foundation
- security-auditor: Audit file upload validation, добавить enhanced barcode validation
- Время: 20-30 min

### Phase 2: Core Implementation (Parallel)
- template-wizard: Создать e-commerce шаблоны с barcode validation
- file-processing-expert: Оптимизировать обработку 500K записей with memory streaming
- websocket-enhancer: Enhanced progress tracking для больших файлов
- Время: 45-60 min

### Phase 3: Performance Validation
- performance-optimizer: Load testing с 500K записями
- monitoring-dashboard-builder: Custom dashboard для клиента
- Время: 30 min

## Предполагаемые файлы изменений:
- TemplateValidationService.java (barcode validation)
- FileAnalyzerService.java (large file optimization)
- BarcodeMatchService.java (enhanced validation)
- WebSocketConfig.java (progress granularity)
- AsyncConfig.java (thread pool tuning)
```

**Сценарий 2: Критический баг в async операциях**
```markdown
## Запрос пользователя:
"Async import операции зависают на больших файлах, пользователи жалуются на потерю данных,
WebSocket уведомления не работают правильно."

## Анализ оркестратора:
- Критичность: EMERGENCY
- Категория: critical_system_issues
- Затронутые компоненты: AsyncImportService, WebSocket, database integrity

## Emergency Response Plan:
### Immediate Response (Parallel - критический режим)
- error-analyzer: Анализ error patterns в AsyncImportService и ImportProcessorService
- async-architecture-specialist: Проверка thread pool configuration и cancellation logic
- database-maintenance-specialist: Data integrity check и recovery procedures

### Secondary Response
- websocket-enhancer: Fixing WebSocket notifications
- monitoring-dashboard-builder: Emergency monitoring dashboard

## Estimated Resolution Time: 60-90 minutes
## Roll-back Plan: Prepared by database-maintenance-specialist
```

**Сценарий 3: Разрешение конфликта между агентами**
```markdown
## Ситуация:
performance-optimizer и async-architecture-specialist одновременно изменяют AsyncConfig.java

## Conflict Detection:
```
⚠️ КОНФЛИКТ ОБНАРУЖЕН: AsyncConfig.java
- performance-optimizer: Изменил importCorePoolSize с 2 на 4
- async-architecture-specialist: Изменил importQueueCapacity с 100 на 200

## Resolution Strategy:
1. Приоритет: performance-optimizer (по матрице приоритетов)
2. Координация: async-architecture-specialist проверяет изменения
3. Результат: Объединенная конфигурация с обоими улучшениями
```

## Final AsyncConfig.java:
```java
@Value("${import.async.core-pool-size:4}")  // от performance-optimizer
private int importCorePoolSize;

@Value("${import.async.queue-capacity:200}")  // от async-architecture-specialist
private int importQueueCapacity;
```

## Validation: Оба агента подтверждают корректность объединенной конфигурации
```

#### 6. **Интерфейс взаимодействия с пользователем**

**Команды оркестратора:**
```markdown
# Анализ и рекомендации
@orchestrator analyze "описание задачи"
→ Возвращает рекомендуемых агентов и план выполнения

# Запуск оркестрованного workflow
@orchestrator execute --agents performance-optimizer,async-architecture-specialist --mode parallel
→ Координирует выполнение с мониторингом конфликтов

# Мониторинг текущих операций
@orchestrator status
→ Показывает статус всех активных агентов и workflow

# Разрешение конфликтов
@orchestrator resolve-conflict --file AsyncConfig.java --agents performance-optimizer,async-architecture-specialist
→ Интерактивное разрешение конфликтов изменений

# Emergency mode для критических проблем
@orchestrator emergency --issue "async operations hanging"
→ Активирует emergency response с приоритетными агентами
```

**Примеры коммуникации:**
```markdown
Пользователь: "Система работает медленно при загрузке больших Excel файлов"

Оркестратор:
🎯 АНАЛИЗ ЗАПРОСА: Проблема производительности файловой обработки

📋 РЕКОМЕНДУЕМЫЙ ПЛАН:
1. performance-optimizer: Анализ memory usage и thread pools
2. file-processing-expert: Оптимизация Apache POI для больших файлов
3. async-architecture-specialist: Coordination между import executors

⚡ РЕЖИМ ВЫПОЛНЕНИЯ: Parallel (агенты 1-2), затем Sequential (агент 3)
⏱️ ОЖИДАЕМОЕ ВРЕМЯ: 45-60 минут
🎯 ФАЙЛЫ К ИЗМЕНЕНИЮ: AsyncConfig.java, ImportProcessorService.java, FileAnalyzerService.java

Запустить план? [Y/n]
```

#### 7. **Advanced функции**

**Predictive Analysis:**
```yaml
# Оркестратор анализирует паттерны и предсказывает потребности
predictive_recommendations:
  based_on_history:
    - "Последние 3 задачи касались производительности → предложить performance audit"
    - "Новые клиенты часто требуют template-wizard + security-auditor"
    - "После database changes всегда нужен performance re-tuning"

  proactive_suggestions:
    - "AsyncConfig.java изменен → рекомендую performance validation"
    - "Новые миграции → предложить database maintenance"
    - "WebSocket changes → проверить client-side compatibility"
```

**Quality Assurance Integration:**
```yaml
qa_integration:
  post_execution_checks:
    compilation: "mvn clean compile"
    startup: "mvn spring-boot:run -Dspring-boot.run.profiles=silent"
    functional_tests: "Custom test scenarios based on changed components"

  rollback_procedures:
    database_changes: "Automated Flyway rollback"
    configuration_changes: "Git reset to pre-execution state"
    code_changes: "Backup and restore procedures"

  success_criteria:
    - "All tests pass"
    - "Application starts successfully"
    - "No new error patterns in logs"
    - "Performance metrics improved or maintained"
```

#### 8. **Интеграция с экосистемой Zoomos v4**

**Понимание бизнес-логики:**
```java
// Оркестратор знает ключевые бизнес-процессы
business_process_knowledge:
  import_workflow:
    - FileAnalyzerService.analyzeFile() → структурный анализ
    - TemplateValidationService.validate() → проверка соответствия
    - AsyncImportService.processImport() → асинхронная обработка
    - WebSocket progress → real-time уведомления пользователю

  export_workflow:
    - ExportTemplateService.prepareTemplate() → подготовка шаблона
    - ExportProcessorService.generateFile() → создание файла
    - FileGeneratorService strategy pattern → CSV/Excel generation

  maintenance_workflow:
    - MaintenanceSchedulerService @Scheduled tasks
    - DatabaseMaintenanceService cleanup procedures
    - SystemHealthService monitoring
```

**Russian Development Practices Awareness:**
```yaml
russian_practices:
  communication:
    - Все сообщения и логи на русском языке
    - Подробные комментарии в коде на русском
    - Error messages понятные российским пользователям

  development_approach:
    - KISS principle - простота превыше всего
    - MVP подход - минимальная функциональность сначала
    - Итеративная разработка с быстрой обратной связью
    - Pet project mindset - избегать over-engineering

  tool_preferences:
    - Spring Boot ecosystem предпочтителен
    - PostgreSQL как основная БД
    - Maven для сборки
    - Thymeleaf для UI (не SPA фреймворки)
```

### 🛠️ Инструменты оркестратора

**Специализированные инструменты:**
- **Read, Edit, MultiEdit**: Анализ и координация изменений кода
- **Bash**: Тестирование и валидация результатов агентов
- **Grep, Glob**: Поиск конфликтов и dependencies между изменениями
- **TodoWrite**: Создание и мониторинг сложных workflow
- **WebSearch**: Поиск best practices для оркестрации агентов

### 🎯 Ключевые принципы работы

1. **Intelligent Delegation**: Не делать работу агентов, а координировать их эффективно
2. **Conflict Prevention**: Предотвращать конфликты через умное планирование
3. **Resource Optimization**: Максимизировать параллелизм при минимизации conflicts
4. **Quality Assurance**: Обеспечивать целостность после мульти-агентных операций
5. **User Experience**: Простой интерфейс для сложной оркестрации

### 🚀 Примеры использования в командной строке

```bash
# Быстрый анализ задачи
@orchestrator "Нужно добавить новый тип файлов для импорта с валидацией"
→ Рекомендует: template-wizard + file-processing-expert + security-auditor

# Полная модернизация производительности
@orchestrator performance-audit --comprehensive
→ Запускает 4-фазный workflow с 6 агентами

# Emergency response
@orchestrator emergency --issue "async imports hanging"
→ Немедленный параллельный запуск error-analyzer + async-specialist + database-maintenance

# Мониторинг workflow
@orchestrator monitor --workflow performance-modernization
→ Real-time статус всех агентов в workflow
```

Агент-оркестратор является "мозгом" экосистемы агентов Zoomos v4, обеспечивая интеллектуальное управление, эффективную координацию и максимальную отдачу от специализированных агентов.

---

## 📚 Практическое руководство по использованию agent-orchestrator

### 🎯 Для чего предназначен агент-оркестратор

**Основная цель:** Максимизировать эффективность работы с 10 специализированными агентами через интеллектуальную координацию и автоматизацию сложных сценариев.

**Ключевые задачи:**
1. **Smart Agent Selection** - автоматический выбор оптимальных агентов для любой задачи
2. **Workflow Orchestration** - создание и управление сложными мульти-агентными процессами
3. **Conflict Resolution** - предотвращение и разрешение конфликтов между агентами
4. **Resource Optimization** - оптимальное использование параллелизма и последовательности
5. **Quality Assurance** - обеспечение целостности результатов после работы множественных агентов

### 🛠️ Как использовать оркестратор

#### Базовые команды

**1. Анализ задачи с рекомендациями:**
```markdown
Пользователь: "Система медленно обрабатывает большие Excel файлы"

→ @orchestrator analyze "медленная обработка больших Excel файлов"

Ответ оркестратора:
🎯 АНАЛИЗ ЗАДАЧИ: Performance optimization для file processing
📋 РЕКОМЕНДУЕМЫЕ АГЕНТЫ:
  - performance-optimizer (приоритет: HIGH)
  - file-processing-expert (приоритет: HIGH)
  - async-architecture-specialist (приоритет: MEDIUM)
⚡ РЕЖИМ: Parallel execution для первых двух, sequential для третьего
⏱️ ВРЕМЯ: 45-60 минут
```

**2. Автоматический запуск рекомендованного плана:**
```markdown
→ @orchestrator execute-plan

Результат: Координированный запуск агентов с мониторингом прогресса
```

**3. Ручной запуск с кастомными параметрами:**
```markdown
→ @orchestrator execute --agents performance-optimizer,file-processing-expert --mode parallel --priority high

Результат: Параллельный запуск указанных агентов с высоким приоритетом
```

#### Продвинутые сценарии

**1. Комплексная модернизация системы:**
```markdown
Пользователь: "Нужна полная модернизация системы: производительность + UI + мониторинг"

→ @orchestrator comprehensive-modernization

План выполнения:
Phase 1: Foundation (30 min)
  - database-maintenance-specialist: DB optimization

Phase 2: Core Systems (60 min, parallel)
  - performance-optimizer: System performance
  - async-architecture-specialist: Async coordination
  - file-processing-expert: File handling optimization

Phase 3: User Experience (45 min, parallel)
  - ui-modernizer: Frontend improvements
  - websocket-enhancer: Real-time features
  - monitoring-dashboard-builder: Dashboards

Phase 4: Validation (30 min)
  - error-analyzer: Quality assurance
  - security-auditor: Security review
```

**2. Emergency response для критических проблем:**
```markdown
Пользователь: "Async операции зависают, пользователи теряют данные!"

→ @orchestrator emergency --issue "async operations data loss"

Emergency Response:
🚨 CRITICAL ISSUE DETECTED
⚡ IMMEDIATE PARALLEL RESPONSE:
  - error-analyzer: Error pattern analysis
  - async-architecture-specialist: Thread pool diagnosis
  - database-maintenance-specialist: Data integrity check
🔄 SECONDARY RESPONSE:
  - websocket-enhancer: Notification system fix
💾 ROLLBACK PREPARED: Automatic backup created
⏱️ ETA: 60-90 minutes
```

#### Мониторинг и управление workflow

**1. Проверка статуса текущих операций:**
```markdown
→ @orchestrator status

Current Active Workflows:
🔄 Performance Modernization (Phase 2/4 - 65% complete)
  ✅ performance-optimizer: AsyncConfig optimized
  🔄 file-processing-expert: Memory optimization (80%)
  ⏳ async-architecture-specialist: Queued

📊 Resource Usage:
  - Active agents: 2/5 max
  - Estimated completion: 25 minutes
  - No conflicts detected
```

**2. Разрешение конфликтов:**
```markdown
⚠️ CONFLICT DETECTED: AsyncConfig.java
  - performance-optimizer: Thread pool size changes
  - async-architecture-specialist: Queue capacity changes

→ @orchestrator resolve-conflict --auto

Resolution Applied:
✅ Combined changes: Both improvements integrated
📝 File: AsyncConfig.java updated with merged configuration
🔍 Validation: Both agents confirmed compatibility
```

### 🎯 Типичные сценарии использования

#### Сценарий 1: Новый разработчик в команде
```markdown
Новый разработчик: "Не знаю какого агента использовать для оптимизации импорта"

→ @orchestrator help --task "optimize import performance"

Рекомендация:
📚 ОБУЧАЮЩИЙ РЕЖИМ:
1. Начни с: performance-optimizer
   - Анализирует AsyncConfig.java и thread pools
   - Проверяет memory usage в ImportProcessorService

2. Затем: file-processing-expert
   - Оптимизирует Apache POI для больших файлов
   - Улучшает character encoding detection

3. При необходимости: async-architecture-specialist
   - Улучшает coordination между import executors

📖 Документация: См. agents.md разделы 1, 6, 7
⏱️ Время изучения: 15-20 минут
```

#### Сценарий 2: Регулярное обслуживание системы
```markdown
→ @orchestrator maintenance --weekly

Weekly Maintenance Plan:
🔧 STANDARD WORKFLOW (120 min):

Phase 1: Health Check (20 min)
  - monitoring-dashboard-builder: System metrics review
  - database-maintenance-specialist: Performance analysis

Phase 2: Optimization (60 min)
  - performance-optimizer: Resource usage optimization
  - database-maintenance-specialist: Query optimization

Phase 3: Security & Quality (40 min)
  - security-auditor: Security scan
  - error-analyzer: Error pattern analysis

📅 Рекомендуемое время: Воскресенье 02:00
🔄 Автоматизация: Можно настроить через MaintenanceSchedulerService
```

#### Сценарий 3: Подготовка к высокой нагрузке
```markdown
Пользователь: "Ожидается пик нагрузки, нужно подготовить систему"

→ @orchestrator load-preparation --scale high

Load Preparation Workflow:
🚀 HIGH LOAD OPTIMIZATION (90 min):

Phase 1: Infrastructure (30 min)
  - database-maintenance-specialist: Connection pool tuning
  - performance-optimizer: JVM optimization

Phase 2: Processing (45 min, parallel)
  - async-architecture-specialist: Thread pool scaling
  - file-processing-expert: Batch size optimization
  - websocket-enhancer: Connection limit tuning

Phase 3: Monitoring (15 min)
  - monitoring-dashboard-builder: Load monitoring dashboard
  - error-analyzer: Error handling validation

📈 Expected improvements:
  - 3x throughput increase
  - Better error resilience
  - Real-time load monitoring
```

### 🔧 Настройка и конфигурация оркестратора

#### Персонализация для проекта
```yaml
# orchestrator-config.yaml
project_context:
  name: "Zoomos v4"
  language: "russian"
  approach: "KISS + MVP"

preferences:
  default_mode: "conservative"  # conservative | aggressive | balanced
  auto_execute: false  # требовать подтверждение перед запуском
  conflict_resolution: "interactive"  # auto | interactive | manual

resource_limits:
  max_parallel_agents: 5
  max_workflow_duration: 180  # minutes
  auto_rollback_on_failure: true

notification_settings:
  progress_updates: true
  conflict_alerts: true
  completion_notifications: true
```

#### Интеграция с IDE и командной строкой
```bash
# Добавить в .bashrc или PowerShell profile
alias orch="@orchestrator"
alias orch-analyze="@orchestrator analyze"
alias orch-status="@orchestrator status"
alias orch-emergency="@orchestrator emergency"

# Примеры использования:
orch-analyze "нужен новый тип импорта для XML файлов"
orch-status
orch emergency --issue "database connection timeout"
```

### 📊 Мониторинг эффективности

#### Метрики успешности оркестратора
```yaml
efficiency_metrics:
  task_completion_rate: "95%"  # задачи завершены успешно
  time_savings: "40%"  # экономия времени vs ручной выбор агентов
  conflict_prevention: "85%"  # конфликты предотвращены до возникновения
  user_satisfaction: "90%"  # удовлетворенность результатами

performance_indicators:
  average_workflow_time: "60 minutes"
  parallel_efficiency: "75%"  # эффективность параллельного выполнения
  rollback_frequency: "5%"  # частота откатов изменений
  learning_accuracy: "88%"  # точность предсказаний на основе истории
```

### 🎓 Обучение и best practices

#### Как максимизировать пользу от оркестратора

**1. Детальные описания задач:**
```markdown
❌ Плохо: "оптимизировать систему"
✅ Хорошо: "медленная обработка Excel файлов размером 200MB+ с частыми timeout ошибками"

→ Подробное описание = более точный выбор агентов
```

**2. Использование контекста проекта:**
```markdown
❌ Плохо: "добавить новую функцию"
✅ Хорошо: "добавить поддержку JSON импорта для REST API интеграции клиента"

→ Контекст помогает оркестратору выбрать правильную стратегию
```

**3. Доверие рекомендациям:**
```markdown
📊 Статистика: Рекомендации оркестратора на 40% эффективнее ручного выбора
💡 Совет: Начни с рекомендованного плана, модифицируй при необходимости
```

#### Типичные ошибки и как их избежать

**1. Микроменеджмент агентов:**
```markdown
❌ Неправильно: Запускать по одному агенту для каждой мелкой задачи
✅ Правильно: Группировать связанные задачи в workflow

Пример:
Вместо: template-wizard → file-processing-expert → security-auditor (последовательно)
Лучше: @orchestrator new-client-onboarding (автоматическая координация)
```

**2. Игнорирование конфликтов:**
```markdown
❌ Неправильно: Запускать conflicting агентов одновременно без координации
✅ Правильно: Использовать conflict resolution оркестратора

Результат: Избежание 85% потенциальных конфликтов
```

### 🔮 Будущие улучшения

**Планируемые функции:**
- **Machine Learning**: Улучшение предсказаний на основе истории проекта
- **Auto-scaling**: Динамическое изменение parallel limits на основе нагрузки
- **Integration APIs**: REST API для интеграции с внешними системами мониторинга
- **Custom Workflows**: Сохранение пользовательских workflow шаблонов

**Обратная связь приветствуется:**
```markdown
→ @orchestrator feedback "что можно улучшить в координации агентов?"
```

Агент-оркестратор превращает работу с множественными агентами из хаотичного процесса в четко организованную, эффективную и предсказуемую систему разработки.