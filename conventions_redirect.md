# Соглашения по разработке: Улучшение утилиты сбора редиректов

> **Базовый документ:** `vision_redirect.md` - техническое видение проекта

## Принципы кода

### 1. KISS и простота
- **Один класс = одна ответственность**
- **Минимум зависимостей** - используй только необходимые библиотеки
- **Простые методы** - максимум 30 строк, одна задача
- **Понятные имена** - `EnhancedHttpStrategy`, `processWithFallback()`

### 2. Переиспользование существующего
- **НЕ СОЗДАВАЙ** новых контроллеров - модифицируй `RedirectCollectorController`
- **НЕ ДУБЛИРУЙ** файловые операции - используй `FileAnalyzerService`, `FileGeneratorService`
- **НЕ ИЗОБРЕТАЙ** WebSocket - используй существующую систему уведомлений
- **РАСШИРЯЙ** существующие сервисы вместо создания новых

### 3. Fail Fast и измеримость  
- **Логируй ВСЕ стратегии** - успехи, блокировки, переключения
- **Быстро определяй блокировки** - timeout ≤ 10s для HTTP, ≤ 30s для браузеров
- **Сохраняй метрики** в БД для анализа эффективности
- **Валидация на входе** - проверяй URL, конфигурацию сразу

## Архитектурные правила

### 4. Strategy Pattern для стратегий
```java
// Базовый интерфейс
public interface AntiBlockStrategy {
    RedirectResult processUrl(String url, int maxRedirects, int timeout);
    String getStrategyName();
    boolean isAvailable();
}

// Реализации
SimpleHttpStrategy → EnhancedHttpStrategy → ProxyHttpStrategy → PlaywrightStrategy → SeleniumStrategy
```

### 5. Конфигурация
- **Spring @ConfigurationProperties** для настроек антиблокировки
- **YAML конфигурация** - простая, читаемая структура  
- **Профили** - dev/prod различия в timeouts и headless режимах
- **Валидация конфигурации** - @Valid на старте приложения

### 6. Обработка ошибок
- **403/429** → автоматический fallback на следующую стратегию
- **Timeout/Connection** → логирование + переход к браузерной стратегии  
- **Все стратегии не сработали** → возврат исходного URL + статус "UNAVAILABLE"
- **НЕ падай с exception** - graceful degradation всегда

## Технические требования

### 7. Зависимости и импорты
```java
// HTTP клиенты
import org.apache.hc.client5.http.impl.classic.HttpClients;
import okhttp3.OkHttpClient;
import java.net.HttpURLConnection; // fallback

// Браузеры  
import com.microsoft.playwright.Browser;
import org.openqa.selenium.WebDriver; // запасной

// Spring
import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.ConfigurationProperties;
```

### 8. Логирование и метрики
```java
// Структурированные логи
log.info("URL: {} | Strategy: {} | Status: {} | Time: {}ms | Final: {}", 
         originalUrl, strategyName, status, elapsedTime, finalUrl);
         
// Сохранение статистики
redirectStatisticsRepository.save(
    RedirectStatistics.builder()
        .url(url).strategy(strategyName)
        .success(success).elapsedTime(time)
        .build()
);
```

### 9. Тестирование
- **Unit тесты** для каждой стратегии отдельно
- **Integration тесты** для fallback цепочек  
- **Mock внешние сервисы** - прокси, браузеры
- **Тестовые URL** - httpbin.org, postman-echo.com

### 10. Производительность
- **Connection pooling** для HTTP клиентов
- **Повторное использование** браузерных сессий где возможно
- **Timeout управление** - жесткие лимиты на каждую стратегию
- **Асинхронная обработка** для больших списков URL

## Что НЕ делать

❌ **Не создавай** отдельный модуль - работай в существующей структуре  
❌ **Не дублируй** существующую логику файлов, WebSocket, шаблонов  
❌ **Не усложняй** конфигурацию - простые YAML настройки  
❌ **Не игнорируй** метрики - логируй и сохраняй все операции  
❌ **Не делай** синхронные долгие операции в контроллерах  

## Итеративная разработка

1. **Итерация 1:** Только HTTP улучшения - быстрый результат
2. **Итерация 2:** Strategy Pattern и fallback - надежность  
3. **Итерация 3:** Браузерные движки - обход сложных блокировок
4. **Итерация 4:** Прокси и аналитика - финальная оптимизация

**Каждая итерация должна работать независимо и приносить измеримое улучшение в обходе блокировок.**