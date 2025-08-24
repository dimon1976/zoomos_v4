# План рефакторинга сервисного слоя Zoomos v4

*Создано: 2025-08-24*  
*Статус: Готов к реализации*

## Цели рефакторинга

- Устранить дублирование кода между похожими сервисами
- Упростить архитектуру для пет-проекта  
- Сохранить всю текущую функциональность
- Улучшить читаемость и поддерживаемость кода
- **Ожидаемая экономия**: ~800 строк кода (28% сокращение)

## Детальный анализ текущего состояния

### Обнаруженное дублирование кода

#### 1. Template Services (ВЫСОКИЙ ПРИОРИТЕТ)
**Файлы**: 
- `ExportTemplateService.java` (181 строка)
- `ImportTemplateService.java` (223 строки)

**Дублированный функционал**:
- CRUD операции (создание, обновление, получение, удаление)
- Валидация уникальности имен шаблонов
- Проверка принадлежности клиенту  
- Клонирование шаблонов
- Использование TemplateUtils для общих операций

**Методы для объединения**:
```java
// Общие методы в обоих сервисах:
- create(clientId, dto)
- update(templateId, dto) 
- findById(templateId)
- findByClientId(clientId)
- delete(templateId)
- clone(templateId, newName)
- validateUniqueName(clientId, name, excludeId)
- checkClientOwnership(templateId, clientId)
```

#### 2. Progress Services (ВЫСОКИЙ ПРИОРИТЕТ)
**Файлы**:
- `ExportProgressService.java` (184 строки)
- `ImportProgressService.java` (285 строк)

**Дублированный функционал**:
- Отправка WebSocket обновлений прогресса
- Throttling обновлений (не чаще раза в секунду)
- Расчет процентов выполнения
- Форматирование сообщений о статусе
- Обработка завершения/ошибок операций

**Методы для объединения**:
```java
// Общие методы:
- sendProgressUpdate(operationId, progress, message)
- calculatePercentage(current, total)
- formatProgressMessage(...)
- handleCompletion(operationId, result)
- handleError(operationId, error)
- shouldSendUpdate(operationId) // throttling логика
```

#### 3. Async Services (СРЕДНИЙ ПРИОРИТЕТ)
**Файлы**:
- `AsyncExportService.java` (45 строк) - простой
- `AsyncImportService.java` (277 строк) - сложный

**Общие паттерны**:
- Асинхронное выполнение через @Async
- Обработка ошибок с CompletableFuture
- Транзакционность операций
- Делегирование к Processor сервисам

#### 4. Processor Services (СРЕДНИЙ ПРИОРИТЕТ)
**Файлы**:
- `ExportProcessorService.java` (389 строк)
- `ImportProcessorService.java` (635 строк)

**Общие операции**:
- Генерация имен файлов с timestamp
- Валидация входных данных
- Логирование операций
- Обработка ошибок

### Архитектурные избыточности

#### 1. Ненужные интерфейсы (НИЗКИЙ ПРИОРИТЕТ)
- `ClientService` → только `ClientServiceImpl`
- `DashboardService` → только `DashboardServiceImpl`
- Для пет-проекта интерфейсы избыточны без множественных реализаций

#### 2. Избыточно простые сервисы
- `EntityFieldService` - просто wrapper над enum, можно сделать static утилитой
- `CleanupService` - простой scheduled сервис, оставить как есть

#### 3. Import Handler'ы (СРЕДНИЙ ПРИОРИТЕТ)
**Файлы**:
- `DataTransformationService.java`
- `DuplicateCheckService.java` 
- `EntityPersistenceService.java`

**Возможность**: Объединить в 1-2 класса, так как работают вместе в цепочке

## Конкретный план реализации

### Этап 1: BaseTemplateService (ВЫСОКИЙ ПРИОРИТЕТ)

**1.1 Создать абстрактный базовый класс**
```java
// Файл: src/main/java/com/java/service/template/BaseTemplateService.java
public abstract class BaseTemplateService<T, D> {
    // Общие методы CRUD
    // Валидация
    // Клонирование
}
```

**1.2 Рефакторить существующие сервисы**
- `ExportTemplateService` → наследует от `BaseTemplateService<ExportTemplate, ExportTemplateDto>`
- `ImportTemplateService` → наследует от `BaseTemplateService<ImportTemplate, ImportTemplateDto>`

**Ожидаемая экономия**: ~150 строк кода

### Этап 2: BaseProgressService (ВЫСОКИЙ ПРИОРИТЕТ)

**2.1 Создать абстрактный класс**
```java
// Файл: src/main/java/com/java/service/progress/BaseProgressService.java
public abstract class BaseProgressService<T, D> {
    // WebSocket операции
    // Throttling логика
    // Расчеты процентов
}
```

**2.2 Рефакторить Progress сервисы**
- `ExportProgressService` → наследует от BaseProgressService
- `ImportProgressService` → наследует от BaseProgressService

**Ожидаемая экономия**: ~200 строк кода

### Этап 3: Упрощение Processor Services (СРЕДНИЙ ПРИОРИТЕТ)

**3.1 Создать ProcessorUtils**
```java
// Общие методы:
- generateFileName(operation, template, timestamp)
- validateInputData(data, template)  
- logOperationStart/End(operation)
```

**3.2 Рефакторить процессоры**
- Вынести общие методы в утилиты
- Разбить большие методы на более мелкие

**Ожидаемая экономия**: ~100 строк кода

### Этап 4: Объединение Async и Progress (СРЕДНИЙ ПРИОРИТЕТ)

**4.1 Интегрировать Progress в Async сервисы**
- Async сервисы напрямую работают с WebSocket
- Убрать промежуточный слой Progress сервисов
- Оставить Progress сервисы только для REST API

**Ожидаемая экономия**: ~150 строк кода

### Этап 5: Упрощение интерфейсов (НИЗКИЙ ПРИОРИТЕТ)

**5.1 Удалить ненужные интерфейсы**
- `ClientService` → `ClientServiceImpl` переименовать в `ClientService`
- `DashboardService` → `DashboardServiceImpl` переименовать в `DashboardService`

**5.2 Упростить EntityFieldService**
```java
// Из сервиса в static утилиту:
public class EntityFieldUtils {
    public static List<String> getFieldsForEntityType(EntityType type) { ... }
}
```

**Ожидаемая экономия**: ~50 строк кода

### Этап 6: Консолидация Import Handler'ов (НИЗКИЙ ПРИОРИТЕТ)

**6.1 Объединить handler'ы**
- `DataTransformationService` + `DuplicateCheckService` → `DataProcessingService`
- `EntityPersistenceService` оставить отдельно (специфичная логика)

**Ожидаемая экономия**: ~100 строк кода

## Порядок выполнения

1. **Этап 1-2** (BaseTemplateService + BaseProgressService) - максимальная отдача
2. **Этап 3** (ProcessorUtils) - средняя сложность
3. **Этап 4** (Async+Progress) - требует осторожности  
4. **Этап 5-6** (интерфейсы + handler'ы) - простые изменения

## Риски и предосторожности

- **Тестирование**: После каждого этапа запускать приложение и проверять основные сценарии
- **Транзакционность**: Внимательно с @Transactional при рефакторинге Async сервисов
- **WebSocket**: Не сломать real-time обновления прогресса
- **Постепенность**: Делать небольшие коммиты для отслеживания изменений

## Файлы для изменения

### Создать новые:
- `src/main/java/com/java/service/template/BaseTemplateService.java`
- `src/main/java/com/java/service/progress/BaseProgressService.java`  
- `src/main/java/com/java/util/ProcessorUtils.java`

### Изменить существующие:
- `src/main/java/com/java/service/exports/ExportTemplateService.java`
- `src/main/java/com/java/service/imports/ImportTemplateService.java`
- `src/main/java/com/java/service/exports/ExportProgressService.java`
- `src/main/java/com/java/service/imports/ImportProgressService.java`
- `src/main/java/com/java/service/exports/ExportProcessorService.java`
- `src/main/java/com/java/service/imports/ImportProcessorService.java`
- `src/main/java/com/java/service/client/ClientServiceImpl.java` → `ClientService.java`
- `src/main/java/com/java/service/dashboard/DashboardServiceImpl.java` → `DashboardService.java`

### Удалить:
- `src/main/java/com/java/service/client/ClientService.java` (интерфейс)
- `src/main/java/com/java/service/dashboard/DashboardService.java` (интерфейс)
- `src/main/java/com/java/service/client/impl/` (директория)
- `src/main/java/com/java/service/dashboard/impl/` (директория)

---

*Готов к реализации. Все изменения сохраняют текущую функциональность, упрощают код и устраняют дублирование.*