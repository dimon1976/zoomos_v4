# 📋 Инструкция: Переиспользование функционала анализа файлов

## 🔧 Архитектура FileAnalyzerService

### Основной компонент: `FileAnalyzerService`
**Местоположение:** `src/main/java/com/java/service/file/FileAnalyzerService.java`

**Зависимости:**
- `PathResolver` - для работы с временными файлами
- `ImportConfig.ImportSettings` - настройки импорта
- `ObjectMapper` - для работы с JSON

### Возможности сервиса:
1. **Автоопределение кодировки** (UTF-8, Windows-1251, etc.)
2. **Автоопределение разделителя CSV** (`,`, `;`, `\t`, `|`)
3. **Автоопределение символов кавычек и экранирования**
4. **Извлечение заголовков** файлов
5. **Генерация MD5 хеша** файла
6. **Анализ структуры Excel/CSV** файлов

## 📘 Правильные паттерны использования

### 1. Базовое использование

```java
@Service
@RequiredArgsConstructor
public class MyFileProcessorService {

    private final FileAnalyzerService fileAnalyzerService;
    private final ObjectMapper objectMapper;

    public void processFile(MultipartFile file) {
        try {
            // 🟢 ПРАВИЛЬНО: Используем FileAnalyzerService
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "my-prefix");

            // Извлекаем параметры парсинга
            Charset charset = Charset.forName(metadata.getDetectedEncoding());
            char delimiter = metadata.getDetectedDelimiter().charAt(0);
            char quoteChar = metadata.getDetectedQuoteChar().charAt(0);

            // Используем для парсинга
            parseFileWithParameters(file, charset, delimiter, quoteChar);

        } catch (IOException e) {
            log.error("Ошибка анализа файла", e);
        }
    }
}
```

### 2. Извлечение заголовков

```java
private List<String> extractHeaders(FileMetadata metadata) {
    try {
        if (metadata.getColumnHeaders() == null || metadata.getColumnHeaders().isEmpty()) {
            return new ArrayList<>();
        }

        // 🟢 ПРАВИЛЬНО: Парсим JSON заголовки из FileMetadata
        return objectMapper.readValue(
            metadata.getColumnHeaders(),
            new TypeReference<List<String>>() {}
        );
    } catch (Exception e) {
        log.warn("Failed to parse headers: {}", e.getMessage());
        return new ArrayList<>();
    }
}
```

### 3. Парсинг CSV с правильными параметрами

```java
private void parseCsvFile(MultipartFile file, FileMetadata metadata) throws IOException {
    Charset charset = Charset.forName(metadata.getDetectedEncoding());
    char delimiter = metadata.getDetectedDelimiter().charAt(0);
    char quoteChar = metadata.getDetectedQuoteChar().charAt(0);
    char escapeChar = metadata.getDetectedEscapeChar() != null ?
        metadata.getDetectedEscapeChar().charAt(0) : CSVParser.NULL_CHARACTER;

    try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), charset)) {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar(quoteChar)
                .withEscapeChar(escapeChar)
                .build();

        try (CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(parser)
                .withSkipLines(1) // Пропускаем заголовок если нужно
                .build()) {

            // 🟢 ПРАВИЛЬНО: Используем правильные параметры парсинга
            // Парсим файл...
        }
    }
}
```

## ❌ Что НЕ НАДО делать (антипаттерны)

### 1. Дублирование логики анализа

```java
// 🔴 НЕПРАВИЛЬНО: Собственная логика определения кодировки
private Charset detectCharset(MultipartFile file) {
    // НЕ ДЕЛАТЬ ТАК - уже есть в FileAnalyzerService!
}

// 🔴 НЕПРАВИЛЬНО: Хардкодные значения
CSVParser parser = new CSVParserBuilder()
    .withSeparator(',')  // Хардкод разделителя
    .build();
```

### 2. Игнорирование метаданных файла

```java
// 🔴 НЕПРАВИЛЬНО: Использование UTF-8 по умолчанию
try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
    // Может не сработать для файлов в других кодировках
}
```

### 3. Самостоятельный парсинг заголовков

```java
// 🔴 НЕПРАВИЛЬНО: Ручное чтение первой строки
String[] headers = csvReader.readNext(); // НЕ ДЕЛАТЬ
```

## 🔄 Пример рефакторинга (как было исправлено в DataMergerService)

### До (неправильно):
```java
// Дублированная логика
private Charset detectCharset(MultipartFile file) { /* ... */ }
private List<String> readCsvHeaders(MultipartFile file) { /* ... */ }

CSVParser parser = new CSVParserBuilder()
    .withSeparator(',')  // Хардкод
    .build();
```

### После (правильно):
```java
// Переиспользование FileAnalyzerService
FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "data-merger-source");

Charset charset = Charset.forName(metadata.getDetectedEncoding());
char delimiter = metadata.getDetectedDelimiter().charAt(0);
char quoteChar = metadata.getDetectedQuoteChar().charAt(0);

List<String> headers = extractHeadersFromMetadata(metadata);
```

## 🎯 Рекомендации по структуре сервиса

### Зависимости в конструкторе:
```java
@Service
@RequiredArgsConstructor
public class MyUtilityService {

    private final FileAnalyzerService fileAnalyzerService;  // 🟢 Обязательно
    private final ObjectMapper objectMapper;               // 🟢 Для JSON заголовков
}
```

### Методы анализа:
```java
// 🟢 Создайте метод для извлечения заголовков
private List<String> extractHeadersFromMetadata(FileMetadata metadata) { /* ... */ }

// 🟢 Создайте метод для получения параметров парсинга
private CsvParsingParams getParsingParams(FileMetadata metadata) { /* ... */ }
```

## 📋 Чеклист перед созданием нового сервиса

- [ ] Добавил зависимость `FileAnalyzerService`
- [ ] Добавил зависимость `ObjectMapper`
- [ ] Использую `fileAnalyzerService.analyzeFile()` вместо собственной логики
- [ ] Извлекаю заголовки из `metadata.getColumnHeaders()` через JSON
- [ ] Использую `metadata.getDetectedEncoding()` для кодировки
- [ ] Использую `metadata.getDetectedDelimiter()` для разделителя
- [ ] Использую `metadata.getDetectedQuoteChar()` для кавычек
- [ ] Обрабатываю `metadata.getDetectedEscapeChar()` (может быть null)
- [ ] Не дублирую логику определения параметров файла

## 🔍 Полезные поля FileMetadata

- `detectedEncoding` - кодировка файла
- `detectedDelimiter` - разделитель CSV
- `detectedQuoteChar` - символ кавычек
- `detectedEscapeChar` - символ экранирования (может быть null)
- `columnHeaders` - JSON массив заголовков
- `totalColumns` - количество столбцов
- `hasHeader` - есть ли заголовки
- `fileFormat` - формат файла (CSV, XLSX, XLS)
- `sampleData` - примеры данных в JSON

## 📝 Примеры существующего использования

Смотрите эти файлы для примеров правильного использования:
- `DataMergerService.java` - корректное переиспользование после рефакторинга
- `AsyncImportService.java` - использование в основной системе импорта
- `ImportController.java` - интеграция с контроллерами

## 🚀 Заключение

Эта инструкция поможет избежать дублирования кода и обеспечит правильное переиспользование проверенной логики анализа файлов. Всегда используйте `FileAnalyzerService` вместо создания собственной логики анализа файлов.