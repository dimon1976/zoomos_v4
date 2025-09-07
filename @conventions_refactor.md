# Соглашения по разработке Zoomos v4

## Цель

Правила для code-ассистентов при генерации кода в рамках рефакторинга проекта. 

**Базовый документ**: `@vision_refactor.md` - полное техническое видение проекта.

---

## Основные принципы

### KISS (Keep It Simple, Stupid)
- Простые решения вместо сложных архитектур
- Читаемый код важнее "умного" кода
- Минимум абстракций и паттернов

### Обратная совместимость
- ❌ НЕ удалять/переименовывать существующие классы, методы, поля
- ❌ НЕ изменять сигнатуры публичных методов
- ✅ Только добавление нового функционала
- ✅ Рефакторинг внутренней логики без изменения интерфейсов

---

## Структура проекта

### Пакеты (новая организация)
```
com.java.
├── controller.importing/     # Контроллеры импорта
├── controller.exporting/     # Контроллеры экспорта
├── service.importing/        # Все сервисы импорта
├── service.exporting/        # Все сервисы экспорта
├── service.validation/       # Валидация (новый слой)
├── service.core/            # ClientService, FileAnalyzer, etc
├── service.utils/           # Утилиты по категориям
├── dto.importing/           # DTO импорта
├── dto.exporting/           # DTO экспорта
└── exception/               # Централизованные исключения
```

### Правила именования
- Классы сервисов: `*Service` (ImportService, ExportService)
- Контроллеры: `*Controller` 
- DTO: `*Dto` или `*Request`/`*Response`
- Исключения: `*Exception`

---

## Архитектура (4 слоя)

### 1. Controller Layer
- Только обработка HTTP запросов
- Валидация через Validation Layer
- Передача в Service Layer
- Обработка исключений через @ExceptionHandler

### 2. Validation Layer (новый)
- **Обязательная** валидация перед бизнес-логикой
- Отдельные сервисы: `FileValidationService`, `BusinessValidationService`
- Fail Fast принцип

### 3. Service Layer
- Бизнес-логика
- Асинхронные операции (@Async)
- Использование валидации перед обработкой

### 4. Data Layer
- JPA Repositories
- Без изменений существующих Entity

---

## Обязательные аннотации

### Классы
```java
@Slf4j                    // Логгирование (обязательно)
@Service/@Controller      // Spring аннотации
@RequiredArgsConstructor  // Lombok для DI
```

### Методы
```java
@Async("importTaskExecutor")  // Для долгих операций
@Transactional               // Для операций с БД
@ExceptionHandler            // В контроллерах
```

---

## Логгирование (обязательные правила)

### Что логировать
```java
log.info("Starting file import: {}", filename);        // Начало операций
log.error("Import failed: {} - {}", filename, e.getMessage(), e);  // Ошибки с контекстом
log.warn("Large file detected: {} MB", fileSize);      // Предупреждения
```

### Что НЕ логировать
- ❌ Персональные данные
- ❌ Содержимое файлов  
- ❌ Дублирующиеся сообщения в циклах

---

## Обработка ошибок

### Централизованная обработка
- `@ControllerAdvice` для глобальных исключений
- Сохранение ошибок в БД для анализа
- Единые форматы ответов с ошибками

### Типы исключений
- `FileOperationException` - файловые операции
- `ValidationException` - ошибки валидации
- `BusinessLogicException` - бизнес-правила

---

## Производительность (файлы 400-500МБ)

### Обязательные подходы
- **Stream processing** вместо загрузки в память
- **Chunked reading** по частям (1000 строк)
- **@Async** для долгих операций
- **Progress tracking** через WebSocket

### Thread Pools
- `importTaskExecutor` - импорт операции
- `exportTaskExecutor` - экспорт операции  
- `redirectTaskExecutor` - утилиты редиректов

---

## Запрещенные практики

### Архитектурные
- ❌ Статические методы для бизнес-логики
- ❌ Утилитные классы вместо @Service
- ❌ Прямые вызовы репозиториев из контроллеров

### Производительность  
- ❌ Загрузка больших файлов целиком
- ❌ Синхронные операции >30 сек
- ❌ Отсутствие прогресс-индикаторов

### Логгирование
- ❌ System.out.println
- ❌ Логи без контекста (что, когда, почему)
- ❌ Избыточное логгирование в циклах

---

## Шаблон кода

### Service класс
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {
    
    private final FileValidationService validationService;
    private final ImportRepository repository;
    
    @Async("importTaskExecutor")
    @Transactional
    public CompletableFuture<ImportResult> processFile(ImportRequest request) {
        log.info("Starting import: {}", request.getFilename());
        
        try {
            // 1. Валидация через Validation Layer
            validationService.validateFile(request.getFile());
            
            // 2. Бизнес-логика
            ImportResult result = processInternal(request);
            
            log.info("Import completed: {}", request.getFilename());
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Import failed: {} - {}", request.getFilename(), e.getMessage(), e);
            throw new ImportException("Import failed", e);
        }
    }
}
```

### Controller класс
```java
@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {
    
    private final ImportService importService;
    
    @PostMapping("/process")
    public ResponseEntity<ImportResponse> processFile(@RequestBody ImportRequest request) {
        CompletableFuture<ImportResult> future = importService.processFile(request);
        return ResponseEntity.ok(new ImportResponse(future.get()));
    }
    
    @ExceptionHandler(ImportException.class)
    public ResponseEntity<ErrorResponse> handleImportException(ImportException e) {
        log.error("Import controller error: {}", e.getMessage(), e);
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(e.getMessage()));
    }
}
```

---

## Проверка качества

### Обязательная проверка перед коммитом
- Компиляция без ошибок
- Логгирование добавлено в ключевых местах
- Валидация входных данных
- Обработка исключений
- @Async для операций >30 сек

### Критерии готовности кода
- ✅ Следует структуре пакетов
- ✅ Использует Validation Layer
- ✅ Логирует операции и ошибки
- ✅ Обрабатывает большие файлы через стримы
- ✅ Сохраняет обратную совместимость