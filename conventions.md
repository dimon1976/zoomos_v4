# Code Conventions для утилиты сбора финальных ссылок

> Данные правила основаны на принципах разработки из [vision.md](./vision.md)

## Основные принципы

### KISS - Keep It Simple, Stupid
- Минимум классов - только необходимые компоненты
- Прямолинейная логика без избыточной абстракции
- Готовые решения вместо custom реализаций
- **Максимум ~950 строк кода общий объем**

### MVP подход
- Только базовая функциональность
- Простой UI с минимальными элементами
- Минимальная конфигурация с разумными умолчаниями
- Никаких сложных паттернов без острой необходимости

## Архитектурные принципы

- **Single Responsibility** - один класс = одна задача
- **Fail Fast** - быстрое выявление ошибок валидации
- **Progressive Enhancement** - curl → playwright → httpclient при неудаче
- **Defensive Programming** - валидация всех входных данных

## Обязательные компоненты

### Strategy Pattern для редиректов
```java
public interface RedirectStrategy {
    RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs);
    boolean canHandle(String url, PageStatus previousStatus);
    int getPriority(); // 1-curl, 2-playwright, 3-httpclient
}
```

### Приоритет стратегий антибот защиты
1. **CurlStrategy** - основной метод (максимальная совместимость)
2. **PlaywrightStrategy** - при блокировке curl (headless browser)
3. **HttpClientStrategy** - fallback для простых случаев

### PageStatus enum обязателен
```java
public enum PageStatus {
    OK, REDIRECT, BLOCKED, NOT_FOUND, ERROR
}
```

### Детекция блокировок ключевыми словами
```java
private static final Set<String> BLOCK_KEYWORDS = Set.of(
    "captcha", "recaptcha", "cloudflare", "access denied", 
    "доступ ограничен", "проверка безопасности", "bot detection"
);
```

## Интерфейс пользователя

### Обязательный показ прогресса
- **Прогресс-бар** с процентами обработки (предпочтительно)
- **Счетчик** обработанных записей из общего количества
- **Индикатор текущей операции** (обработка URL, переключение стратегии)
- **Примерное время** до завершения при возможности

Пример:
```
Обработка редиректов: 45% (90/200)
Текущий URL: https://example.com/...
Используется: CurlStrategy
Примерно: 2 мин 15 сек
```

### UI требования
- Загрузка файла через существующий FileAnalyzerService
- Автоанализ структуры колонок
- Простая форма выбора колонок (URL обязательно, ID/Model опционально)
- Скачивание результата через FileGeneratorService

## Логирование обязательно

### Уровни по важности
- **DEBUG**: команды curl, детали стратегий
- **INFO**: начало/конец обработки, успешные результаты, статистика
- **WARN**: блокировки антиботом, таймауты, превышение лимитов
- **ERROR**: критические ошибки выполнения

### Формат логов
```
INFO - === НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===
INFO - URL: original → final (редиректов: N, время: Xms, стратегия: Strategy)  
INFO - === РЕЗУЛЬТАТЫ: успешно: X, заблокировано: Y, ошибок: Z ===
```

## Структура данных

### RedirectResult модель
```java
@Data @Builder
public class RedirectResult {
    private String id, model;           // из исходного файла
    private String originalUrl, finalUrl;
    private Integer redirectCount;      // 0-N
    private PageStatus status;
    private String errorMessage;
    private Long processingTimeMs;
    private String strategy;           // какая стратегия сработала
}
```

### Минимальная конфигурация
```java
@Data
public class RedirectFinderDto {
    @NotNull private Integer urlColumn;
    private Integer idColumn = null;
    private Integer modelColumn = null; 
    private Integer maxRedirects = 5;
    private Integer timeoutMs = 10000;
    private Boolean usePlaywright = false;
    private String outputFormat = "csv";
}
```

## Технические требования

### Стек технологий
- Java 11+, Spring Boot, Thymeleaf, Bootstrap
- Lombok обязательно для упрощения кода
- Никаких дополнительных БД - только файловая обработка

### Curl как основа
```bash
curl -L -s -w "%{url_effective} %{num_redirects} %{http_code}" \
--connect-timeout 5 --max-time 10 --user-agent "Mozilla/5.0..." URL
```

### Playwright для сложных случаев
- Headless Chrome запуск только при BLOCKED статусе
- Эмуляция реального браузера с задержками
- Автоматический fallback при недоступности

## Тестирование

### Обязательные тесты на реальных данных
```java
@Test
public void testGoldAppleRedirect() {
    // https://goldapple.ru/qr/19000180719
    // Ожидается: статус REDIRECT на elixir-precious
}
```

### Цепочка стратегий тестируется
- Успешный curl результат  
- Блокировка → переключение на playwright
- Ошибки → fallback на httpclient

## НЕ делать

- ❌ Сложные паттерны без необходимости
- ❌ Внешние конфигурационные файлы для пользователей  
- ❌ Сохранение в БД (только файловая обработка)
- ❌ Избыточное логирование в DEBUG
- ❌ UI без показа прогресса обработки
- ❌ Превышение лимита ~950 строк кода

## Деплой

- Локально: только `mvn spring-boot:run`
- Production: + playwright установка при необходимости
- Системные требования: Java 11+, curl (есть везде), опционально chromium