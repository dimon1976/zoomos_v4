# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Основные принципы
- Общаемся на русском языке
- Разработка ведется на ОС Windows
- Не редактируй .env файл - лишь говори какие переменные нужно туда добавить
- Это pet проект, не усложняй без необходимости
- Если чего-то не знаешь, не придумывай, так и говори

## Обзор проекта

Zoomos v4 - это Spring Boot 3.2.12 приложение для обработки файлов клиентов с функциями импорта/экспорта. Поддерживает асинхронную обработку Excel и CSV файлов с настраиваемыми шаблонами, анализом статистики и веб-интерфейсом.

## Команды разработки

### Сборка и запуск
```bash
# Собрать проект
mvn clean compile

# Запустить приложение
mvn spring-boot:run

# Собрать JAR файл  
mvn clean package

# Запустить тесты
mvn test

# Запустить конкретный тест
mvn test -Dtest=ClassNameTest

# Запуск с профилем
mvn spring-boot:run -Dspring-boot.run.profiles=silent

# Быстрый запуск для разработки
start-dev.bat
```

### База данных
- PostgreSQL: `jdbc:postgresql://localhost:5432/zoomos_v4` (postgres/root)
- Миграции Flyway в `src/main/resources/db/migration/`
- Тестовый профиль использует H2 в памяти

## Архитектура

### Основные потоки обработки
1. **Импорт**: Загрузка → Анализ → Выбор шаблона → Асинхронная обработка → Мониторинг статуса
2. **Экспорт**: Выбор шаблона → Выбор операций → Асинхронная обработка → Генерация файла
3. **Статистика**: Настройка → Анализ → Визуализация результатов

### Ключевые паттерны

**Асинхронная обработка**
- Двойные пулы потоков: `ImportExecutor-*` и `ExportExecutor-*`
- Отслеживание прогресса через WebSocket и сессии БД
- Конфигурируемые пулы через `import.async.*` и `export.async.*`

**Strategy для экспорта**
- `ExportStrategyFactory` выбирает между стратегиями:
  - `DefaultExportStrategy` - стандартный экспорт
  - `SimpleReportExportStrategy` - простой отчет
  - `TaskReportExportStrategy` - отчет по задачам

**Система шаблонов**
- `ImportTemplate` и `ExportTemplate` с mapping полей и фильтрами
- Шаблоны привязаны к клиентам, поддерживают клонирование
- Валидация через `TemplateValidationService`

### Структура URL (UrlConstants)
```
/clients                          # Список клиентов
/clients/{clientId}               # Обзор клиента
/clients/{clientId}/import        # Страница импорта
/clients/{clientId}/export        # Страница экспорта
/clients/{clientId}/templates     # Управление шаблонами
/clients/{clientId}/statistics    # Статистика клиента
/clients/{clientId}/operations    # История операций
```

### Организация сервисов

**Клиентские сервисы**
- `ClientService` - управление клиентами
- `DashboardService` - кросс-клиентская статистика

**Обработка файлов**
- `AsyncImportService`/`AsyncExportService` - асинхронная обработка
- `ImportProcessorService`/`ExportProcessorService` - основная логика
- `FileAnalyzerService` - анализ структуры файлов

**Шаблоны**
- `ImportTemplateService`/`ExportTemplateService` - управление шаблонами
- `TemplateValidationService` - валидация полей

**Данные**
- `EntityPersistenceService` - операции БД для импортированных данных
- `DuplicateCheckService` - обнаружение дубликатов
- `ExportDataService` - получение данных для экспорта

## Конфигурация

### Производительность
```properties
# Импорт
import.batch-size=500
import.max-memory-percentage=60
import.timeout-minutes=60

# Экспорт
export.async.threshold-rows=10000
export.batch-size=1000
export.xlsx.max-rows=1048576

# Пулы потоков
import.async.core-pool-size=1
import.async.max-pool-size=2
export.async.core-pool-size=2
export.async.max-pool-size=4
```

### Файлы
```properties
# Ограничения размера
spring.servlet.multipart.max-file-size=1200MB
spring.servlet.multipart.max-request-size=1200MB

# Директории
application.upload.dir=data/upload
application.export.dir=data/upload/exports
application.temp.dir=data/temp
```

## Модель данных

### Основные сущности
- `Client` - центральная сущность, связывает все операции
- `FileOperation` - отслеживает все операции импорта/экспорта
- `ImportSession`/`ExportSession` - данные и конфигурация операций
- `ImportTemplate`/`ExportTemplate` - шаблоны обработки

### Статистика
- `ExportStatistics` - результаты анализа с настраиваемыми порогами
- `ImportError` - отслеживание ошибок с категоризацией
- `FileMetadata` - результаты анализа файлов

## Обработка ошибок

**Иерархия исключений**
- `FileOperationException` - базовый класс для файловых операций
- `ImportException` - ошибки импорта
- `TemplateValidationException` - ошибки конфигурации шаблонов

**Глобальная обработка**
- `GlobalExceptionHandler` - обработка исключений для всего приложения
- `ErrorMessages` - централизованные сообщения об ошибках

## WebSocket интеграция

Обновления прогресса в реальном времени:
- Обновления транслируются в `/topic/progress/{operationId}`
- JavaScript в `main.js` обрабатывает WebSocket соединения
- Автоматическое переподключение и обработка ошибок

## Технологический стек

### Backend
- **Framework**: Spring Boot 3.2.12 с Java 17
- **База данных**: PostgreSQL с миграциями Flyway
- **Обработка файлов**: Apache POI 5.2.3 (Excel), OpenCSV 5.8 (CSV)
- **Определение кодировки**: juniversalchardet 2.4.0
- **Шаблонизатор**: Thymeleaf
- **WebSockets**: Spring WebSocket с SockJS/STOMP
- **Разработка**: Spring DevTools, Lombok

### Тестирование
- Spring Boot Test с H2 in-memory для тестов
- Тестовый профиль автоматически переключается с PostgreSQL на H2

## Среда разработки

### Профили приложения
- **verbose**: Стандартное логирование, детальный вывод
- **silent**: Минимальное логирование для продакшен-подобной среды

### Конфигурация сервера
- **Порт**: 8081 (по умолчанию)
- **Hot Reload**: Spring DevTools включен с LiveReload
- **Thymeleaf**: Кеширование отключено для разработки

## Рекомендации для ИИ-ассистента

* При реализации фич с внешними API используй Context7 для изучения документации
* Если есть изменения на фронтенде, проверь работу через playwright
* Обязательно останавливай запущенный сервер после тестирования
* Используй существующие паттерны вместо изобретения новых
* При рефакторинге сохраняй всю существующую бизнес-логику

## Текущие особенности

**Навигация**
Переход от табов к отдельным страницам с proper URL структурой:
1. **Импорт**: Загрузка → Анализ → Выбор шаблона → Старт → Статус
2. **Экспорт**: Страница экспорта → Форма старта → Статус  
3. **Статистика**: Настройка → Анализ → Результаты

**JavaScript паттерны**
- Извлечение client ID из URL paths вместо Thymeleaf inlining
- Глобальные переменные для состояния между функциями
- Обработка ошибок с user-friendly сообщениями
- Автоматическое обновление данных каждые 30 секунд