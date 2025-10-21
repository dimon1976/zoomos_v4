# План улучшения сообщений об ошибках импорта

**Дата создания:** 2025-10-20
**Статус:** Планирование
**Приоритет:** Высокий

## Проблема

При импорте файла ошибка `DataIntegrityViolationException: значение не умещается в тип character varying(255)` показывается пользователю как сложное техническое сообщение. Нужно сделать его понятным для конечного пользователя.

### Пример технической ошибки из логов

```
org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback;
SQL [INSERT INTO av_data (...) VALUES (...)];
Batch entry 132 INSERT INTO av_data (...) VALUES (
  ('s146095'),
  ('Рос. вино с ЗГУ "КРЫМ" выдержанное сухое красное "Меганом" торговой марки "СОЛНЕЧНАЯ ДОЛИНА" 0,75 л Рос. вино Меганом  торговой марки СОЛНЕЧНАЯ ДОЛИНА, белое сухое, 0.75 0,75 Рос. вино с ЗГУ "КРЫМ" выдержанное сухое красное "Меганом" торговой марки "СОЛНЕЧНАЯ ДОЛИНА" 0,75 л'),
  ...
) was aborted: ОШИБКА: значение не умещается в тип character varying(255)
```

**Проблема:**
- Значение `product_name` содержит ~338 символов
- Лимит поля в БД: VARCHAR(255)
- В консоли разработчика видно детали, но в UI показывается нечитаемое сообщение

## Анализ текущей реализации

### Цепочка отображения ошибок

1. **Возникновение ошибки** → `EntityPersistenceService.saveAvData():106`
   - `namedParameterJdbcTemplate.batchUpdate()` выбрасывает `DataIntegrityViolationException`

2. **Первичная обработка** → `ImportProcessorService.processCsvBatch():462-468`
   ```java
   catch (Exception e) {
       log.error("Ошибка сохранения батча из {} записей", transformedBatch.size(), e);
       session.setErrorRows(session.getErrorRows() + transformedBatch.size());

       if (template.getErrorStrategy() == ErrorStrategy.STOP_ON_ERROR) {
           throw e; // Пробрасывается дальше
       }
   }
   ```

3. **Асинхронная обработка** → `AsyncImportService.handleAsyncImportError():178`
   ```java
   fileOperation.markAsFailed(e.getMessage()); // RAW техническое сообщение!
   ```

4. **Отображение в UI** → `status.html:239-245`
   ```html
   <div th:if="${operation.status.name() == 'FAILED'}">
       <div class="alert alert-danger">
           <i class="fas fa-exclamation-circle me-2"></i>
           <strong>Операция завершена с ошибкой</strong>
           <br th:if="${operation.errorMessage}" />
           <span th:if="${operation.errorMessage}" th:text="${operation.errorMessage}">Ошибка</span>
       </div>
   </div>
   ```

### Ключевые места в коде

| Файл | Строки | Назначение |
|------|--------|-----------|
| `EntityPersistenceService.java` | 69-109 | Сохранение данных в БД (место возникновения ошибки) |
| `ImportProcessorService.java` | 462-469 | Обработка ошибок сохранения батча |
| `AsyncImportService.java` | 170-192 | handleAsyncImportError() - сохранение сообщения об ошибке |
| `FileOperation.java` | 111-115 | markAsFailed() - установка errorMessage |
| `status.html` | 239-260 | Отображение ошибки пользователю |

## Архитектура решения

### Компоненты системы

```
┌─────────────────────────────────────────────────────────────┐
│                    Import Error Flow                         │
└─────────────────────────────────────────────────────────────┘

1. EntityPersistenceService.saveAvData()
   ↓ (DataIntegrityViolationException)

2. ImportProcessorService.processCsvBatch()
   ↓ catch (Exception e)
   ↓ DatabaseErrorMessageParser.parse(e) ← НОВЫЙ
   ↓ ErrorMessageFormatter.format() ← НОВЫЙ

3. AsyncImportService.handleAsyncImportError()
   ↓ fileOperation.markAsFailed(userFriendlyMessage) ← УЛУЧШЕНО

4. status.html
   ↓ Отображение с деталями и рекомендациями ← УЛУЧШЕНО
```

## План реализации

### Этап 1: Создание инфраструктуры обработки ошибок

#### 1.1 DatabaseErrorMessageParser.java
**Расположение:** `src/main/java/com/java/service/error/DatabaseErrorMessageParser.java`

**Назначение:** Парсинг SQL исключений и извлечение деталей

**Основные методы:**
```java
public class DatabaseErrorMessageParser {

    public ParsedDatabaseError parse(Exception exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return parseDataIntegrityViolation((DataIntegrityViolationException) exception);
        }
        // Другие типы исключений...
        return new ParsedDatabaseError(DatabaseErrorType.UNKNOWN, exception.getMessage());
    }

    private ParsedDatabaseError parseDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage();

        // Парсим "значение не умещается в тип character varying(255)"
        if (message.contains("значение не умещается в тип character varying")) {
            int maxLength = extractMaxLength(message);
            String columnName = extractColumnName(message);
            int batchEntryNumber = extractBatchEntryNumber(message);

            return ParsedDatabaseError.builder()
                .type(DatabaseErrorType.VALUE_TOO_LONG)
                .columnName(columnName)
                .maxLength(maxLength)
                .rowNumber(batchEntryNumber)
                .originalMessage(message)
                .build();
        }

        // Другие constraint violations...
        return ParsedDatabaseError.builder()
            .type(DatabaseErrorType.CONSTRAINT_VIOLATION)
            .originalMessage(message)
            .build();
    }
}
```

**Модель ParsedDatabaseError:**
```java
@Data
@Builder
public class ParsedDatabaseError {
    private DatabaseErrorType type;
    private String columnName;
    private Integer maxLength;
    private Integer actualLength;
    private Long rowNumber;
    private String constraintName;
    private String originalMessage;
}

enum DatabaseErrorType {
    VALUE_TOO_LONG,
    CONSTRAINT_VIOLATION,
    FOREIGN_KEY_VIOLATION,
    UNIQUE_VIOLATION,
    NOT_NULL_VIOLATION,
    UNKNOWN
}
```

#### 1.2 ErrorMessageFormatter.java
**Расположение:** `src/main/java/com/java/service/error/ErrorMessageFormatter.java`

**Назначение:** Форматирование понятных сообщений с использованием i18n

**Основные методы:**
```java
@Service
@RequiredArgsConstructor
public class ErrorMessageFormatter {

    private final MessageService messageService;

    public String formatDatabaseError(ParsedDatabaseError error) {
        switch (error.getType()) {
            case VALUE_TOO_LONG:
                return messageService.get(
                    "import.error.db.value.too.long",
                    translateColumnName(error.getColumnName()),
                    error.getActualLength(),
                    error.getMaxLength()
                );

            case CONSTRAINT_VIOLATION:
                return messageService.get(
                    "import.error.db.constraint.violation",
                    error.getConstraintName()
                );

            default:
                return messageService.get("import.error.db.general");
        }
    }

    public String formatWithRowNumber(Long rowNumber, String errorMessage) {
        if (rowNumber != null) {
            return messageService.get("import.error.batch.row", rowNumber, errorMessage);
        }
        return errorMessage;
    }

    private String translateColumnName(String dbColumnName) {
        // Маппинг технических имен колонок на понятные названия
        Map<String, String> columnNameMap = Map.of(
            "product_name", "Название товара",
            "product_description", "Описание товара",
            "product_brand", "Бренд",
            "competitor_name", "Название конкурента"
            // ... другие поля
        );
        return columnNameMap.getOrDefault(dbColumnName, dbColumnName);
    }
}
```

### Этап 2: Обновление messages.properties

**Файл:** `src/main/resources/messages.properties`

```properties
# Database errors - общие ошибки БД
import.error.db.value.too.long=Значение слишком длинное для поля "{0}": {1} символов (максимум {2})
import.error.db.constraint.violation=Нарушение ограничения базы данных: {0}
import.error.db.foreign.key=Ссылка на несуществующую запись в поле "{0}"
import.error.db.unique.violation=Дубликат значения в поле "{0}": {1}
import.error.db.not.null=Обязательное поле "{0}" не заполнено
import.error.db.general=Ошибка сохранения данных в базу

# Batch errors
import.error.batch.row=Строка {0}: {1}
import.error.batch.failed=Не удалось сохранить батч из {0} записей

# Import process errors
import.error.metadata.notfound=Не удалось найти метаданные загруженного файла
import.error.template.notfound=Шаблон импорта не найден или был удалён
import.error.file.unsupported=Неподдерживаемый формат файла: {0}. Поддерживаются: CSV, XLSX
import.error.row.critical=Критическая ошибка в строке {0}. Обработка остановлена
import.error.analysis.failed=Не удалось проанализировать структуру файла

# Recommendations
import.recommendation.value.too.long=Сократите значение поля "{0}" до {1} символов или используйте поле для длинных описаний
import.recommendation.check.data=Проверьте корректность данных в исходном файле
import.recommendation.template.fields=Убедитесь, что все обязательные поля шаблона заполнены
```

**Файл:** `src/main/resources/messages_en.properties`

```properties
# Database errors
import.error.db.value.too.long=Value too long for field "{0}": {1} characters (maximum {2})
import.error.db.constraint.violation=Database constraint violation: {0}
import.error.db.foreign.key=Reference to non-existent record in field "{0}"
import.error.db.unique.violation=Duplicate value in field "{0}": {1}
import.error.db.not.null=Required field "{0}" is not filled
import.error.db.general=Error saving data to database

# Batch errors
import.error.batch.row=Row {0}: {1}
import.error.batch.failed=Failed to save batch of {0} records

# Import process errors
import.error.metadata.notfound=Failed to find uploaded file metadata
import.error.template.notfound=Import template not found or was deleted
import.error.file.unsupported=Unsupported file format: {0}. Supported: CSV, XLSX
import.error.row.critical=Critical error in row {0}. Processing stopped
import.error.analysis.failed=Failed to analyze file structure

# Recommendations
import.recommendation.value.too.long=Shorten the "{0}" field value to {1} characters or use a field for long descriptions
import.recommendation.check.data=Check the correctness of data in the source file
import.recommendation.template.fields=Make sure all required template fields are filled
```

### Этап 3: Интеграция в AsyncImportService

**Файл:** `src/main/java/com/java/service/imports/AsyncImportService.java`

**Изменения в методе handleAsyncImportError() (строки 170-192):**

```java
@Autowired
private DatabaseErrorMessageParser errorParser;

@Autowired
private ErrorMessageFormatter errorFormatter;

private void handleAsyncImportError(ImportSession session, Exception e) {
    try {
        // Парсим исключение для получения понятного сообщения
        ParsedDatabaseError parsedError = errorParser.parse(e);
        String userFriendlyMessage = errorFormatter.formatDatabaseError(parsedError);

        // Добавляем номер строки если доступен
        if (parsedError.getRowNumber() != null) {
            userFriendlyMessage = errorFormatter.formatWithRowNumber(
                parsedError.getRowNumber(),
                userFriendlyMessage
            );
        }

        session.setStatus(ImportStatus.FAILED);
        session.setCompletedAt(ZonedDateTime.now());
        session.setErrorMessage(userFriendlyMessage); // Понятное сообщение
        sessionRepository.save(session);

        FileOperation fileOperation = session.getFileOperation();
        fileOperation.markAsFailed(userFriendlyMessage); // Понятное сообщение
        fileOperationRepository.save(fileOperation);

        progressService.sendErrorNotification(session, userFriendlyMessage);

        // Отправляем нотификацию об ошибке
        FileOperation operationWithClient = fileOperationRepository.findByIdWithClient(fileOperation.getId())
                .orElse(fileOperation);
        notificationService.sendImportFailedNotification(session, operationWithClient, userFriendlyMessage);

        // Логируем техническую информацию для разработчиков
        log.error("Import failed for session {}: {}", session.getId(), parsedError.getOriginalMessage(), e);

    } catch (Exception ex) {
        log.error("Ошибка обработки ошибки импорта", ex);
    }
}
```

### Этап 4: Улучшение обработки в ImportProcessorService

**Файл:** `src/main/java/com/java/service/imports/ImportProcessorService.java`

**Изменения в processCsvBatch() (строки 462-469):**

```java
@Autowired
private DatabaseErrorMessageParser errorParser;

@Autowired
private ErrorMessageFormatter errorFormatter;

// В методе processCsvBatch(), в блоке catch:
catch (Exception e) {
    log.error("Ошибка сохранения батча из {} записей", transformedBatch.size(), e);

    // Парсим ошибку для понятного сообщения
    ParsedDatabaseError parsedError = errorParser.parse(e);
    String userMessage = errorFormatter.formatDatabaseError(parsedError);

    // Записываем детальную ошибку
    if (parsedError.getRowNumber() != null) {
        Long actualRowNumber = startRowNumber + parsedError.getRowNumber();
        recordError(
            session,
            actualRowNumber,
            parsedError.getColumnName(),
            null,
            ErrorType.DATABASE_ERROR,
            userMessage
        );
    }

    session.setErrorRows(session.getErrorRows() + transformedBatch.size());

    if (template.getErrorStrategy() == ErrorStrategy.STOP_ON_ERROR) {
        // Бросаем исключение с понятным сообщением
        throw new ImportException(userMessage, e);
    }
}
```

### Этап 5: Улучшение UI в status.html

**Файл:** `src/main/resources/templates/operations/status.html`

**Изменения в блоке FAILED (строки 239-260):**

```html
<div th:if="${operation.status.name() == 'FAILED'}">
    <div class="alert alert-danger">
        <div class="d-flex align-items-start">
            <i class="fas fa-exclamation-circle me-3 fs-4"></i>
            <div class="flex-grow-1">
                <h6 class="alert-heading mb-2">
                    <strong>Операция завершена с ошибкой</strong>
                </h6>

                <!-- Основное сообщение об ошибке -->
                <div th:if="${operation.errorMessage}" class="error-message mb-3">
                    <span th:text="${operation.errorMessage}">Ошибка импорта</span>
                </div>

                <!-- Детали для импорта -->
                <div th:if="${importSession}" class="error-details">
                    <small class="text-muted">
                        <strong>Статистика:</strong>
                        Обработано: <span th:text="${importSession.processedRows}">0</span>,
                        Успешно: <span class="text-success" th:text="${importSession.successRows}">0</span>,
                        Ошибок: <span class="text-danger" th:text="${importSession.errorRows}">0</span>
                    </small>
                </div>
            </div>
        </div>
    </div>

    <!-- Рекомендации по исправлению -->
    <div class="card mt-3">
        <div class="card-header bg-light">
            <h6 class="mb-0">
                <i class="fas fa-lightbulb me-2 text-warning"></i>
                Рекомендации по исправлению
            </h6>
        </div>
        <div class="card-body">
            <ul class="mb-0">
                <li>Проверьте формат файла и соответствие заголовков шаблону импорта</li>
                <li>Убедитесь, что все значения соответствуют ограничениям полей</li>
                <li th:if="${operation.errorMessage != null and operation.errorMessage.contains('255')}">
                    Сократите длинные текстовые поля до 255 символов
                </li>
                <li>Проверьте корректность данных в проблемных строках</li>
                <li>При необходимости обратитесь к администратору системы</li>
            </ul>

            <!-- Ссылка на детальный лог ошибок если есть -->
            <div th:if="${importSession and importSession.errorRows > 0}" class="mt-3">
                <a th:href="@{/imports/{id}/errors(id=${importSession.id})}"
                   class="btn btn-outline-danger btn-sm">
                    <i class="fas fa-list me-1"></i>
                    Посмотреть детальный лог ошибок (<span th:text="${importSession.errorRows}">0</span>)
                </a>
            </div>
        </div>
    </div>

    <!-- Возможные действия при ошибке -->
    <div class="mt-3">
        <div class="btn-group">
            <a th:href="@{/clients/{id}/import(id=${clientId})}"
               class="btn btn-primary">
                <i class="fas fa-redo me-1"></i>Попробовать снова
            </a>
            <a th:href="@{/clients/{id}(id=${clientId})}"
               class="btn btn-outline-secondary">
                <i class="fas fa-arrow-left me-1"></i>Вернуться к клиенту
            </a>
        </div>
    </div>
</div>
```

## Примеры результатов

### До улучшений

**В UI пользователь видит:**
```
❌ Операция завершена с ошибкой

org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL [INSERT INTO av_data (data_source, operation_id, client_id, product_id, product_name, ...) VALUES (?, ?, ?, ?, ?, ...)]; Batch entry 132 INSERT INTO av_data (...) VALUES (('FILE'), ('68'::int8), ('1'::int8), ('s146095'), ('Рос. вино с ЗГУ "КРЫМ" выдержанное сухое красное "Меганом" торговой марки "СОЛНЕЧНАЯ ДОЛИНА" 0,75 л Рос. вино Меганом  торговой марки СОЛНЕЧНАЯ ДОЛИНА, белое сухое, 0.75 0,75 Рос. вино с ЗГУ "КРЫМ" выдержанное сухое красное "Меганом" торговой марки "СОЛНЕЧНАЯ ДОЛИНА" 0,75 л'), ...) was aborted: ОШИБКА: значение не умещается в тип character varying(255)
```

### После улучшений

**В UI пользователь видит:**
```
❌ Операция завершена с ошибкой

Строка 132: Значение слишком длинное для поля "Название товара": 338 символов (максимум 255)

Статистика: Обработано: 132, Успешно: 131, Ошибок: 1

💡 Рекомендации по исправлению
• Проверьте формат файла и соответствие заголовков шаблону импорта
• Убедитесь, что все значения соответствуют ограничениям полей
• Сократите длинные текстовые поля до 255 символов
• Проверьте корректность данных в проблемных строках

[Посмотреть детальный лог ошибок (1)]  [Попробовать снова]  [Вернуться к клиенту]
```

**В логах для разработчика остаётся:**
```
ERROR ImportProcessorService - Ошибка сохранения батча из 500 записей
org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL [INSERT...]
ОШИБКА: значение не умещается в тип character varying(255)
  at org.springframework.jdbc.core.JdbcTemplate.execute...
  [полный стектрейс]
```

## Преимущества решения

### Для пользователей
✅ Понятное описание проблемы без технических деталей
✅ Указание конкретной строки и поля с ошибкой
✅ Рекомендации по исправлению
✅ Возможность просмотреть детальный лог ошибок
✅ Поддержка русского и английского языков

### Для разработчиков
✅ Полная техническая информация остаётся в логах
✅ Централизованная обработка ошибок БД
✅ Легко добавлять новые типы ошибок
✅ Переиспользуемый код для других модулей
✅ i18n готов для расширения

### Для системы
✅ Улучшение UX при работе с ошибками
✅ Снижение нагрузки на поддержку
✅ Единообразная обработка ошибок
✅ Расширяемая архитектура

## Потенциальные расширения

### Фаза 2 (опционально)
1. **Детальный лог ошибок** - страница `/imports/{id}/errors` со всеми ошибками импорта
2. **Превью проблемных строк** - показывать содержимое строк с ошибками
3. **Автоматические рекомендации** - умные советы на основе типа ошибки
4. **Экспорт ошибок** - выгрузка списка ошибок в CSV/XLSX
5. **Исправление и переимпорт** - возможность исправить только проблемные строки

### Фаза 3 (опционально)
1. **Предварительная валидация** - проверка файла до импорта
2. **Умное усечение** - автоматическое сокращение длинных значений
3. **Предупреждения** - показывать warning'и до возникновения ошибок
4. **Статистика ошибок** - аналитика по типам ошибок

## Риски и ограничения

### Технические риски
⚠️ Сложность парсинга всех возможных вариантов SQL ошибок
⚠️ Зависимость от формата сообщений PostgreSQL
⚠️ Потенциальные проблемы с локализацией PostgreSQL

### Митигация рисков
- Fallback на техническое сообщение если парсинг не удался
- Юнит-тесты для всех типов известных ошибок
- Логирование нераспознанных ошибок для дальнейшего улучшения

## Чеклист реализации

- [ ] Создать `DatabaseErrorMessageParser.java`
- [ ] Создать `ErrorMessageFormatter.java`
- [ ] Создать модель `ParsedDatabaseError.java`
- [ ] Создать enum `DatabaseErrorType.java`
- [ ] Обновить `messages.properties` (RU)
- [ ] Обновить `messages_en.properties` (EN)
- [ ] Интегрировать в `AsyncImportService.java`
- [ ] Интегрировать в `ImportProcessorService.java`
- [ ] Улучшить UI в `status.html`
- [ ] Добавить стили для улучшенного отображения ошибок
- [ ] Написать юнит-тесты для парсера
- [ ] Написать интеграционные тесты
- [ ] Тестирование на реальных данных
- [ ] Обновить документацию

## Связанные документы

- [CLAUDE.md](../CLAUDE.md) - общая документация проекта
- [PR #46 Review](https://github.com/dimon1976/zoomos_v4/pull/46#issuecomment-3419838714) - Code Review рекомендации
- [Import Error Entity](../src/main/java/com/java/model/ImportError.java) - модель ошибок импорта

## История изменений

| Дата | Версия | Автор | Описание |
|------|--------|-------|----------|
| 2025-10-20 | 1.0 | Claude Code | Первая версия плана |
