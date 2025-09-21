# Data Merger Utility - Техническое видение

## 1. Технологии

### Используем существующий стек Zoomos v4

- **Backend**: Java 17 + Spring Boot 3.2.12
- **Обработка файлов**: Apache POI (Excel), OpenCSV (CSV)
- **Frontend**: Thymeleaf + Bootstrap 5 + Vanilla JavaScript
- **Переиспользование**: FileAnalyzerService, FileGeneratorService
- **БД не требуется**: Вся обработка in-memory

### Принцип выбора технологий

- Никаких новых зависимостей
- Максимальное переиспользование существующего функционала
- Простота важнее производительности

## 2. Принципы разработки

### Основные принципы

- **KISS** - Keep It Simple, Stupid
- **YAGNI** - You Aren't Gonna Need It
- **MVP** - Minimum Viable Product
- **Fail Fast** - Быстрое выявление ошибок

### Конкретные решения

- Один контроллер, один сервис
- Никаких Entity, только DTO
- Простая валидация без оверинжиниринга
- Unit тесты не обязательны на первом этапе
- Итеративная разработка

## 3. Структура проекта

### Файловая структура

```
src/main/java/com/java/
├── controller/utils/
│   └── DataMergerController.java           # Единственный контроллер
├── service/utils/
│   └── DataMergerService.java              # Бизнес-логика
├── dto/utils/
│   ├── SourceProductDto.java               # DTO исходного файла
│   ├── ProductLinksDto.java                # DTO файла ссылок
│   ├── MergedProductDto.java               # DTO результата
│   └── DataMergerConfig.java               # Конфигурация маппинга
└── exception/
    └── DataMergerException.java            # Специфичные ошибки
```

### Веб-ресурсы

```text
src/main/resources/
├── templates/utils/
│   └── data-merger.html                    # Страница утилиты
└── static/js/utils/
    └── data-merger.js                       # JavaScript логика
```

### Маршрутизация

- `GET /utils/data-merger` - форма загрузки файлов
- `POST /utils/data-merger/process` - обработка и скачивание

## 4. Архитектура

### Простой поток данных

```text
Пользователь
    ↓
HTML форма (загрузка 2 файлов)
    ↓
DataMergerController
    ↓
DataMergerService (обработка in-memory)
    ↓
FileGeneratorService (экспорт)
    ↓
Скачивание результата
```

### Компоненты и их ответственность

**DataMergerController**

- Принимает 2 файла от пользователя
- Принимает конфигурацию маппинга столбцов
- Вызывает сервис обработки
- Возвращает файл для скачивания

**DataMergerService**

- Парсит оба файла используя FileAnalyzerService
- Применяет маппинг столбцов
- Объединяет данные по ключу (модель аналога)
- Формирует результат

### Обработка ошибок

- Валидация на уровне контроллера
- DataMergerException для бизнес-ошибок
- Использование существующего GlobalExceptionHandler

### Интеграция с существующими сервисами

- FileAnalyzerService - анализ структуры файлов
- FileGeneratorService - генерация CSV/Excel результата
- Переиспользование логики ImportTemplateField (без БД)

## 5. Модель данных

### Основные DTO

```java
// DTO для строки исходного файла
@Data @Builder
class SourceProductDto {
    String id;              // ID товара-оригинала
    String originalModel;   // Модель оригинала
    String analogModel;     // Модель аналога (ключ)
    Double coefficient;     // Коэффициент аналога
    Map<String, String> additionalFields; // Дополнительные поля
}

// DTO для строки файла ссылок
@Data @Builder
class ProductLinksDto {
    String analogModel;     // Модель аналога (ключ)
    String link;            // Ссылка на карточку товара
}

// DTO результата объединения
@Data @Builder
class MergedProductDto {
    String id;
    String originalModel;
    String analogModel;
    Double coefficient;
    String link;
    Map<String, String> additionalFields;
}
```

### Конфигурация маппинга (переиспользуем логику ImportTemplateField)

```java
// Маппинг полей для одного файла
@Data @Builder
class DataMergerFieldMapping {
    String columnName;      // Название столбца в файле
    Integer columnIndex;    // Индекс столбца (0-based)
    String fieldType;       // Тип поля: "id", "originalModel", "analogModel", "coefficient", "link"
    Boolean isRequired;     // Обязательное поле
}

// Полная конфигурация маппинга
@Data @Builder
class DataMergerConfig {
    List<DataMergerFieldMapping> sourceFileMapping;  // Маппинг исходного файла
    List<DataMergerFieldMapping> linksFileMapping;   // Маппинг файла ссылок
    List<String> outputFields;                       // Поля для включения в результат
    String outputFormat;                              // "CSV" или "XLSX"
}
```

### Валидация

- Базовая проверка обязательных полей
- Проверка наличия ключевого поля (модель аналога) в обоих файлах
- Проверка типа коэффициента (должен быть числом)

## 6. Сценарии работы

### Основной сценарий

1. **Загрузка файлов**
   - Пользователь открывает `/utils/data-merger`
   - Загружает исходный файл (товары с аналогами)
   - Загружает файл ссылок (аналоги со ссылками)

2. **Настройка столбцов**
   - Система анализирует файлы через FileAnalyzerService
   - Показывает первые 5 строк каждого файла
   - Пользователь выбирает столбцы через выпадающие списки:
     * Исходный: ID, Модель оригинал, Модель аналог, Коэффициент
     * Ссылки: Модель аналог, Ссылка
   - Выбирает какие поля включить в результат

3. **Обработка**
   - Нажимает "Объединить данные"
   - Система обрабатывает файлы в памяти
   - Объединяет по ключу (модель аналога)
   - Генерирует развернутый результат

4. **Скачивание**
   - Автоматическое скачивание результата
   - Формат CSV или XLSX (выбор пользователя)

### Обработка особых случаев

- **Аналог без ссылок**: Пропускается с предупреждением
- **Дубликаты**: Создается отдельная строка для каждой ссылки
- **Пустые значения**: Используются значения по умолчанию
- **Ошибки парсинга**: Логирование и пропуск строки

### Ограничения MVP

- Без сохранения конфигураций маппинга
- Без асинхронной обработки (все синхронно)
- Без прогресс-бара
- Максимум 100 000 строк на файл
- Без preview результата

## 7. Подход к логгированию

### Конфигурация логирования

```java
@Slf4j // Lombok annotation
class DataMergerService {
    // Используем SLF4J через Lombok
}
```

### Уровни логирования

- **INFO**: Ключевые этапы обработки
  ```java
  log.info("Starting data merger: source={} rows, links={} rows", sourceCount, linksCount);
  log.info("Column mapping configured: source={}, links={}", sourceMapping, linksMapping);
  log.info("Data merger completed: {} records generated in {} ms", resultCount, duration);
  ```

- **WARN**: Некритичные проблемы
  ```java
  log.warn("Analog '{}' not found in links file, skipping", analogModel);
  log.warn("Invalid coefficient value '{}' for row {}, using default", value, rowNum);
  ```

- **ERROR**: Критические ошибки
  ```java
  log.error("Failed to parse source file: {}", filename, exception);
  log.error("Column mapping validation failed: {}", validationErrors);
  ```

- **DEBUG**: Детали для отладки (только в dev профиле)
  ```java
  log.debug("Processing row {}: {}", rowNum, rowData);
  log.debug("Found {} links for analog '{}'", linksCount, analogModel);
  ```

### Статистика в логах

В конце обработки выводим статистику:
```
INFO: Data merger summary:
  - Source file: 1000 rows processed
  - Links file: 500 unique analogs
  - Result: 3500 merged records
  - Skipped: 50 analogs without links
  - Processing time: 1234 ms
```

### Вывод логов
- Консоль - основной вывод
- Файловое логирование - не требуется для MVP
- Уровень по умолчанию: INFO

## 8. План реализации (следующие шаги)

### Этап 1: Базовая функциональность
1. Создать DTO классы
2. Реализовать DataMergerController
3. Реализовать DataMergerService
4. Создать HTML страницу
5. Добавить JavaScript для динамического маппинга

### Этап 2: Интеграция
1. Интегрировать с FileAnalyzerService
2. Интегрировать с FileGeneratorService
3. Добавить обработку ошибок
4. Настроить логирование

### Этап 3: Тестирование
1. Тестирование с реальными файлами
2. Проверка граничных случаев
3. Оптимизация производительности

### Этап 4: Улучшения (если потребуется)
1. Добавить preview результата
2. Сохранение конфигураций маппинга
3. Асинхронная обработка для больших файлов
4. Прогресс-бар

---

**Дата создания**: 2025-09-21
**Статус**: Техническое видение утверждено
**Принцип**: KISS - максимально простое решение для MVP