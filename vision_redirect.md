# Техническое видение: Улучшение утилиты сбора редиректов

## Обзор проекта

Модернизация существующей утилиты `RedirectCollectorService` для обхода региональных блокировок и повышения надежности сбора финальных URL после редиректов.

**Цель:** Достичь 90%+ успешности прохождения региональных блокировок при сохранении производительности обработки больших списков URL.

---

## 1. Технологии

### Основной технологический стек
- **Java 17** + **Spring Boot 3.2.12** (база проекта)
- **Apache HttpClient 5.x** (основной HTTP клиент) - connection pooling, cookies, headers
- **OkHttp 4.x** (альтернативный HTTP клиент) - для диверсификации запросов  
- **HttpURLConnection** (fallback) - встроенный в Java

### Браузерные движки
- **Playwright** (приоритетный браузерный fallback) - быстрее, современнее
- **Selenium WebDriver** (запасной браузерный fallback) - если Playwright не справился

### Дополнительные возможности
- **Proxy поддержка** - HTTP/SOCKS proxy с ротацией IP адресов
- **User-Agent rotation** - готовая библиотека актуальных browser fingerprints  
- **Cookie management** - CookieStore для поддержания сессий
- **PostgreSQL** - сохранение статистики эффективности стратегий

### Переиспользование существующего функционала
- `FileAnalyzerService` - анализ загруженных файлов
- `FileGeneratorService` - генерация результирующих файлов
- `ExportTemplate` система - настройка формата вывода
- WebSocket уведомления для отображения прогресса

---

## 2. Принципы разработки

1. **KISS** - каждый компонент решает одну задачу просто
2. **Fail Fast** - быстро определяем неработающие стратегии  
3. **Итеративность** - добавляем стратегии постепенно, тестируем каждую
4. **Измеримость** - все действия логируем и считаем метрики
5. **Graceful Degradation** - если сложная стратегия не работает, возвращаемся к простой
6. **DRY & Reuse** - переиспользуем существующий функционал импорта/экспорта файлов, не дублируем

---

## 3. Структура проекта

**Минимальное расширение существующей структуры:**

```
src/main/java/com/java/
├── service/utils/
│   ├── RedirectCollectorService (существующий - МОДИФИЦИРУЕМ)
│   ├── BrowserService (существующий - РАСШИРЯЕМ)  
│   └── AntiBlockService (НОВЫЙ - стратегии обхода блокировок)
├── config/
│   └── AntiBlockConfig (НОВЫЙ - настройки прокси, user-agents)
├── model/entity/
│   └── RedirectStatistics (НОВЫЙ - статистика стратегий в БД)
└── controller/utils/
    └── RedirectCollectorController (существующий - используем как есть)
```

---

## 4. Архитектура проекта

### Компонентная архитектура

```
┌─────────────────────┐
│ RedirectCollector   │ ← существующий контроллер
│ Controller          │
└──────┬──────────────┘
       │
┌──────▼──────────────┐
│ RedirectCollector   │ ← МОДИФИЦИРУЕМ существующий
│ Service             │
└──────┬──────────────┘
       │
┌──────▼──────────────┐
│ AntiBlockService    │ ← НОВЫЙ - выбор стратегий
│ (Strategy Pattern)  │
└──────┬──────────────┘
       │
┌──────▼──────────────┐
│ HTTP Clients Pool   │ ← HttpClient + OkHttp + URLConnection  
│ + Proxy Pool        │ ← ротация прокси
│ + UserAgent Pool    │ ← ротация браузеров
│ + Browser Pool      │ ← Playwright + Selenium
└─────────────────────┘
```

### Стратегии обхода блокировок (Strategy Pattern)

1. **SimpleHttpStrategy** - базовые HTTP запросы (HttpURLConnection)
2. **EnhancedHttpStrategy** - улучшенные заголовки + cookies (Apache HttpClient)
3. **ProxyHttpStrategy** - через прокси + ротация IP (OkHttp + Proxy)
4. **PlaywrightBrowserStrategy** - современный браузерный движок (Chromium/Firefox)
5. **SeleniumBrowserStrategy** - классический браузерный fallback (Chrome/Firefox)

---

## 5. Сценарии работы

### Основной сценарий обработки
```
1. Пользователь загружает файл с URL → переиспользуем FileAnalyzerService
2. Система анализирует файл → переиспользуем существующую логику  
3. Для каждого URL:
   a) AntiBlockService выбирает стратегию
   b) Попытка доступа к URL
   c) При блокировке - следующая стратегия
   d) Сохранение результата + статистики в БД
4. Генерация отчета → переиспользуем FileGeneratorService
5. WebSocket уведомления о прогрессе → переиспользуем существующую систему
```

### Fallback-сценарий при блокировках
```
URL → SimpleHttp (1-2сек)
  ├─ SUCCESS → Результат
  ├─ 403/BLOCK → EnhancedHttp (2-3сек) 
  │   ├─ SUCCESS → Результат
  │   └─ 403/BLOCK → ProxyHttp (3-5сек)
  │       ├─ SUCCESS → Результат  
  │       └─ 403/BLOCK → Playwright (5-10сек)
  │           ├─ SUCCESS → Результат
  │           └─ FAIL → Selenium (10-20сек)
```

### Интеллектуальное переключение стратегий
- **Playwright** - приоритетный браузерный fallback (быстрее, лучше обход современных систем)
- **Selenium** - запасной браузерный fallback (если Playwright не справился)
- **Ротация браузеров** - Chromium/Firefox в Playwright и Chrome/Firefox в Selenium

---

## 6. Подход к конфигурированию

**Конфигурация через application-antiblock.yml:**

```yaml
antiblock:
  strategies:
    simple-http:
      enabled: true
      timeout: 10s
      max-retries: 2
    
    enhanced-http:
      enabled: true
      timeout: 15s
      user-agents: 
        - "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36"
        - "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/119.0"
        - "Mozilla/5.0 (X11; Linux x86_64) Chrome/120.0.0.0 Safari/537.36"
      
    proxy-http:
      enabled: false  # отключено по умолчанию
      proxies:
        - "http://proxy1:8080"
        - "socks5://proxy2:1080"
      rotation: true
      
    playwright:
      enabled: true
      browsers: [chromium, firefox]
      headless: true
      timeout: 30s
      
    selenium:
      enabled: true
      timeout: 60s
      headless: true

  statistics:
    save-to-db: true
    log-strategies: true
```

**Принципы конфигурации:**
- **Простота** - минимальные настройки для старта
- **Гибкость** - возможность отключить любую стратегию
- **Безопасность** - прокси по умолчанию отключены

---

## 7. Подход к логгированию

### Уровни логов
- **INFO** - успешные операции и переключения стратегий
- **WARN** - блокировки и fallback переключения  
- **ERROR** - критические ошибки и недоступность всех стратегий
- **DEBUG** - детальная информация для отладки

### Структура логов
```java
// Примеры логов для диагностики
log.info("URL: {} | Strategy: SimpleHttp | Status: SUCCESS | Time: {}ms", url, time);
log.warn("URL: {} | Strategy: SimpleHttp | Status: BLOCKED_403 | Fallback: EnhancedHttp", url);  
log.info("URL: {} | Strategy: EnhancedHttp | Status: SUCCESS | Final: {} | Redirects: {}", 
         url, finalUrl, redirectCount);
log.error("URL: {} | All strategies failed | Final status: UNAVAILABLE", url);
```

### Метрики в БД (RedirectStatistics)
- **Success rate** по каждой стратегии
- **Среднее время выполнения** для каждой стратегии
- **Топ заблокированных доменов** для анализа
- **Эффективность fallback цепочек** для оптимизации

---

## 8. Ожидаемые результаты

### Количественные метрики
- **90%+ успешность** прохождения региональных блокировок
- **Сохранение производительности** за счет умного переключения стратегий
- **Детальная диагностика** причин блокировок для постоянного улучшения
- **Масштабируемость** для обработки тысяч URL без блокировок

### Качественные улучшения
- **Надежность** - множественные fallback стратегии
- **Прозрачность** - детальное логирование всех операций
- **Адаптивность** - система учится на основе статистики успешности
- **Простота использования** - без изменений для пользователя

---

## 9. План итеративной разработки

### Итерация 1: HTTP улучшения (быстрые wins)
1. Улучшение заголовков в существующем `RedirectCollectorService`
2. Добавление `EnhancedHttpStrategy` с Apache HttpClient
3. User-Agent ротация и cookie management

### Итерация 2: Fallback логика
1. Создание `AntiBlockService` с Strategy Pattern
2. Интеграция всех HTTP стратегий
3. Автоматическое переключение при 403 ошибках

### Итерация 3: Браузерные стратегии  
1. Интеграция Playwright как приоритетного браузера
2. Улучшение существующего Selenium сервиса
3. Ротация браузерных движков

### Итерация 4: Прокси и аналитика
1. Добавление proxy поддержки с IP ротацией
2. Система сбора статистики в БД
3. Оптимизация на основе метрик эффективности

---

**Техническое видение готово к реализации. Каждая итерация приносит измеримое улучшение в обходе региональных блокировок с минимальными изменениями в архитектуре проекта.**