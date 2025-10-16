# План оптимизации базы данных Zoomos v4

## Проблема
Таблица `av_data` накопила **45+ миллионов записей** за 2 месяца, что критически замедляет экспорт. Требуется система безопасной очистки устаревших данных с возможностью выбора даты и гибкими настройками.

## Архитектура решения

### 1. Новая миграция Flyway (V17)
**Файл**: `V17__add_data_cleanup_improvements.sql`

**Индексы для оптимизации**:
- Композитный индекс на `av_data(client_id, created_at)` - для фильтрации по клиенту и дате
- Композитный индекс на `av_data(operation_id, created_at)` - для группировки операций
- Индекс на `import_sessions(started_at, status)` - для поиска старых сессий
- Индекс на `export_sessions(started_at, status)` - аналогично для экспорта
- BRIN индекс на `av_data(created_at)` - для больших таблиц с временными данными

**Таблица настроек очистки**:
```sql
CREATE TABLE data_cleanup_settings (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL DEFAULT 30,
    auto_cleanup_enabled BOOLEAN DEFAULT FALSE,
    cleanup_batch_size INTEGER DEFAULT 10000,
    description TEXT
);
```

**Таблица истории очистки**:
```sql
CREATE TABLE data_cleanup_history (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    cleanup_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cutoff_date TIMESTAMP NOT NULL,
    records_deleted BIGINT NOT NULL DEFAULT 0,
    execution_time_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    initiated_by VARCHAR(255)
);
```

### 2. Расширение DatabaseMaintenanceService

**Новые методы**:

**a) `cleanupRawDataByDate(LocalDateTime cutoffDate, int batchSize)`**
- Безопасное batch-удаление устаревших данных **ТОЛЬКО из av_data**
- **ВАЖНО**: av_handbook НЕ трогаем - данные там статичные и редко меняются
- Удаляет данные старше указанной даты порциями (по умолчанию 10000 записей)
- Учитывает связанные таблицы через foreign keys
- Возвращает `DataCleanupResultDto` с детальной статистикой

**b) `previewCleanup(LocalDateTime cutoffDate)`**
- Показывает сколько записей будет удалено БЕЗ фактического удаления
- Группирует по клиентам и операциям
- Оценка освобождаемого места
- **Только для av_data**

**c) `cleanupOldDataSelective(DataCleanupRequestDto request)`**
- Гибкая очистка с фильтрами:
  - Дата до которой удалять
  - Типы данных (av_data, sessions, errors) - **av_handbook исключен**
  - Исключения по клиентам (не удалять данные конкретных клиентов)
  - Batch size для контроля производительности

**d) `getCleanupSettings()`** и **`updateCleanupSettings()`**
- Управление настройками хранения для разных типов данных
- Настройка через UI

**e) `getCleanupHistory()`**
- История выполнения очисток с фильтрацией

### 3. Новый контроллер для UI

**Файл**: `DataCleanupController.java`

**Эндпоинты**:
- `GET /maintenance/data-cleanup` - страница с формой очистки
- `POST /maintenance/data-cleanup/preview` - предпросмотр что будет удалено
- `POST /maintenance/data-cleanup/execute` - выполнение очистки
- `GET /maintenance/data-cleanup/history` - история очисток
- `GET /maintenance/data-cleanup/settings` - текущие настройки
- `PUT /maintenance/data-cleanup/settings` - обновление настроек

### 4. Web-интерфейс

**Файл**: `templates/maintenance/data-cleanup.html`

**Функционал**:
- **Календарь** для выбора даты (до которой удалять данные)
- **Чекбоксы** для выбора типов данных (av_data, sessions, errors) - **av_handbook отсутствует**
- **Мультиселект** исключений по клиентам
- **Кнопка "Предпросмотр"** - показывает что будет удалено без удаления
- **Кнопка "Очистить"** - выполняет удаление с подтверждением
- **Таблица истории** предыдущих очисток
- **Настройки хранения** для разных типов данных
- **Индикатор выполнения** через WebSocket
- **Безопасность**: требует подтверждения с вводом CONFIRM

### 5. DTO классы

**DataCleanupRequestDto**:
```java
- LocalDateTime cutoffDate
- Set<String> entityTypes  // av_data, import_sessions, export_sessions, import_errors
- Set<Long> excludedClientIds
- int batchSize
- boolean dryRun
```

**DataCleanupResultDto**:
```java
- LocalDateTime cleanupTime
- LocalDateTime cutoffDate
- Map<String, Long> deletedRecordsByType
- long totalRecordsDeleted
- long freedSpaceBytes
- String formattedFreedSpace
- long executionTimeMs
- boolean success
- String errorMessage
```

**DataCleanupPreviewDto**:
```java
- Map<String, Long> recordsToDelete
- Map<String, Map<String, Long>> recordsByClient
- long estimatedFreeSpaceBytes
- List<String> warnings
```

### 6. Оптимизация существующего кода

**DatabaseMaintenanceService**:
- Разделить методы на категории: статистика (хранить долго) vs сырые данные
- Метод `cleanupOldData()` НЕ должен трогать `export_statistics` и `export_sessions` (изменить логику)
- Добавить property `database.maintenance.statistics.retention-days=730` (2 года)
- Добавить property `database.maintenance.raw-data.retention-days=30` (30 дней)

**ExportDataService**:
- Запросы уже используют фильтрацию - хорошо
- Добавить использование новых индексов в критичные запросы
- Рассмотреть партиционирование `av_data` по дате в будущем (для очень больших объемов)

### 7. Автоматизация (опционально)

**MaintenanceSchedulerService** - добавить новый scheduled метод:
```java
@Scheduled(cron = "${maintenance.scheduler.raw-data-cleanup.cron:-}")
@ConditionalOnProperty(name = "maintenance.scheduler.enabled", havingValue = "true")
public void scheduledRawDataCleanup() {
    // Выполняется только если auto_cleanup_enabled=true в настройках
}
```

**Application.properties**:
```properties
# Очистка сырых данных (ОТКЛЮЧЕНО по умолчанию)
maintenance.scheduler.raw-data-cleanup.cron=0 0 4 * * *
maintenance.scheduler.raw-data-cleanup.enabled=false
```

### 8. Безопасность

**Защита от случайного удаления**:
- Минимальная дата cutoff = "сегодня - 7 дней" (нельзя удалить данные моложе недели)
- Требование явного подтверждения в UI (ввод "CONFIRM")
- Логирование всех операций очистки
- Dry-run режим для проверки перед удалением
- **av_handbook полностью исключен из очистки**

**Транзакции**:
- Удаление большими batch в отдельных транзакциях
- При ошибке rollback только текущего batch
- Общий прогресс сохраняется

## Важные ограничения

⚠️ **av_handbook НЕ подлежит автоматической очистке**
- Данные меняются редко
- Содержит важную справочную информацию
- Удаление только вручную через отдельный функционал при необходимости

## Порядок реализации

1. ✅ Создать миграцию V17 с индексами и таблицами
2. ✅ Создать DTO классы
3. ✅ Расширить DatabaseMaintenanceService новыми методами
4. ✅ Создать DataCleanupController
5. ✅ Создать UI data-cleanup.html
6. ✅ Добавить ссылку в navigation на новую страницу
7. ✅ Протестировать на реальных данных
8. ✅ Документировать использование

## Результат

- **Гибкая система очистки** с выбором даты через календарь
- **Безопасность**: предпросмотр, подтверждение, исключения, av_handbook защищен
- **Производительность**: batch-удаление, индексы, оптимизированные запросы
- **Мониторинг**: история операций, статистика
- **Разделение данных**: статистика хранится долго, сырые данные (av_data) очищаются часто
- **Ручной запуск** с возможностью автоматизации позже
