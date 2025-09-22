# Data Merger - Соглашения по разработке

## Основные правила

Этот документ определяет правила разработки утилиты Data Merger.
Детальное техническое видение см. в [@vision.md](@vision.md).

## Принципы кода

### KISS превыше всего

- **Один класс - одна ответственность**
- **Минимум абстракций** - никаких интерфейсов без необходимости
- **Прямолинейный код** - лучше понятный, чем "умный"
- **Никаких Entity** - только DTO для обработки данных

### Структура

- **Один контроллер** - `DataMergerController`
- **Один сервис** - `DataMergerService`
- **Минимум классов** - объединяй логику, если это не усложняет
- **Переиспользуй существующее** - FileAnalyzerService, FileGeneratorService

### Код-стиль

```java
// ✅ ХОРОШО - простой и понятный
public List<MergedProductDto> mergeData(List<SourceProductDto> source,
                                         List<ProductLinksDto> links) {
    Map<String, List<String>> linkMap = createLinkMap(links);
    return source.stream()
        .flatMap(s -> createMergedRecords(s, linkMap))
        .collect(toList());
}

// ❌ ПЛОХО - оверинжиниринг
public interface MergeStrategy<T, R> {
    R merge(T source, Map<String, ?> context);
}
```

## Правила разработки

### Валидация

- **Fail Fast** - проверяй на входе метода
- **Базовая проверка** - только критичные ошибки
- **Простые сообщения** - "Файл не найден", не "Unable to locate file resource"

### Обработка ошибок

- **Один тип исключения** - `DataMergerException`
- **Логируй и пропускай** - не останавливай обработку из-за одной строки
- **Статистика в конце** - сколько обработано, сколько пропущено

### Логирование

```java
@Slf4j
class DataMergerService {
    // Только важные события
    log.info("Processing {} source rows, {} link rows", sourceCount, linkCount);
    log.warn("Analog '{}' not found, skipping", model);
    log.error("Failed to parse file", exception);
}
```

## Тестирование

### MVP подход

- **Ручное тестирование** с реальными файлами
- **Unit тесты потом** - сначала рабочий код
- **Тестовые данные** - использовать примеры из [@idea.md](@idea.md)

## Производительность

### In-Memory обработка

- **Все в памяти** - никакой БД
- **Stream API** где уместно - но без фанатизма
- **Ограничение** - максимум 100K строк на файл

## UI/UX

### Простота интерфейса

- **Минимум шагов** - загрузил → настроил → скачал
- **Vanilla JavaScript** - никаких фреймворков
- **Bootstrap компоненты** - используй готовое

## Чего НЕ делать

❌ **НЕ создавай**:
- Абстрактные классы "на будущее"
- Интерфейсы с одной реализацией
- Фабрики, стратегии, декораторы
- Кэширование, оптимизации
- Асинхронную обработку
- Unit тесты на первом этапе

✅ **Делай**:
- Простой синхронный код
- Прямую обработку данных
- Минимальную валидацию
- Быстрый результат

## Примеры кода

### DTO - просто и достаточно

```java
@Data @Builder
public class MergedProductDto {
    private String id;
    private String originalModel;
    private String analogModel;
    private Double coefficient;
    private String link;
    // Никаких сложных вложенных структур
}
```

### Сервис - прямолинейная логика

```java
@Service
@Slf4j
public class DataMergerService {

    public List<MergedProductDto> processFiles(
            MultipartFile sourceFile,
            MultipartFile linksFile,
            DataMergerConfig config) {

        // 1. Парсим файлы
        var sourceData = parseSourceFile(sourceFile, config);
        var linksData = parseLinksFile(linksFile, config);

        // 2. Объединяем
        return mergeData(sourceData, linksData);
    }
    // Никаких лишних абстракций
}
```

## Итог

**Цель**: Рабочий MVP за минимальное время.
**Метод**: Простой код без излишеств.
**Результат**: Утилита, решающая задачу пользователя.

---

При разработке всегда обращайся к [@vision.md](@vision.md) за деталями архитектуры и [@idea.md](@idea.md) за примерами данных.