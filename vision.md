# Техническое видение: Утилита сбора финальных ссылок после HTTP редиректов

## Технологии

### Основной стек
- **Java 11+** - основной язык разработки
- **Spring Boot** - фреймворк приложения 
- **Thymeleaf** - шаблонизатор для UI
- **Bootstrap** - UI фреймворк (соответствие существующим утилитам)
- **Lombok** - упрощение кода

### Стратегии обхода антиботных систем

#### 1. CurlStrategy (Приоритет 1)
```java
@Component
@Slf4j
public class CurlStrategy implements RedirectStrategy {
    
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        // curl -L -s -w "%{url_effective} %{num_redirects} %{http_code} %{content_type}" 
        // --connect-timeout 5 --max-time 10 --user-agent "Mozilla/5.0..." URL
    }
}
```

**Преимущества curl:**
- Максимальная совместимость с сайтами
- Нативная обработка редиректов
- Отличная поддержка HTTP/2, HTTP/3
- Обход большинства антиботных систем
- Проверенное решение (успешно работало ранее)

#### 2. PlaywrightStrategy (Приоритет 2) 
```java
@Component 
@Slf4j
public class PlaywrightStrategy implements RedirectStrategy {
    
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        // Запуск headless браузера для сложных случаев
        // Эмуляция полного браузерного поведения
        // Обход JavaScript редиректов и сложных антиботных систем
    }
}
```

**Когда использовать Playwright:**
- Curl получил статус BLOCKED
- Обнаружены ключевые слова блокировки
- JavaScript редиректы
- Сложные антиботные системы (CloudFlare, etc.)

#### 3. HttpClientStrategy (Резервный)
```java
@Component
@Slf4j  
public class HttpClientStrategy implements RedirectStrategy {
    
    public RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs) {
        // Java HttpClient как fallback для простых случаев
    }
}
```

### Детекция блокировок
```java
public enum PageStatus {
    OK,           // Успешная обработка
    REDIRECT,     // Найдены редиректы  
    BLOCKED,      // Антиботная система заблокировала
    NOT_FOUND,    // 404, 403 и подобные ошибки
    ERROR         // Технические ошибки (timeout, connection, etc.)
}

// Ключевые слова для детекции скрытых блокировок
private static final Set<String> BLOCK_KEYWORDS = Set.of(
    "captcha", "recaptcha", "cloudflare", "access denied", 
    "доступ ограничен", "доступ запрещен", "проверка безопасности",
    "security check", "bot detection", "too many requests",
    "rate limit", "temporarily unavailable"
);
```

## Принцип разработки

### KISS - Keep It Simple, Stupid
1. **Минимум классов** - только необходимые компоненты
2. **Простая логика** - без избыточной абстракции
3. **Прямолинейный алгоритм** - читаем файл → обрабатываем URL → записываем результат
4. **Готовые решения** - используем curl и существующие сервисы проекта

### MVP подход
1. **Базовая функциональность** - обработка HTTP редиректов через curl
2. **Простой UI** - загрузка файла, выбор колонок, скачивание результата  
3. **Минимальная конфигурация** - только критически важные параметры
4. **Без оверинжиниринга** - никаких сложных паттернов без острой необходимости

### Принципы архитектуры
- **Single Responsibility** - каждый класс решает одну задачу
- **Fail Fast** - быстрое выявление ошибок валидации
- **Progressive Enhancement** - сначала curl, при неудаче - playwright
- **Defensive Programming** - валидация всех входных данных

## Структура проекта

```
src/main/java/com/java/
├── controller/utils/
│   └── RedirectFinderController.java        // REST контроллер (~ 150 строк)
├── dto/utils/  
│   └── RedirectFinderDto.java              // Параметры конфигурации (~ 50 строк)
├── service/utils/
│   ├── RedirectFinderService.java          // Основная бизнес-логика (~ 200 строк)
│   └── redirect/
│       ├── RedirectStrategy.java           // Интерфейс стратегии (~ 20 строк)
│       ├── CurlStrategy.java              // Curl реализация (~ 100 строк)
│       ├── PlaywrightStrategy.java        // Playwright реализация (~ 150 строк)
│       └── HttpClientStrategy.java        // HttpClient fallback (~ 80 строк)
└── model/
    ├── RedirectResult.java                 // Результат обработки (~ 40 строк)
    └── PageStatus.java                     // Enum статусов (~ 10 строк)

src/main/resources/templates/utils/
├── redirect-finder.html                    // Главная страница (~ 100 строк)
└── redirect-finder-configure.html          // Настройки (~ 150 строк)

src/test/java/com/java/service/utils/
├── RedirectFinderServiceTest.java          // Тесты основного сервиса
└── redirect/
    ├── CurlStrategyTest.java              // Тесты curl стратегии
    └── PlaywrightStrategyTest.java        // Тесты playwright стратегии
```

**Итого: ~950 строк кода** - максимально компактное решение.

## Архитектура проекта

### Диаграмма компонентов
```
[User] → [RedirectFinderController] → [RedirectFinderService] → [Strategy Pattern]
                                                                      ↓
                                                   [CurlStrategy] → [PlaywrightStrategy]
                                                                      ↓
                                                              [RedirectResult + PageStatus]
```

### Паттерн Strategy для обработки редиректов
```java
public interface RedirectStrategy {
    RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs);
    boolean canHandle(String url, PageStatus previousStatus);
    int getPriority(); // 1 - curl, 2 - playwright, 3 - httpclient
}
```

### Цепочка обработки (Chain of Responsibility)
1. **CurlStrategy** - пробуем сначала (быстро, эффективно)
2. **Если BLOCKED** → **PlaywrightStrategy** - headless браузер
3. **Если ERROR** → **HttpClientStrategy** - простой fallback

### Компоненты и их роли

#### RedirectFinderController
- Загрузка файлов через существующий FileAnalyzerService
- Конфигурация параметров обработки
- Генерация результирующих файлов через FileGeneratorService
- Обработка ошибок и flash messages

#### RedirectFinderService  
- Чтение данных из загруженного файла
- Валидация выбранных колонок
- Координация работы стратегий
- Агрегация результатов

#### Strategy классы
- CurlStrategy: системные вызовы curl команд
- PlaywrightStrategy: запуск headless браузера для сложных случаев
- HttpClientStrategy: встроенный Java HTTP клиент

## Модель данных

### RedirectResult.java
```java
@Data
@Builder
public class RedirectResult {
    private String id;                    // ID из исходного файла
    private String model;                 // Модель из исходного файла
    private String originalUrl;           // Исходный URL
    private String finalUrl;             // Финальный URL после редиректов
    private Integer redirectCount;        // Количество редиректов (0-N)
    private PageStatus status;           // OK, REDIRECT, BLOCKED, NOT_FOUND, ERROR
    private String errorMessage;         // Детали ошибки
    private Long processingTimeMs;       // Время обработки
    private String strategy;             // Какая стратегия использовалась
}
```

### PageStatus.java
```java
public enum PageStatus {
    OK("Успешно"),
    REDIRECT("Редирект выполнен"), 
    BLOCKED("Заблокировано антиботом"),
    NOT_FOUND("Страница не найдена"),
    ERROR("Техническая ошибка");
    
    private final String description;
}
```

### RedirectFinderDto.java
```java
@Data
public class RedirectFinderDto {
    @NotNull(message = "Колонка с URL обязательна")
    private Integer urlColumn;
    
    private Integer idColumn;              // Опционально
    private Integer modelColumn;           // Опционально
    
    private Integer maxRedirects = 5;      // Ограничение редиректов
    private Integer timeoutMs = 10000;     // Таймаут на URL
    private Boolean usePlaywright = false; // Принудительно использовать browser
    
    private String outputFormat = "csv";   // Формат вывода
}
```

### Схема базы данных
**НЕ ТРЕБУЕТСЯ** - утилита работает только с файлами, без сохранения в БД.

## Сценарии работы

### Сценарий 1: Базовая обработка
1. **Загрузка файла** - пользователь выбирает CSV/Excel с URL
2. **Анализ структуры** - система определяет колонки автоматически
3. **Конфигурация** - выбор колонки URL (обязательно), ID и Model (опционально)
4. **Обработка через curl** - для каждого URL выполняется curl команда
5. **Генерация результата** - создание файла с финальными URL и статистикой

### Сценарий 2: Обход антиботных систем
1. **Первая попытка** - CurlStrategy отрабатывает URL
2. **Детекция блокировки** - обнаружены ключевые слова или статус 403/429
3. **Переключение на браузер** - PlaywrightStrategy запускает headless Chrome
4. **Эмуляция пользователя** - реальный браузер с задержками и headers
5. **Успешный обход** - получение финального URL

### Сценарий 3: Обработка ошибок
1. **Невалидный URL** - пропуск с статусом ERROR
2. **Таймаут соединения** - переход к следующей стратегии  
3. **Циклические редиректы** - обрыв на maxRedirects с предупреждением
4. **Недоступный домен** - сохранение с ERROR статусом

### Сценарий 4: Тестирование на реальных данных
```java
@Test
public void testGoldAppleRedirect() {
    // https://goldapple.ru/qr/19000180719 
    // Ожидаем: status 301 -> https://goldapple.ru/19000180719-elixir-precious
    RedirectResult result = curlStrategy.followRedirects(
        "https://goldapple.ru/qr/19000180719", 10, 5000
    );
    
    assertThat(result.getStatus()).isEqualTo(PageStatus.REDIRECT);
    assertThat(result.getFinalUrl()).contains("elixir-precious");
    assertThat(result.getRedirectCount()).isGreaterThan(0);
}

@Test 
public void testLentaRedirect() {
    // https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/
    // Ожидаем: status 301 -> https://lenta.com/product/vino-igristoe-bel-bryut-italiya-075l/
    RedirectResult result = curlStrategy.followRedirects(
        "https://lenta.com/product/vino-igristoe-bio-bio-bubbles-organic-bel-bryut-italiya-075l-521969/", 
        10, 5000
    );
    
    assertThat(result.getStatus()).isEqualTo(PageStatus.REDIRECT);
    assertThat(result.getFinalUrl()).contains("vino-igristoe-bel-bryut");
    assertThat(result.getRedirectCount()).isGreaterThan(0);
}
```

## Деплой

### Локальная разработка
```bash
# Никаких дополнительных зависимостей - curl уже есть в системе
mvn spring-boot:run

# Playwright установка (только при необходимости)  
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

### Production окружение
```xml
<!-- pom.xml - добавляем только playwright, если нужен -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.40.0</version>
</dependency>
```

### Docker (если потребуется)
```dockerfile
FROM openjdk:11-jre-slim

# Устанавливаем curl (обычно уже есть)
RUN apt-get update && apt-get install -y curl

# Playwright установка только при необходимости
RUN apt-get install -y chromium-browser

COPY target/app.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Системные требования
- **Java 11+** 
- **curl** (присутствует в 99% систем)
- **Chromium** (только если используется PlaywrightStrategy)
- **RAM**: +50MB для curl, +200MB для Playwright
- **CPU**: минимальные требования

## Подход к конфигурированию

### Принцип минимальной конфигурации
**Только критически важные настройки:**

```java
@Data
public class RedirectFinderDto {
    // ОБЯЗАТЕЛЬНЫЕ
    @NotNull
    private Integer urlColumn;             // Колонка с URL
    
    // ОПЦИОНАЛЬНЫЕ с разумными умолчаниями
    private Integer idColumn = null;       // ID колонка
    private Integer modelColumn = null;    // Модель колонка  
    private Integer maxRedirects = 5;      // Лимит редиректов
    private Integer timeoutMs = 10000;     // 10 сек на URL
    private Boolean usePlaywright = false; // Форсировать браузер
    private String outputFormat = "csv";   // Формат результата
}
```

### Настройки приложения
```yaml
# application.yml
redirect-finder:
  curl:
    user-agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    connect-timeout: 5
    max-time: 10
  playwright:
    headless: true
    timeout: 15000
    viewport:
      width: 1920
      height: 1080
  block-keywords:
    - "captcha"
    - "recaptcha"  
    - "cloudflare"
    - "access denied"
    - "доступ ограничен"
    - "проверка безопасности"
```

### Стратегия без внешних конфигов
- **Никаких property файлов** для пользователей
- **Встроенные умолчания** для всех параметров
- **Автоопределение** стратегий на основе результатов
- **Простая web-форма** для настройки колонок

## Подход к логгированию

### Уровни логирования

#### DEBUG - детальная диагностика
```java
log.debug("Обработка URL: {} стратегией: {}", url, strategy.getClass().getSimpleName());
log.debug("Curl команда: {}", curlCommand);
log.debug("Результат curl: status={}, redirects={}", status, redirectCount);
```

#### INFO - ключевые события
```java
log.info("=== НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===");
log.info("Файл: {}, строк для обработки: {}", filename, totalRows);
log.info("Параметры: maxRedirects={}, timeout={}ms", maxRedirects, timeoutMs);

log.info("URL: {} → {} (редиректов: {}, время: {}ms, стратегия: {})", 
    originalUrl, finalUrl, redirectCount, processingTime, strategy);

log.info("=== РЕЗУЛЬТАТЫ ОБРАБОТКИ ===");
log.info("Успешно: {}, заблокировано: {}, ошибок: {}, всего: {}", 
    successCount, blockedCount, errorCount, totalCount);
```

#### WARN - проблемные ситуации
```java
log.warn("URL заблокирован антиботом: {} (ключевые слова: {})", url, foundKeywords);
log.warn("Превышен лимит редиректов для URL: {} (лимит: {})", url, maxRedirects);
log.warn("Таймаут при обработке URL: {} ({}ms)", url, timeoutMs);
```

#### ERROR - критические ошибки
```java
log.error("Ошибка выполнения curl для URL: {}", url, exception);
log.error("Playwright недоступен для URL: {}", url, exception);
log.error("Неизвестная ошибка обработки файла", exception);
```

### Формат логов
```
2024-09-04 22:30:15.123 [http-nio-8080-exec-1] INFO  RedirectFinderService - === НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===
2024-09-04 22:30:15.124 [http-nio-8080-exec-1] INFO  RedirectFinderService - Файл: test_redirect.csv, строк для обработки: 2
2024-09-04 22:30:15.125 [http-nio-8080-exec-1] DEBUG CurlStrategy - Curl команда: curl -L -s -w "%{url_effective} %{num_redirects} %{http_code}" --connect-timeout 5 --max-time 10 "https://goldapple.ru/qr/19000180719"
2024-09-04 22:30:16.234 [http-nio-8080-exec-1] INFO  RedirectFinderService - URL: https://goldapple.ru/qr/19000180719 → https://goldapple.ru/19000180719-elixir-precious (редиректов: 1, время: 1109ms, стратегия: CurlStrategy)
2024-09-04 22:30:16.235 [http-nio-8080-exec-1] WARN  CurlStrategy - URL заблокирован антиботом: https://some-blocked-site.com (ключевые слова: [captcha])
2024-09-04 22:30:18.456 [http-nio-8080-exec-1] INFO  PlaywrightStrategy - Переключение на браузерную стратегию для URL: https://some-blocked-site.com
2024-09-04 22:30:20.789 [http-nio-8080-exec-1] INFO  RedirectFinderService - === РЕЗУЛЬТАТЫ ОБРАБОТКИ ===  
2024-09-04 22:30:20.790 [http-nio-8080-exec-1] INFO  RedirectFinderService - Успешно: 1, заблокировано: 0, ошибок: 0, всего: 1
```

### Мониторинг производительности
```java
@Component
@Slf4j
public class PerformanceLogger {
    
    public void logProcessingStats(List<RedirectResult> results) {
        Map<String, Long> strategyStats = results.stream()
            .collect(Collectors.groupingBy(
                RedirectResult::getStrategy, 
                Collectors.counting()
            ));
            
        log.info("Статистика по стратегиям: {}", strategyStats);
        
        OptionalDouble avgTime = results.stream()
            .mapToLong(RedirectResult::getProcessingTimeMs)
            .average();
            
        log.info("Среднее время обработки: {:.2f}ms", avgTime.orElse(0.0));
    }
}
```

---

## Итоговое решение

Данное техническое видение предлагает **максимально простое и эффективное решение** для обработки HTTP редиректов:

✅ **Минимум кода** (~950 строк) - только необходимое  
✅ **Проверенные технологии** - curl как основа + Playwright для сложных случаев  
✅ **Антиботная защита** - многоуровневая стратегия обхода блокировок  
✅ **Интеграция** - полное соответствие архитектуре проекта  
✅ **Тестируемость** - готовые тесты на реальных примерах  
✅ **Производственная готовность** - логирование, мониторинг, обработка ошибок

**Результат**: MVP утилита, готовая к немедленному использованию с возможностью развития по мере необходимости.