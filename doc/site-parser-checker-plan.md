# Проверка полноты выкачки — план разработки

## Обзор задачи

Автоматизация проверки статуса выкачки сайтов из кабинета zoomos.by. Программа берёт список сайтов клиента из БД (`zoomos_city_ids`), парсит страницы статистики выкачки, собирает данные, сравнивает с историей и выдаёт сводную таблицу.

### Два типа проверки (check_type)

| Тип | Значение | Страница | URL шаблон |
|-----|----------|----------|------------|
| **Полная выкачка из API** | `API` | parsing-history | `export.zoomos.by/shops-parser/{siteName}/parsing-history?dateFrom=...&dateTo=...&onlyFinished=1` |
| **Выкачка по карточкам** | `ITEM` | competitors-parsing-history | `export.zoomos.by/shop/{shopName}/competitors-parsing-history?dateFrom=...&dateTo=...` |

### Данные для сбора (на основе реальной таблицы со скриншота)

Столбцы таблицы на странице parsing-history:
| # | ID | Сервер | Клиент | Сайт | Город | Адрес | Аккаунт | Старт (общий) | Финиш | Кол-во | Кол-во категорий | Кол-во потоков | Кол-во ошибок | Завершено (всего) | Обновлено | Время | Парсер | Эмулятор |

**Ключевые поля для сбора:**

- Сервер, Сайт, Город
- Старт (общий), Финиш — **для сравнения времени между выкачками**
- Кол-во (товаров)
- В наличии (если есть в таблице кабинета клиента)
- Кол-во ошибок (критичный параметр)
- Завершено (всего) — процент, например "100% (58%)"
- Время — длительность выкачки ("27 мин", "112 мин")

---

## Этап 1 — Модель данных и конфигурация (V23 миграция)

### 1.1 Расширение `zoomos_city_ids` — тип проверки

```sql
ALTER TABLE zoomos_city_ids ADD COLUMN check_type VARCHAR(10) DEFAULT 'API';
-- Значения: 'API' (полная выкачка) или 'ITEM' (по карточкам из кабинета клиента)
```

**Почему на уровне `zoomos_city_ids`**: тип проверки — свойство конкретной пары shop+site. Настраивается один раз, используется при каждой проверке.

### 1.2 Новая таблица `zoomos_parsing_stats` — история выкачек

```sql
CREATE TABLE zoomos_parsing_stats (
    id BIGSERIAL PRIMARY KEY,
    check_run_id BIGINT REFERENCES zoomos_check_runs(id) ON DELETE CASCADE,
    city_id_ref BIGINT REFERENCES zoomos_city_ids(id) ON DELETE SET NULL,

    -- Идентификация выкачки
    parsing_id BIGINT,                -- ID из таблицы на странице (столбец "ID")
    site_name VARCHAR(255) NOT NULL,
    city_name VARCHAR(255),           -- "3612 - Нижний Новгород"
    server_name VARCHAR(100),         -- "appzoomos13"

    -- Временные метки парсинга (для сравнения между выкачками)
    start_time TIMESTAMP,             -- Старт (общий): "15.02.26 21:40"
    finish_time TIMESTAMP,            -- Финиш: "15.02.26 22:07"

    -- Метрики
    total_products INTEGER,           -- Кол-во: 21133
    in_stock INTEGER,                 -- В наличии: 431 (из кабинета клиента)
    category_count INTEGER,           -- Кол-во категорий
    error_count INTEGER DEFAULT 0,    -- Кол-во ошибок (критичный): 0
    completion_total VARCHAR(30),     -- Завершено (всего): "100% (58%)"
    completion_percent INTEGER,       -- Извлечённый процент: 100
    parsing_duration VARCHAR(50),     -- Время: "27 мин"
    parsing_duration_minutes INTEGER, -- Время в минутах для сравнения: 27

    -- Мета
    parsing_date DATE NOT NULL,       -- Дата выкачки (из start_time)
    check_type VARCHAR(10) NOT NULL,  -- API / ITEM
    checked_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_parsing_stats_run ON zoomos_parsing_stats(check_run_id);
CREATE INDEX idx_parsing_stats_site_date ON zoomos_parsing_stats(site_name, parsing_date);
CREATE INDEX idx_parsing_stats_city_ref ON zoomos_parsing_stats(city_id_ref);
```

### 1.3 Новая таблица `zoomos_check_runs` — журнал проверок

```sql
CREATE TABLE zoomos_check_runs (
    id BIGSERIAL PRIMARY KEY,
    shop_id BIGINT REFERENCES zoomos_shops(id) ON DELETE CASCADE,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    total_sites INTEGER DEFAULT 0,
    ok_count INTEGER DEFAULT 0,
    warning_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    not_found_count INTEGER DEFAULT 0,  -- сайты без данных за период
    status VARCHAR(20) DEFAULT 'RUNNING',  -- RUNNING, COMPLETED, FAILED
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);
```

### 1.4 Обновление entity `ZoomosCityId`

```java
@Column(name = "check_type")
@Builder.Default
private String checkType = "API";  // API или ITEM
```

### 1.5 Новые entity классы

- `ZoomosParsingStats` — запись об одной строке выкачки из таблицы
- `ZoomosCheckRun` — запись о запуске проверки

### 1.6 Enum `CheckType`

```java
public enum CheckType {
    API,   // Полная выкачка из API (shops-parser/{site}/parsing-history)
    ITEM   // По карточкам из кабинета клиента (shop/{client}/competitors-parsing-history)
}
```

---

## Этап 2 — Парсинг страниц выкачки (сервис)

### 2.1 `ZoomosCheckService` — основной сервис проверки

**Метод `runCheck(Long shopId, LocalDate dateFrom, LocalDate dateTo)`:**

1. Загрузить клиента (`ZoomosShop`) и все его активные `zoomos_city_ids`
2. Создать `ZoomosCheckRun` (status=RUNNING)
3. Открыть Playwright + загрузить куки
4. Группировать сайты по `checkType`:
   - **API**: для каждого `siteName` → парсить `shops-parser/{siteName}/parsing-history`
   - **ITEM**: один запрос `shop/{shopName}/competitors-parsing-history` → фильтровать строки по нужным `siteName`
5. Сохранить все строки в `zoomos_parsing_stats`
6. Обновить `ZoomosCheckRun` (подсчитать ok/warning/error)
7. Закрыть Playwright

### 2.2 Парсинг страницы полной выкачки (API)

URL: `export.zoomos.by/shops-parser/{siteName}/parsing-history?dateFrom={dd.MM.yyyy}&dateTo={dd.MM.yyyy}&onlyFinished=1`

Парсинг HTML таблицы — извлечение по столбцам:

```
# | ID | Сервер | Клиент | Сайт | Город | ... | Старт (общий) | Финиш | Кол-во | ... | Кол-во ошибок | Завершено (всего) | Обновлено | Время | Парсер | Эмулятор
```

**Особенности:**

- `&onlyFinished=1` — только завершённые выкачки
- Город содержит ID + название: "3612 - Нижний Новгород"
- Нужно фильтровать строки по ID городов из `zoomos_city_ids.cityIds`
- Одна страница может содержать выкачки по нескольким городам

### 2.3 Парсинг страницы кабинета клиента (ITEM)

URL: `export.zoomos.by/shop/{shopName}/competitors-parsing-history?dateFrom={dd.MM.yyyy}&dateTo={dd.MM.yyyy}`

- Один запрос на кабинет клиента, затем фильтрация строк по `siteName` из city_ids
- Таблица содержит дополнительные столбцы: "В наличии", "Кол-во ошибок"
- Без `&onlyFinished=1` (в кабинете этого параметра может не быть)

### 2.4 Переиспользование Playwright-инфраструктуры

- **Один** экземпляр Playwright + Browser на весь прогон проверки
- Reuse куки из `ZoomosParserService.loadSession()` / `saveSession()`
- ITEM-сайты: один запрос на страницу кабинета → парсим все нужные строки
- API-сайты: последовательные запросы по каждому уникальному siteName
- Асинхронное выполнение через `@Async("utilsTaskExecutor")`
- WebSocket прогресс: `/topic/zoomos-check/{operationId}`

---

## Этап 3 — Анализ и сравнение с историей

### 3.1 Сравнение по временным меткам

Основа сравнения — поля `start_time` и `finish_time`:

- Сравниваем текущую выкачку с **предыдущей** за ближайший прошлый период (предыдущий check_run)
- По одному сайту+городу может быть несколько выкачек за день (разные start_time)
- Сравниваем **последнюю** выкачку текущего периода с **последней** предыдущего

### 3.2 Алгоритм оценки

| Метрика | WARNING | CRITICAL |
|---------|---------|----------|
| Кол-во ошибок | > 0 ошибок | > 10 ошибок или рост > 50% |
| Кол-во товаров | падение > 10% | падение > 30% |
| Время выкачки | рост > 50% | рост > 100% |
| Завершённость | < 100% | < 90% |
| Нет данных | за текущий период нет выкачки | — |

Для ITEM-типа дополнительно:
| В наличии | падение > 15% | падение > 40% |

### 3.3 Компактное отображение нескольких выкачек за период

Если за неделю по одному сайту+городу было N успешных выкачек:

- Показываем **последнюю** выкачку (по `finish_time`) как основную строку
- Бейдж "×N" рядом с названием города (кликабельный)
- При клике — раскрывающийся блок со всеми выкачками за период
- В основной строке — данные последней выкачки + индикаторы изменений

---

## Этап 4 — UI (страница проверки)

### 4.1 Расположение

Новые страницы в рамках `/zoomos`:

- `GET /zoomos/check` — страница запуска проверки
- `GET /zoomos/check/results/{runId}` — результаты проверки

### 4.2 Страница запуска

- Выбор клиента (dropdown из `zoomos_shops`)
- Период: dateFrom — dateTo (по умолчанию: вчера — сегодня)
- **Список сайтов клиента** загружается автоматически при выборе клиента:
  - Каждый сайт с переключателем типа проверки (API / ITEM)
  - Тип сохраняется в БД (`zoomos_city_ids.check_type`) — настраивается один раз
  - Чекбокс активности (is_active) — неактивные сайты не проверяются
- Кнопка **"Запустить проверку"** → проверяет ВСЕ активные сайты клиента

### 4.3 Страница результатов — сводная таблица

Колонки:
| Статус | Сайт | Город | Тип | Товаров | В наличии | Ошибки | Завершено | Время | Старт→Финиш | ×N | Δ |

- **Статус**: иконка (OK / WARNING / CRITICAL / НЕТ ДАННЫХ)
- **Тип**: API или ITEM бейдж
- **Старт→Финиш**: временной диапазон последней выкачки
- **×N**: количество выкачек за период (кликабельный)
- **Δ**: мини-индикаторы изменений относительно предыдущего периода (↑↓=)
- Сортировка: CRITICAL → WARNING → НЕТ ДАННЫХ → OK
- Цветовая кодировка строк

### 4.4 WebSocket прогресс

- Прогресс-бар (обработано X из Y сайтов)
- Текущее действие ("Проверяем apteka-april.ru, город 3612...")
- `/topic/zoomos-check/{operationId}`

---

## Этап 5 — История и тренды

### 5.1 Страница истории

- `GET /zoomos/check/history` — список всех проверок (check_runs)
- Таблица: дата, клиент, период, OK/WARNING/CRITICAL, быстрый переход к результатам

### 5.2 Тренды по сайту

- При клике на сайт в результатах → мини-график метрик за последние N проверок
- Визуализация: товары, в наличии, ошибки, время

---

## Порядок реализации

| № | Этап | Что делаем | Приоритет |
|---|------|------------|-----------|
| 1 | Модель данных | V23 миграция + entity + enum + repositories | 🔴 Первый |
| 2 | Парсинг | ZoomosCheckService + парсинг двух типов страниц | 🔴 Второй |
| 3 | Анализ | Сравнение с историей + алгоритм оценки | 🟡 Третий |
| 4 | UI | Страница запуска + результаты + WebSocket | 🟡 Четвёртый |
| 5 | История | Журнал проверок + тренды | 🟢 Пятый |

---

## Технические решения

- **Playwright reuse**: одна сессия браузера на весь прогон
- **ITEM-оптимизация**: один запрос на кабинет клиента для всех ITEM-сайтов (не по одному)
- **API-оптимизация**: группировка — один запрос на `siteName`, парсим все города сразу
- **Async**: `@Async("utilsTaskExecutor")` для фоновой проверки
- **WebSocket**: прогресс через существующую инфраструктуру
- **check_type в БД**: настраивается один раз на странице `/zoomos`, используется постоянно
- **Flyway V23**: одна миграция для всех новых таблиц и ALTER
- **Фильтрация по city_ids**: строки из таблицы фильтруются по ID городов из `zoomos_city_ids.cityIds`
