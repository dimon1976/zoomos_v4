# Утилита сбора финальных ссылок после HTTP редиректов

## Обзор концепции

Утилита предназначена для обработки списка ссылок и получения их финальных URL после выполнения всех HTTP редиректов (статус 301, 302, 307, 308). Утилита должна интегрироваться в существующую архитектуру проекта в папке `/utils` и следовать установленным паттернам.

## Архитектура утилиты

### 1. Структура компонентов

```
src/main/java/com/java/
├── controller/utils/
│   └── RedirectFinderController.java       // Веб-контроллер для UI
├── dto/utils/
│   └── RedirectFinderDto.java             // DTO для передачи параметров
├── service/utils/
│   ├── RedirectFinderService.java         // Основной сервис
│   └── HttpRedirectStrategy.java          // Стратегия обработки редиректов
└── model/entity/
    └── RedirectResult.java                // Entity для результатов (опционально)

src/main/resources/templates/utils/
├── redirect-finder.html                   // Главная страница утилиты
└── redirect-finder-configure.html         // Страница настройки параметров
```

### 2. Паттерн архитектуры

Утилита следует MVC паттерну с трехуровневой архитектурой:
- **Controller** - обработка HTTP запросов и управление UI
- **Service** - бизнес-логика обработки редиректов
- **Strategy** - стратегии для различных методов получения редиректов

## Детальное описание компонентов

### RedirectFinderController.java

```java
@Controller
@RequestMapping("/utils/redirect-finder")
@RequiredArgsConstructor
@Slf4j
public class RedirectFinderController {
    
    private final FileAnalyzerService fileAnalyzerService;
    private final RedirectFinderService redirectFinderService;
    
    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Сбор финальных ссылок");
        return "utils/redirect-finder";
    }
    
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, HttpSession session, RedirectAttributes redirectAttributes);
    
    @GetMapping("/configure") 
    public String configure(Model model, HttpSession session);
    
    @PostMapping("/process")
    public ResponseEntity<Resource> processFile(@Valid @ModelAttribute RedirectFinderDto dto, BindingResult bindingResult, HttpSession session);
    
    @PostMapping("/cancel")
    public String cancel(HttpSession session);
}
```

### RedirectFinderDto.java

```java
@Data
public class RedirectFinderDto {
    // Основные колонки
    @NotNull(message = "Колонка с URL обязательна")
    private Integer urlColumn;
    
    private Integer idColumn;              // Опциональная колонка с ID
    private Integer modelColumn;           // Опциональная колонка с моделью
    
    // Настройки обработки
    private Integer maxRedirects = 10;     // Максимальное количество редиректов
    private Integer timeoutMs = 5000;      // Таймаут для каждого запроса
    private Boolean followJavaScript = false; // Обрабатывать JS редиректы
    
    // Настройки формата вывода  
    private String outputFormat = "csv";   // csv или excel
    private String csvDelimiter = ";";     // разделитель для CSV
    private String csvEncoding = "UTF-8";  // кодировка для CSV
}
```

### RedirectFinderService.java

```java
@Service
@RequiredArgsConstructor 
@Slf4j
public class RedirectFinderService {
    
    private final FileGeneratorService fileGeneratorService;
    private final HttpRedirectStrategy httpRedirectStrategy;
    
    public byte[] processRedirectFinding(FileMetadata metadata, RedirectFinderDto dto) throws IOException {
        // 1. Чтение данных из файла
        List<List<String>> data = readFullFileData(metadata);
        
        // 2. Валидация колонок
        validateColumns(data, dto);
        
        // 3. Обработка URL и получение финальных ссылок
        List<RedirectResult> results = processUrls(data, dto);
        
        // 4. Генерация результирующего файла
        return generateResultFile(results, dto);
    }
    
    private List<RedirectResult> processUrls(List<List<String>> data, RedirectFinderDto dto) {
        List<RedirectResult> results = new ArrayList<>();
        
        for (int i = 1; i < data.size(); i++) { // Пропускаем заголовок
            List<String> row = data.get(i);
            
            String originalUrl = getColumnValue(row, dto.getUrlColumn());
            String id = getOptionalColumnValue(row, dto.getIdColumn());
            String model = getOptionalColumnValue(row, dto.getModelColumn());
            
            if (originalUrl != null && !originalUrl.trim().isEmpty()) {
                RedirectResult result = httpRedirectStrategy.followRedirects(
                    originalUrl, id, model, dto.getMaxRedirects(), dto.getTimeoutMs()
                );
                results.add(result);
            }
        }
        
        return results;
    }
}
```

### HttpRedirectStrategy.java

```java
@Component
@Slf4j
public class HttpRedirectStrategy {
    
    public RedirectResult followRedirects(String originalUrl, String id, String model, 
                                        int maxRedirects, int timeoutMs) {
        
        RedirectResult.Builder builder = RedirectResult.builder()
            .originalUrl(originalUrl)
            .id(id)
            .model(model)
            .startTime(System.currentTimeMillis());
            
        try {
            String finalUrl = processRedirectChain(originalUrl, maxRedirects, timeoutMs);
            int redirectCount = countRedirects(originalUrl, finalUrl);
            
            return builder
                .finalUrl(finalUrl)
                .redirectCount(redirectCount)
                .status("SUCCESS")
                .endTime(System.currentTimeMillis())
                .build();
                
        } catch (Exception e) {
            log.error("Ошибка при обработке URL {}: {}", originalUrl, e.getMessage());
            return builder
                .finalUrl(originalUrl)
                .redirectCount(0)
                .status("ERROR")
                .errorMessage(e.getMessage())
                .endTime(System.currentTimeMillis())
                .build();
        }
    }
    
    private String processRedirectChain(String url, int maxRedirects, int timeoutMs) {
        // Реализация через curl команду (как было успешно реализовано ранее)
        // или через Java HTTP Client с ручным контролем редиректов
    }
}
```

### RedirectResult.java

```java
@Builder
@Data
public class RedirectResult {
    private String id;                    // ID записи (если указан)
    private String model;                 // Модель (если указана)
    private String originalUrl;           // Исходный URL
    private String finalUrl;             // Финальный URL после редиректов
    private Integer redirectCount;        // Количество редиректов
    private String status;               // SUCCESS, ERROR, TIMEOUT
    private String errorMessage;         // Сообщение об ошибке
    private Long processingTimeMs;       // Время обработки в мс
    private Long startTime;              // Время начала
    private Long endTime;                // Время окончания
    
    public Long getProcessingTimeMs() {
        if (startTime != null && endTime != null) {
            return endTime - startTime;
        }
        return null;
    }
}
```

## Алгоритм работы

### 1. Инициализация
1. Пользователь загружает файл (CSV/Excel) с ссылками
2. Система анализирует структуру файла и определяет колонки
3. Пользователь выбирает колонки: URL (обязательно), ID и модель (опционально)

### 2. Конфигурация
1. Выбор колонки с URL (обязательно)
2. Выбор дополнительных колонок: ID, модель
3. Настройка параметров обработки:
   - Максимальное количество редиректов (по умолчанию 10)
   - Таймаут запроса (по умолчанию 5000мс)
   - Обработка JavaScript редиректов (опционально)

### 3. Обработка
1. Чтение каждой строки файла
2. Извлечение URL и дополнительных полей
3. Выполнение HTTP запросов с отслеживанием редиректов:
   - Отправка HEAD/GET запроса
   - Анализ ответа на наличие редиректа (301, 302, 307, 308)
   - Следование по цепочке редиректов до финального URL
   - Сохранение количества редиректов и времени обработки

### 4. Генерация результата
1. Формирование списка результатов
2. Создание выходного файла в выбранном формате
3. Возврат файла пользователю

## Форматы входных и выходных данных

### Входной формат

**CSV/Excel файл** с минимальной структурой:
```
URL,ID,Модель
https://example.com/redirect1,12345,Model-A
https://another.com/page,67890,Model-B
```

**Поддерживаемые колонки:**
- URL (обязательно) - ссылка для обработки
- ID (опционально) - идентификатор записи
- Модель (опционально) - связанная модель/категория

### Выходной формат

**CSV/Excel файл** с расширенной информацией:
```
ID,Модель,Исходный URL,Финальный URL,Количество редиректов,Статус,Время обработки (мс),Сообщение об ошибке
12345,Model-A,https://example.com/redirect1,https://final-domain.com/page,3,SUCCESS,1247,
67890,Model-B,https://another.com/page,https://another.com/page,0,SUCCESS,234,
```

**Колонки результата:**
- ID - идентификатор (если был в исходном файле)
- Модель - модель (если была в исходном файле)  
- Исходный URL - оригинальная ссылка
- Финальный URL - ссылка после всех редиректов
- Количество редиректов - число промежуточных переходов
- Статус - SUCCESS/ERROR/TIMEOUT
- Время обработки (мс) - время выполнения запроса
- Сообщение об ошибке - детали при ошибке

## Технические детали реализации

### 1. HTTP стратегия

```java
// Использование встроенного Java HTTP Client с ручным контролем редиректов
HttpClient client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NEVER) // Отключаем автоследование
    .connectTimeout(Duration.ofMillis(timeoutMs))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .method("HEAD", HttpRequest.BodyPublishers.noBody())
    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .timeout(Duration.ofMillis(timeoutMs))
    .build();
```

### 2. Альтернативная CURL стратегия 

```java
// Использование системной команды curl (как было успешно реализовано)
String command = String.format(
    "curl -L -s -o /dev/null -w \"%%{url_effective} %%{num_redirects} %%{http_code}\" \"%s\"",
    url
);
```

### 3. Обработка ошибок

- **Таймауты**: установка максимального времени ожидания
- **Недоступные домены**: обработка DNS и connection ошибок  
- **Циклические редиректы**: ограничение максимального числа переходов
- **Некорректные URL**: валидация и нормализация адресов

### 4. Производительность

- **Параллельная обработка**: использование CompletableFuture для одновременной обработки нескольких URL
- **Пулы подключений**: переиспользование HTTP соединений
- **Кеширование**: сохранение результатов для одинаковых URL

### 5. Логирование и мониторинг

```java
@Slf4j
public class RedirectFinderService {
    
    public byte[] processRedirectFinding(FileMetadata metadata, RedirectFinderDto dto) {
        log.info("=== НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===");
        log.info("Файл: {}", metadata.getOriginalFilename());
        log.info("Параметры: maxRedirects={}, timeout={}ms", 
                dto.getMaxRedirects(), dto.getTimeoutMs());
        
        // Обработка...
        
        log.info("Обработано URL: {}, успешно: {}, ошибок: {}", 
                totalUrls, successCount, errorCount);
        log.info("=== ОКОНЧАНИЕ ОБРАБОТКИ РЕДИРЕКТОВ ===");
    }
}
```

## Интеграция с существующей системой

### 1. Регистрация в UtilsController

```java
@GetMapping
public String index(Model model) {
    // Добавление утилиты в список
    utilities.add(UtilityInfo.builder()
        .name("redirect-finder")
        .title("Сбор финальных ссылок")
        .description("Обработка HTTP редиректов и получение финальных URL")
        .icon("fas fa-external-link-alt")
        .status("ready")
        .url("/utils/redirect-finder")
        .build());
}
```

### 2. HTML шаблоны

Следование существующим паттернам:
- Использование Thymeleaf layout
- Bootstrap для стилизации  
- Единообразное поведение форм
- Обработка ошибок через flash attributes

### 3. Валидация и безопасность

- Bean Validation для DTO
- Проверка размера файлов
- Валидация URL перед обработкой
- Защита от SSRF атак

## Преимущества архитектурного решения

1. **Модульность**: четкое разделение ответственности между компонентами
2. **Расширяемость**: возможность добавления новых стратегий обработки
3. **Совместимость**: следование установленным в проекте паттернам
4. **Производительность**: возможность параллельной обработки
5. **Надежность**: обработка ошибок и edge cases
6. **Наблюдаемость**: детальное логирование процесса

## Перспективы развития

1. **JavaScript редиректы**: интеграция с headless браузером
2. **Аналитика**: сохранение статистики обработки
3. **Планировщик**: автоматическая периодическая обработка
4. **API интеграция**: REST endpoints для внешних систем
5. **Кеширование результатов**: Redis для оптимизации повторных запросов

Данная концепция обеспечивает создание надежной, производительной и хорошо интегрированной утилиты для обработки HTTP редиректов в соответствии с архитектурными принципами проекта.