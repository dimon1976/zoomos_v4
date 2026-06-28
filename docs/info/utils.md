# Утилиты (/utils) — руководство по разработке

## Что такое утилита

Страница `/utils` содержит инструменты для разовой обработки файлов пользователем:
загрузить файл → настроить параметры → скачать результат. Каждая утилита — отдельный
Spring MVC controller + service + Thymeleaf-шаблон.

**Текущие утилиты:**

| Утилита | URL | Сервис |
|---|---|---|
| Redirect Finder | `/utils/redirect-finder` | `RedirectFinderService` |
| Data Merger | `/utils/data-merger` | `DataMergerService` |
| Barcode Match | `/utils/barcode-match` | `BarcodeMatchService` |
| URL Cleaner | `/utils/url-cleaner` | `UrlCleanerService` |
| Link Extractor | `/utils/link-extractor` | `LinkExtractorService` |
| Stats Processor | `/utils/stats-processor` | `StatsProcessorService` |
| Normalization | `/utils/normalization` | (контроллер, без файлов) |

---

## Архитектура чтения файлов

### ПРАВИЛО: не дублировать код чтения файлов

Весь код чтения CSV/Excel сосредоточен в `FileReaderUtils` (`com.java.util.FileReaderUtils`).
**Никогда не пишите свои методы `readCsvFile`, `readExcelFile`, `readFullFileData` в сервисе утилиты.**

### Два метода для разных сценариев

```java
@RequiredArgsConstructor
public class MyUtilityService {
    private final FileReaderUtils fileReaderUtils;

    // Вариант 1: нужны ВСЕ строки включая заголовок (data.get(0) = заголовки)
    // Используют: UrlCleaner, LinkExtractor, RedirectFinder
    List<List<String>> data = fileReaderUtils.readAllRows(metadata);

    // Вариант 2: только данные, заголовок пропущен
    // Используют: BarcodeMatch, StatsProcessor
    List<List<String>> data = fileReaderUtils.readFullFileData(metadata);
}
```

**Когда какой выбрать:**
- `readAllRows` — если сервис сам определяет колонки из `data.get(0)` (заголовочная строка)
- `readFullFileData` — если заголовки уже известны из `metadata.getColumnHeaders()` или DTO

### Что `FileReaderUtils` делает за тебя

- CSV: `CSVReaderBuilder` с корректными delimiter/quoteChar из `metadata`
- CSV: пропуск BOM/пустой первой строки
- CSV/Excel: фильтрация полностью пустых строк
- Excel: `WorkbookFactory.create()` — автоопределение XLSX/XLS
- Excel: `getCellValue(cell)` — числа как `long`, даты, формулы, boolean

---

## Архитектура записи файлов

### ПРАВИЛО: не генерировать Excel/CSV вручную

Используй `FileGeneratorService` (`com.java.service.exports.FileGeneratorService`).
Сборка файла через `ExportTemplate` + `Stream<Map<String, Object>>`.

```java
@RequiredArgsConstructor
public class MyUtilityService {
    private final FileGeneratorService fileGeneratorService;

    public byte[] process(FileMetadata metadata, MyDto dto) throws IOException {
        // 1. Читаем файл
        List<List<String>> data = fileReaderUtils.readAllRows(metadata);

        // 2. Обрабатываем (бизнес-логика утилиты)
        List<MyResult> results = doWork(data, dto);

        // 3. Описываем структуру выходного файла
        ExportTemplate template = buildTemplate(dto);

        // 4. Генерируем файл
        Stream<Map<String, Object>> stream = results.stream().map(this::toMap);
        String fileName = "my-util-result_" + System.currentTimeMillis();
        Path filePath = fileGeneratorService.generateFile(stream, results.size(), template, fileName);
        return Files.readAllBytes(filePath);
    }
}
```

### Построение ExportTemplate

```java
private ExportTemplate buildTemplate(MyDto dto) {
    ExportTemplate template = ExportTemplate.builder()
            .name("My Utility Export")
            .fileFormat("excel".equalsIgnoreCase(dto.getOutputFormat()) ? "XLSX" : "CSV")
            .csvDelimiter(dto.getCsvDelimiter())       // обычно ";" или ","
            .csvEncoding(dto.getCsvEncoding())          // обычно "UTF-8"
            .csvQuoteChar("\"")
            .csvIncludeHeader(true)
            .fields(new ArrayList<>())
            .build();

    List<ExportTemplateField> fields = new ArrayList<>();
    int order = 1;
    fields.add(ExportTemplateField.builder()
            .template(template)
            .entityFieldName("ID")           // ключ в Map из toMap()
            .exportColumnName("ID")          // заголовок колонки в файле
            .fieldOrder(order++)
            .isIncluded(true)
            .build());
    // ... добавляй поля по аналогии

    template.setFields(fields);
    return template;
}

private Map<String, Object> toMap(MyResult result) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("ID", result.getId());          // ключ = entityFieldName выше
    map.put("URL", result.getUrl());
    return map;
}
```

---

## Минимальный скелет новой утилиты

### 1. DTO для параметров

```java
// src/main/java/com/java/dto/utils/MyUtilDto.java
@Data
public class MyUtilDto {
    private Integer urlColumn;          // индекс колонки (0-based)
    private String outputFormat;        // "excel" | "csv"
    private String csvDelimiter = ";";
    private String csvEncoding = "UTF-8";
}
```

### 2. Сервис

```java
// src/main/java/com/java/service/utils/MyUtilService.java
@Service
@RequiredArgsConstructor
@Slf4j
public class MyUtilService {

    private final FileReaderUtils fileReaderUtils;
    private final FileGeneratorService fileGeneratorService;

    public byte[] process(FileMetadata metadata, MyUtilDto dto) throws IOException {
        List<List<String>> data = fileReaderUtils.readAllRows(metadata);
        if (data.isEmpty()) throw new IllegalArgumentException("Файл пуст");

        List<MyResult> results = doWork(data, dto);

        ExportTemplate template = buildTemplate(dto);
        Stream<Map<String, Object>> stream = results.stream().map(this::toMap);
        Path filePath = fileGeneratorService.generateFile(stream, results.size(), template,
                "my-util_" + System.currentTimeMillis());
        return Files.readAllBytes(filePath);
    }

    private List<MyResult> doWork(List<List<String>> data, MyUtilDto dto) {
        // data.get(0) — заголовки (если readAllRows)
        // data.get(1..n) — строки данных
        // ...
    }

    private ExportTemplate buildTemplate(MyUtilDto dto) { /* см. выше */ }
    private Map<String, Object> toMap(MyResult r) { /* см. выше */ }
}
```

### 3. Контроллер

```java
// src/main/java/com/java/controller/utils/MyUtilController.java
@Controller
@RequestMapping("/utils/my-util")
@RequiredArgsConstructor
@Slf4j
public class MyUtilController {

    private final MyUtilService service;
    private final FileMetadataRepository fileMetadataRepository;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("dto", new MyUtilDto());
        return "utils/my-util";
    }

    @PostMapping("/process")
    @ResponseBody
    public ResponseEntity<byte[]> process(
            @RequestParam Long fileId,
            MyUtilDto dto) throws IOException {

        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
        byte[] result = service.process(metadata, dto);

        String filename = "my-util-result.xlsx"; // или .csv по dto.outputFormat
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result);
    }
}
```

### 4. Шаблон

```
src/main/resources/templates/utils/my-util.html
```

Смотри любой существующий шаблон как образец, например `url-cleaner.html`.

---

## Чего НЕ делать

| Нельзя | Правильно |
|---|---|
| Писать `readCsvFile` в сервисе | `fileReaderUtils.readAllRows(metadata)` |
| Писать `readExcelFile` в сервисе | `fileReaderUtils.readFullFileData(metadata)` |
| Генерировать Excel через `XSSFWorkbook` напрямую | `FileGeneratorService.generateFile(...)` |
| Парсить `metadata.getSampleData()` / `metadata.getColumnHeaders()` через ObjectMapper | брать данные из `readAllRows` |
| Добавлять `ObjectMapper` только для `parseSampleData`/`parseHeaders` | не нужно — методы удалены как мёртвый код |
| Инжектировать `XSSFWorkbook`, `HSSFWorkbook`, `WorkbookFactory` напрямую | `FileReaderUtils` справится |

---

## Ключевые классы

| Класс | Путь | Назначение |
|---|---|---|
| `FileReaderUtils` | `com.java.util` | Чтение CSV/Excel — единственный источник |
| `FileGeneratorService` | `com.java.service.exports` | Генерация CSV/Excel по `ExportTemplate` |
| `FileMetadata` | `com.java.model.entity` | Метаданные загруженного файла (путь, формат, кодировка) |
| `FileMetadataRepository` | `com.java.repository` | Получение FileMetadata по ID |
| `ExportTemplate` / `ExportTemplateField` | `com.java.model.entity` | Описание структуры выходного файла |
