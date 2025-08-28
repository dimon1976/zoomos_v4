# План реализации системы обслуживания приложения

## Цель
Добавить комплексную систему обслуживания для обеспечения быстрой и стабильной работы приложения, автоматической очистки устаревших данных и файлов.

## Текущее состояние
- Существует базовый `CleanupService` с планировщиком
- `PathResolver` для управления путями
- Накопление файлов в `data/upload/exports/` (23 файла)
- Много архивированных логов в `logs/archived/` (165+ файлов)
- База данных: PostgreSQL с таблицами clients, file_operations, import/export_sessions

## Архитектура решения

### 1. Центральное управление - пакет `service/maintenance/`

#### MaintenanceService
- Координация всех задач обслуживания
- Управление планированием и выполнением
- Отчетность и логирование результатов
- Интеграция с существующим CleanupService

#### MaintenanceScheduler
- Автоматическое планирование задач
- Ежедневная очистка временных файлов
- Еженедельная оптимизация БД
- Ежемесячное архивирование файлов
- Настраиваемые пользовательские задачи

### 2. Управление файлами

#### FileManagementService
- Управление файлами в директориях (browse, delete, analyze)
- Очистка по размеру, возрасту, количеству
- Архивирование старых файлов
- Мониторинг дискового пространства
- Безопасное удаление с проверками

#### Расширение CleanupService
- Интеграция с MaintenanceService
- Настраиваемые стратегии очистки
- Селективная очистка по критериям
- Поддержка различных типов файлов

### 3. Обслуживание базы данных

#### DatabaseMaintenanceService
- VACUUM и ANALYZE таблиц PostgreSQL
- Очистка старых записей из sessions и operations
- Реиндексация таблиц для производительности
- Статистика использования БД
- Проверка целостности данных
- Сжатие и архивирование старых данных

### 4. Мониторинг системы

#### SystemHealthService
- Мониторинг памяти JVM и системы
- Проверка дискового пространства
- Мониторинг пулов потоков AsyncExportService
- Диагностика производительности
- Health checks для всех компонентов

### 5. API и интерфейс

#### MaintenanceController
REST API endpoints:
- `/api/maintenance/files/**` - управление файлами
- `/api/maintenance/database/**` - операции с БД
- `/api/maintenance/system/**` - системная информация
- `/api/maintenance/cleanup/**` - задачи очистки
- `/api/maintenance/schedule/**` - управление планировщиком

#### Веб-интерфейс
Расширение UtilsController и шаблонов:
- Дашборд состояния системы
- Интерфейс управления файлами с возможностью удаления
- Инструменты обслуживания БД
- Планировщик задач
- Отчеты по обслуживанию

## Этапы реализации

### Этап 1: Базовая инфраструктура
1. Создать пакет `service/maintenance/`
2. Реализовать `MaintenanceService` как координирующий сервис
3. Создать базовый `MaintenanceController`
4. Добавить конфигурационные настройки в `application.properties`

### Этап 2: Управление файлами
1. Реализовать `FileManagementService`
2. Расширить существующий `CleanupService`
3. Добавить REST endpoints для управления файлами
4. Создать веб-интерфейс для просмотра и удаления файлов

### Этап 3: Обслуживание БД
1. Реализовать `DatabaseMaintenanceService`
2. Добавить SQL скрипты для оптимизации
3. Создать endpoints для операций с БД
4. Добавить интерфейс управления БД

### Этап 4: Мониторинг
1. Реализовать `SystemHealthService`
2. Добавить метрики и health checks
3. Создать дашборд состояния системы
4. Настроить алерты и уведомления

### Этап 5: Автоматизация
1. Реализовать `MaintenanceScheduler`
2. Настроить автоматические задачи
3. Добавить интерфейс управления планировщиком
4. Создать отчеты и логирование

## Конфигурационные настройки

```properties
# Настройки обслуживания
maintenance.enabled=true
maintenance.files.cleanup.enabled=true
maintenance.files.max-age-days=30
maintenance.files.max-size-mb=1024
maintenance.database.vacuum.enabled=true
maintenance.database.cleanup.old-sessions-days=90
maintenance.health.check.enabled=true
maintenance.schedule.daily-cleanup=02:00
maintenance.schedule.weekly-vacuum=Sunday 03:00
```

## Структура файлов

```
src/main/java/com/java/
├── controller/
│   └── MaintenanceController.java    # Новый
├── service/
│   ├── maintenance/                  # Новый пакет
│   │   ├── MaintenanceService.java
│   │   ├── FileManagementService.java
│   │   ├── DatabaseMaintenanceService.java
│   │   ├── SystemHealthService.java
│   │   └── MaintenanceScheduler.java
│   └── CleanupService.java          # Расширить
├── config/
│   └── MaintenanceConfig.java       # Новый
└── dto/
    └── maintenance/                 # Новый пакет
        ├── SystemHealthDto.java
        ├── FileInfoDto.java
        └── MaintenanceReportDto.java

src/main/resources/
├── static/js/
│   └── maintenance.js               # Новый
└── templates/
    └── maintenance/                 # Новый пакет
        ├── dashboard.html
        ├── files.html
        └── database.html
```

## Ожидаемые результаты

1. **Производительность**: Автоматическая очистка и оптимизация БД
2. **Стабильность**: Мониторинг здоровья системы и предотвращение проблем
3. **Управляемость**: Удобный веб-интерфейс для администратора
4. **Автоматизация**: Планировщик задач обслуживания
5. **Безопасность**: Контролируемые операции с проверками
6. **Мониторинг**: Отчеты и статистика по обслуживанию

## Интеграция с существующим кодом

- Использовать существующий `PathResolver` для управления путями
- Интегрировать с `AsyncExportService` для мониторинга пулов потоков
- Расширить `UtilsController` для веб-интерфейса
- Использовать существующие настройки Spring Boot
- Совместимость с профилями приложения (silent, verbose, dev, prod)