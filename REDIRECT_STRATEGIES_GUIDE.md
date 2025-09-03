# Гайд по новым стратегиям определения HTTP редиректов

## Обзор решения

Реализована надежная архитектура для определения HTTP редиректов с использованием Pattern Strategy и нескольких HTTP клиентов для обхода блокировок.

## Новые стратегии (в порядке приоритета)

### 1. OkHttpStrategy (приоритет: 0) - РЕКОМЕНДУЕМАЯ
- **HTTP клиент**: OkHttp 5.1.0
- **Особенности**: 
  - Полный контроль над редиректами
  - Поддержка HTTP/2 и HTTP/1.1
  - Расширенная настройка SSL
  - Cookie management
  - Обход большинства блокировок
- **Надежность**: ⭐⭐⭐⭐⭐

### 2. WebClientStrategy (приоритет: 1) - СОВРЕМЕННАЯ
- **HTTP клиент**: Spring WebClient (Reactive)
- **Особенности**:
  - Реактивный подход с таймаутами
  - Настройка Netty HttpClient
  - Поддержка HTTP/2
  - Неблокирующий I/O
- **Надежность**: ⭐⭐⭐⭐

### 3. SimpleHttpStrategy (приоритет: 1) - БАЗОВАЯ
- **HTTP клиент**: Java HttpURLConnection
- **Особенности**:
  - Исправлены enum статусы
  - Улучшена обработка ошибок
  - Простота и надежность
- **Надежность**: ⭐⭐⭐

### 4. EnhancedHttpStrategy (приоритет: 2) - ПРОДВИНУТАЯ
- **HTTP клиент**: Apache HttpClient 5
- **Особенности**:
  - Cookie store
  - Расширенные заголовки
  - HTTP/2 поддержка
- **Надежность**: ⭐⭐⭐⭐

## Ключевые улучшения

### ✅ Решена основная проблема
- **HttpURLConnection игнорировал setFollowRedirects(false)** → Теперь OkHttp и WebClient дают точный контроль
- **Неопределенные статусы** → Все статусы теперь используют PageStatus enum

### ✅ Архитектурные улучшения
- Strategy Pattern с автоматическим fallback
- Единообразная обработка ошибок через PageStatus enum
- Реалистичные User-Agent и HTTP заголовки
- Cookie support для сессий

### ✅ Тестирование
- Unit тесты с моками
- Интеграционные тесты с реальными URL
- Тесты производительности
- Тесты на goldapple.ru и других проблемных сайтах

## Использование

### Автоматический режим (рекомендуемый)
```java
@Autowired
private AntiBlockService antiBlockService;

// Автоматически пробует стратегии по приоритету
RedirectResult result = antiBlockService.processUrlWithFallback(
    "https://goldapple.ru/qr/19000180718", 
    5, // макс. редиректов
    30 // таймаут в секундах
);

PageStatus status = PageStatus.valueOf(result.getStatus());
```

### Ручной режим
```java
@Autowired 
private OkHttpStrategy okHttpStrategy;

if (okHttpStrategy.isAvailable()) {
    RedirectResult result = okHttpStrategy.processUrl(url, 5, 30);
}
```

## Статусы PageStatus

```java
public enum PageStatus {
    SUCCESS,        // Успешно получен финальный URL
    REDIRECT,       // Промежуточный редирект (внутренний)
    MAX_REDIRECTS,  // Достигнут лимит редиректов
    TIMEOUT,        // Таймаут соединения
    NOT_FOUND,      // Ошибка 404
    FORBIDDEN,      // Ошибки 401/403/429
    ERROR,          // Общая ошибка (5xx и др.)
    UNKNOWN_HOST,   // Неизвестный хост
    IO_ERROR,       // Ошибка ввода-вывода
    BROWSER_ERROR   // Ошибка браузерной стратегии
}
```

## Тестирование

### Запуск unit тестов
```bash
mvn test -Dtest=RedirectCollectorServiceTest
```

### Запуск интеграционных тестов
```bash
# Включить реальные HTTP запросы (осторожно - зависимость от сети!)
export ENABLE_INTEGRATION_TESTS=true
mvn test -Dtest=RedirectStrategiesIntegrationTest
```

### Тесты goldapple.ru
```bash
# Специальный тест для проблемного URL
mvn test -Dtest=RedirectStrategiesIntegrationTest#testGoldappleRedirect
```

## Настройки application.yml

```yaml
anti-block:
  log-strategies: true  # Детальное логирование стратегий
  user-agents:
    - "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    - "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
    - "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"

logging:
  level:
    com.java.service.utils: DEBUG  # Детальные логи стратегий
```

## Производительность

| Стратегия | Скорость | Надежность | Сложность |
|-----------|----------|------------|-----------|
| OkHttpStrategy | ⚡⚡⚡ | ⭐⭐⭐⭐⭐ | Средняя |
| WebClientStrategy | ⚡⚡⚡⚡ | ⭐⭐⭐⭐ | Высокая |
| SimpleHttpStrategy | ⚡⚡ | ⭐⭐⭐ | Низкая |
| EnhancedHttpStrategy | ⚡⚡⚡ | ⭐⭐⭐⭐ | Средняя |

## Решение проблемы с goldapple.ru

**Было**: HttpURLConnection возвращал HTTP 200 вместо 301 редиректа
**Стало**: OkHttpStrategy корректно определяет редиректы 301/302/303/307/308

```java
// Тест показывает правильную работу
String url = "https://goldapple.ru/qr/19000180718";
RedirectResult result = okHttpStrategy.processUrl(url, 5, 30);

// Ожидаемый результат:
// result.getStatus() = PageStatus.SUCCESS
// result.getFinalUrl() = "https://goldapple.ru/19000180718-elixir-intense" 
// result.getRedirectCount() = 1
```

## Мониторинг

Логи стратегий содержат:
- URL и используемую стратегию
- HTTP статус код
- Время выполнения
- Количество редиректов
- Финальный URL

Пример лога:
```
URL: https://goldapple.ru/qr/19000180718 | Strategy: OkHttp | Status: 301 | Time: 234ms | Protocol: HTTP_2
OkHttp redirect [1]: https://goldapple.ru/qr/19000180718 -> https://goldapple.ru/19000180718-elixir-intense
✅ OkHttp: SUCCESS | Time: 456ms | Final: https://goldapple.ru/19000180718-elixir-intense | Redirects: 1
```