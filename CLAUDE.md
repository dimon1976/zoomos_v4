# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

- Общаемся на русском языке
- Порт: 8081

## Команды разработки

### Сборка и запуск
```bash
# Запуск приложения (основной способ)
mvn spring-boot:run

# Быстрый запуск с JAR файлом
java -jar target/file-processing-app-1.0-SNAPSHOT.jar --spring.profiles.active=silent

# Сборка проекта
mvn clean compile

# Запуск с конкретным профилем
mvn spring-boot:run -Dspring-boot.run.profiles=silent
mvn spring-boot:run -Dspring-boot.run.profiles=verbose

# Сборка JAR
mvn clean package

# Тесты
mvn test
mvn test -Dtest=FileAnalyzerServiceTest
```

### База данных
- PostgreSQL: `jdbc:postgresql://localhost:5432/zoomos_v4`
- Миграции Flyway в `src/main/resources/db/migration/`
- Тестовый профиль использует H2 в памяти

### Профили приложения
- **silent**: Минимальное логирование (рекомендуемый)
- **verbose**: Максимальная отладка с SQL логами
- **dev**: Разработка с DevTools
- **prod**: Продакшн с оптимизацией

## Архитектура проекта

Zoomos v4 - Spring Boot 3.2.12 приложение для обработки клиентских данных с импортом/экспортом Excel/CSV файлов, статистикой и веб-интерфейсом.

### Основные компоненты

**Сервисы обработки**
- `AsyncImportService` / `AsyncExportService` - асинхронная обработка файлов
- `FileAnalyzerService` - анализ структуры файлов
- `EntityPersistenceService` - сохранение данных в БД
- `ExportDataService` - извлечение данных для экспорта

**Система шаблонов**
- `ImportTemplate` / `ExportTemplate` - шаблоны с маппингом полей
- `TemplateValidationService` - валидация конфигурации шаблонов

**Асинхронная архитектура**
- Пулы потоков: `ImportExecutor-*` и `ExportExecutor-*`
- WebSocket уведомления через `/topic/progress/{operationId}`
- Мониторинг прогресса через `FileOperation` статусы

**Стратегии экспорта**
- `ExportStrategyFactory` выбирает между `DefaultExportStrategy`, `SimpleReportExportStrategy`, `TaskReportExportStrategy`

### Структура URL (UrlConstants)
```
/clients/{clientId}                           # Обзор клиента
/clients/{clientId}/import                    # Страница импорта
/clients/{clientId}/export                    # Страница экспорта
/clients/{clientId}/templates                 # Управление шаблонами
/clients/{clientId}/statistics                # Статистика клиента
/maintenance                                  # Система обслуживания
```

### Система обслуживания
- `MaintenanceSchedulerService` - автоматические задачи (@Scheduled)
- `DatabaseMaintenanceService` - оптимизация БД
- `FileMaintenanceService` - архивирование файлов
- `SystemHealthService` - мониторинг системы
- Планировщик отключен по умолчанию (`maintenance.scheduler.enabled=false`)

### Технологический стек
- **Java 17**, **Spring Boot 3.2.12**
- **PostgreSQL** с Flyway миграциями  
- **Apache POI 5.2.3** (Excel), **OpenCSV 5.8** (CSV)
- **Selenium WebDriver**, **Playwright** для автоматизации браузера
- **Lombok**, **Thymeleaf**, **WebSockets**

### Настройки производительности
```properties
# Импорт
import.batch-size=500
import.max-memory-percentage=60
import.async.core-pool-size=1
import.async.max-pool-size=2

# Экспорт
export.batch-size=1000
export.async.core-pool-size=2
export.async.max-pool-size=4

# Файлы
spring.servlet.multipart.max-file-size=1200MB
application.upload.dir=data/upload
```

## Важные принципы разработки

- **KISS, YAGNI, MVP, Fail Fast** - основа всех решений
- Никаких комментариев в коде (кроме JavaDoc)
- Используй существующие паттерны и сервисы
- Lombok для устранения boilerplate
- `@Slf4j` для логирования
- Следуй структуре пакетов `com.java.*`
- Главный класс: `com.java.Main`

## Текущее состояние

**Завершено**
- Полная система автоматического обслуживания с веб-интерфейсом
- Переход от вкладок к постраничной навигации
- CRUD операции шаблонов
- Рефакторинг с устранением дублирования кода

**Основной поток работы**
1. **Импорт**: Загрузка → Анализ → Выбор шаблона → Асинхронная обработка
2. **Экспорт**: Выбор шаблона → Настройка → Генерация файла
3. **Статистика**: Настройка параметров → Анализ → Результаты