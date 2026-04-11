# План рефакторинга: устранение дублирующегося кода

Дата анализа: 2026-04-08  
Ветка: audit/project-review

---

## Критический приоритет

### 1. Utils-контроллеры — абстрактный базовый класс (~120 строк дублирования)

**Файлы:**
- `controller/utils/BarcodeMatchController.java`
- `controller/utils/UrlCleanerController.java`
- `controller/utils/LinkExtractorController.java`
- `controller/utils/StatsProcessorController.java`
- `controller/utils/RedirectFinderController.java`

**Суть:** Методы `uploadFile`, `configure`, `cancel` идентичны во всех 5 контроллерах. Различия только в имени сессионного атрибута и путях редиректа.

**Решение:** Создать `AbstractFileUtilController` с шаблонными методами. Конкретные контроллеры переопределяют `getSessionKey()`, `getUtilityPath()`, `getConfigureView()`, `createDto()`.

---

### 2. Operation status атрибуты в Model (10 строк × 2)

**Файлы:**
- `controller/ImportController.java:282-287`
- `controller/ExportController.java:156-162`

**Суть:** Оба метода добавляют в модель одинаковые 5 атрибутов через `controllerUtils`.

**Решение:** Добавить `ControllerUtils.populateOperationStatus(Model model, FileOperation operation)`.

---

### 3. JSON error ответы — `Map.of("success", false, "error", ...)` (30+ вхождений)

**Файлы:**
- `controller/ZoomosAnalysisController.java` — 25+ мест
- `controller/ClientController.java:412, 427`
- `controller/MaintenanceController.java` — приватный `createErrorResponse()` только для себя

**Суть:** Паттерн вручную копируется в каждом catch-блоке вместо централизованного хелпера.

**Решение:** Добавить в `ControllerUtils` методы `errorResponse(String message)` и `successResponse(Object data)` возвращающие `ResponseEntity<Map<String, Object>>`.

---

## Высокий приоритет

### 4. Нормализаторы — абстрактный базовый класс (~60 строк дублирования)

**Файлы:**
- `service/normalization/BrandNormalizer.java:25-43`
- `service/normalization/VolumeNormalizer.java:31-49`
- `service/normalization/CurrencyNormalizer.java:30-48`
- `service/normalization/CustomNormalizer.java:25-43`

**Суть:** Первые ~15 строк метода `normalize()` и метод `parseRules()` идентичны во всех 4 классах.

**Решение:** `AbstractNormalizer<R>` с шаблонным методом `normalize()`, делегирующим `doNormalize(String value, R rules)`.

---

### 5. Два ClientService — дублирующийся сервис

**Файлы:**
- `service/ClientService.java` — упрощённый дубль с `findAll()` и `findById()`
- `service/client/ClientService.java` — полный интерфейс (правильный)
- `service/client/impl/ClientServiceImpl.java` — реализация

**Суть:** Три контроллера (`MaintenanceController`, `ExportStatisticsController`, `DataCleanupController`) используют упрощённый `service/ClientService.java` вместо полного интерфейса.

**Решение:** Удалить `service/ClientService.java`, переключить 3 контроллера на `service/client/ClientService`, добавить `findAll()` в интерфейс если отсутствует.

---

### 6. Timestamp форматтер для имён файлов (11 вхождений в 10 файлах)

**Файлы:** `BarcodeMatchController`, `UrlCleanerController`, `LinkExtractorController`, `StatsProcessorController`, `RedirectFinderController`, `BarcodeHandbookController`, `RedirectFinderService`, `AsyncRedirectService`, `AsyncStatsProcessorService`, `FileManagementService`

**Суть:** `LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))` копируется повсюду при наличии `DateTimeHelper.java`.

**Решение:** Добавить в `DateTimeHelper` константу `FILENAME_TIMESTAMP_FORMATTER` и метод `nowAsFilenameTimestamp()`.

---

### 7. Ручные breadcrumbs при наличии BreadcrumbAdvice (14 вхождений)

**Файлы:**
- `controller/ExportController.java:62-66, 146-151`
- `controller/ExportStatisticsController.java:84-90, 141-147`

**Суть:** Ручное построение `List<Breadcrumb>` в контроллерах, хотя `BreadcrumbAdvice` уже является `@ControllerAdvice`.

**Решение:** Убрать ручное построение, расширить паттерн-матчинг в `BreadcrumbAdvice` для экспортных путей.

---

## Средний приоритет

### 8. cloneTemplate — идентичный код в двух контроллерах (~18 строк × 2)

**Файлы:**
- `controller/ImportTemplateController.java:251-268`
- `controller/ExportTemplateController.java:215-233`

**Решение:** Логика уже есть в `AbstractTemplateService.cloneTemplate()` — упростить контроллеры до вызова + обработки результата.

---

### 9. ValidationException блок — дублирование в одном классе (~15 строк × 2)

**Файл:** `controller/ImportTemplateController.java:88-108, 185-208`

**Суть:** catch-блок с `e.getErrors().forEach(...)` и `populateAvailableFields()` повторяется в `createTemplate` и `updateTemplate`.

**Решение:** Выделить приватный метод `handleValidationError(...)`.

---

### 10. FileOperation builder — дублирование с TODO-комментарием

**Файлы:**
- `service/imports/AsyncImportService.java:237-248`
- `service/exports/ExportService.java:66-73`

**Суть:** Одинаковый builder-блок с `createdBy("system")` и одинаковым TODO в обоих файлах.

**Решение:** `FileOperationFactory.createForImport()` / `createForExport()`.

---

### 11. Паттерн `orElseThrow("не найдено")` (13+ мест)

**Файлы:** `ExportService.java`, `AsyncImportService.java`, `ExportController.java`, `ExportStatisticsController.java`, `OperationsRestController.java`, `AbstractTemplateService.java`, `HistoricalStatisticsService.java` и другие.

**Суть:** `clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Клиент не найден"))` без центральных констант.

**Решение:** Добавить в `ErrorMessages.java` константы `CLIENT_NOT_FOUND`, `TEMPLATE_NOT_FOUND`. Использовать `AbstractTemplateService.getClientById()` там, где возможно.

---

## Низкий приоритет

### 12. validateFileName — только в StatsProcessorController

**Файлы:** `controller/utils/StatsProcessorController.java:219-262` — полная валидация, остальные 4 утилиты делают только `file.isEmpty()`.

**Решение:** Перенести в `FileValidationUtils`, вызывать из всех 5 контроллеров.

---

### 13. Пагинация — inline Specification в контроллере

**Файл:** `controller/OperationsRestController.java:64-81`

**Решение:** Вынести в `FileOperationSpecifications` (Spring Data Specifications pattern).

---

### 14. DateTimeFormatter — дублирование константы

**Файлы:**
- `util/ControllerUtils.java:8` — `"dd.MM.yyyy HH:mm:ss"`
- `controller/ClientController.java:390` — `"dd.MM.yyyy HH:mm"` (inline)

**Решение:** Вынести в `DateTimeHelper` или `ControllerUtils` как статическую константу.

---

## Порядок выполнения (рекомендуемый)

1. **Пункт 5** — удалить дублирующий `ClientService` (безопасно, без новых абстракций)
2. **Пункт 6** — `DateTimeHelper.nowAsFilenameTimestamp()` (простая утилита)
3. **Пункт 3** — `errorResponse()`/`successResponse()` в `ControllerUtils` (широкое покрытие)
4. **Пункт 2** — `populateOperationStatus()` в `ControllerUtils`
5. **Пункт 11** — константы в `ErrorMessages.java`
6. **Пункт 9** — `handleValidationError()` в `ImportTemplateController`
7. **Пункт 14** — константа форматтера
8. **Пункт 4** — `AbstractNormalizer<R>` (требует осторожности)
9. **Пункт 7** — расширить `BreadcrumbAdvice`
10. **Пункт 8** — упростить `cloneTemplate` контроллеры
11. **Пункт 10** — `FileOperationFactory`
12. **Пункт 12** — `FileValidationUtils`
13. **Пункт 13** — `FileOperationSpecifications`
14. **Пункт 1** — `AbstractFileUtilController` (самый сложный, в конце)
