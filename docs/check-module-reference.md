# Zoomos Check — Технический справочник модуля

Версия: по состоянию на ветку `feature/zoomos-v2`, апрель 2026.
Назначение: перенос логики анализа выкачки в новый проект.

---

## 1. Схема базы данных

### Таблицы

#### `zoomos_shops` — магазины (клиенты Zoomos)

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `shop_name` | VARCHAR(255) NOT NULL UNIQUE | Имя магазина на export.zoomos.by |
| `is_enabled` | BOOLEAN NOT NULL DEFAULT TRUE | Видимость в UI |
| `is_priority` | BOOLEAN NOT NULL DEFAULT FALSE | Приоритетный клиент |
| `client_id` | BIGINT FK → clients(id) | Привязка к клиенту (опционально) |
| `last_synced_at` | TIMESTAMPTZ | Дата последней синхронизации настроек |
| `created_at` | TIMESTAMPTZ DEFAULT NOW() | |

---

#### `zoomos_sites` — глобальный справочник сайтов-конкурентов

Entity: `ZoomosKnownSite`, таблица: **`zoomos_sites`** (не `zoomos_known_sites`!)

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) NOT NULL UNIQUE | Имя сайта (напр. `wildberries.ru`) |
| `check_type` | VARCHAR(10) NOT NULL DEFAULT 'ITEM' | `API` или `ITEM` |
| `description` | VARCHAR(500) | Описание |
| `is_priority` | BOOLEAN NOT NULL DEFAULT FALSE | Приоритетный сайт |
| `ignore_stock` | BOOLEAN NOT NULL DEFAULT FALSE | Не проверять inStock (только наличие товаров) |
| `master_city_id` | VARCHAR(50) | Мастер-город: только по нему считать метрики. Авторитетный источник (перенесён из city_ids в V53) |
| `item_price_configured` | BOOLEAN | null=не проверялось, true/false = настроен ли ITEM_PRICE |
| `cities_equal_prices` | BOOLEAN | Одинаковые ли цены во всех городах |
| `cities_equal_prices_checked_at` | TIMESTAMPTZ | Когда проверялось |
| `created_at` | TIMESTAMPTZ DEFAULT NOW() | |

---

#### `zoomos_city_ids` — конфигурация сайтов для каждого магазина

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT NOT NULL FK → zoomos_shops(id) CASCADE | |
| `site_name` | VARCHAR(255) NOT NULL | |
| `city_ids` | TEXT | Список ожидаемых city_id через запятую (напр. `3612,4400`) |
| `address_ids` | TEXT | JSON-маппинг `{"cityId":["addressId1","addressId2"]}` или плоский список `"18121,18122"` |
| `master_city_id` | VARCHAR(50) | DEPRECATED с V53; используйте `zoomos_sites.master_city_id` |
| `check_type` | VARCHAR(10) DEFAULT 'ITEM' | `API` или `ITEM` |
| `is_active` | BOOLEAN DEFAULT TRUE | |
| `parser_include` | TEXT | Подстроки фильтра включения парсера (разделитель `;`) |
| `parser_include_mode` | VARCHAR(3) DEFAULT 'OR' | `OR` (любая из подстрок) / `AND` (все должны совпасть) |
| `parser_exclude` | TEXT | Подстроки исключения (всегда OR-логика) |
| `has_config_issue` | BOOLEAN NOT NULL DEFAULT FALSE | Флаг конфигурационной проблемы |
| `config_issue_type` | VARCHAR(30) | Тип проблемы (ConfigIssueType) |
| `config_issue_note` | TEXT | Заметка о проблеме |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |
| UNIQUE | `(shop_id, site_name)` | |

---

#### `zoomos_check_runs` — журнал запусков проверок

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT NOT NULL FK → zoomos_shops(id) CASCADE | |
| `date_from` | DATE NOT NULL | Начало проверяемого периода |
| `date_to` | DATE NOT NULL | Конец проверяемого периода |
| `time_from` | VARCHAR(5) | Время начала окна `HH:mm` (опционально) |
| `time_to` | VARCHAR(5) | Время конца окна `HH:mm` (опционально) |
| `total_sites` | INTEGER DEFAULT 0 | Всего сайтов в проверке |
| `ok_count` | INTEGER DEFAULT 0 | Сайтов со статусом OK |
| `warning_count` | INTEGER DEFAULT 0 | Сайтов со статусом WARNING/TREND |
| `error_count` | INTEGER DEFAULT 0 | Сайтов со статусом CRITICAL |
| `not_found_count` | INTEGER DEFAULT 0 | Из них с причиной NOT_FOUND |
| `drop_threshold` | INTEGER DEFAULT 10 | Порог падения inStock в % |
| `error_growth_threshold` | INTEGER DEFAULT 30 | Порог роста ошибок парсинга в % |
| `baseline_days` | INTEGER DEFAULT 7 | Горизонт baseline-анализа (дней назад) |
| `min_absolute_errors` | INTEGER DEFAULT 5 | Мин. абсолютное кол-во ошибок для WARNING |
| `trend_drop_threshold` | INTEGER DEFAULT 30 | Порог тренда падения/замедления в % |
| `trend_error_threshold` | INTEGER DEFAULT 100 | Порог тренда роста ошибок в % |
| `status` | VARCHAR(20) DEFAULT 'RUNNING' | `RUNNING`, `COMPLETED`, `FAILED` |
| `error_message` | VARCHAR(1000) | Сообщение об ошибке (таймаут и т.п.) |
| `timeout_count` | INTEGER DEFAULT 0 | Кол-во таймаутов в ходе проверки |
| `started_at` | TIMESTAMPTZ DEFAULT NOW() | |
| `completed_at` | TIMESTAMPTZ | |

---

#### `zoomos_parsing_stats` — строки истории выкачки

Каждая запись = одна строка из таблицы на странице `/shops-parser/{site}/parsing-history`.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `check_run_id` | BIGINT NOT NULL FK → zoomos_check_runs(id) CASCADE | |
| `city_id_ref` | BIGINT FK → zoomos_city_ids(id) SET NULL | |
| `parsing_id` | BIGINT | ID выкачки на сервере Zoomos |
| `site_name` | VARCHAR(255) NOT NULL | |
| `city_name` | VARCHAR(255) | Строка вида `"3612 - Нижний Новгород"` |
| `server_name` | VARCHAR(100) | Сервер выкачки |
| `client_name` | VARCHAR(255) | Клиент (пусто для API-выкачки) |
| `address_id` | VARCHAR(20) | Числовой ID адреса (из `"[14342] Братск..."`) |
| `address_name` | TEXT | Полная строка адреса |
| `start_time` | TIMESTAMPTZ | Время старта выкачки |
| `finish_time` | TIMESTAMPTZ | Время завершения |
| `updated_time` | TIMESTAMPTZ | Время последнего обновления (для STALLED) |
| `total_products` | INTEGER | Количество товаров |
| `in_stock` | INTEGER | Количество в наличии |
| `category_count` | INTEGER | Количество категорий |
| `error_count` | INTEGER DEFAULT 0 | Количество ошибок парсинга |
| `completion_total` | VARCHAR(30) | Строка завершённости `"100% (58%)"` |
| `completion_percent` | INTEGER | Числовой процент завершённости |
| `parsing_duration` | VARCHAR(50) | Строка длительности `"27 мин"`, `"1 ч 23 мин"` |
| `parsing_duration_minutes` | INTEGER | Длительность в минутах |
| `parser_description` | TEXT | Текст колонки «Парсер» |
| `account_name` | VARCHAR(255) | Аккаунт выкачки (очищен от `[ID]`) |
| `is_finished` | BOOLEAN DEFAULT TRUE | Завершена ли выкачка |
| `is_baseline` | BOOLEAN NOT NULL DEFAULT FALSE | Запись baseline-периода |
| `parsing_date` | DATE NOT NULL | Дата старта выкачки |
| `check_type` | VARCHAR(10) NOT NULL | `API` или `ITEM` |
| `checked_at` | TIMESTAMPTZ DEFAULT NOW() | |

---

#### `zoomos_city_names` — справочник ID→название города

| Колонка | Тип | Описание |
|---|---|---|
| `city_id` | VARCHAR(50) PK | Числовой ID города Zoomos |
| `city_name` | VARCHAR(255) NOT NULL | Название города |
| `updated_at` | TIMESTAMP NOT NULL DEFAULT NOW() | |

Заполняется автоматически при парсинге из строки формата `"3612 - Нижний Новгород"`.

---

#### `zoomos_city_addresses` — справочник адресов по городам

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `city_id` | VARCHAR(50) NOT NULL | ID города |
| `address_id` | VARCHAR(50) NOT NULL | Числовой ID адреса |
| `address_name` | VARCHAR(500) | Полная строка адреса |
| `updated_at` | TIMESTAMP NOT NULL DEFAULT NOW() | |
| UNIQUE | `(city_id, address_id)` | |

---

#### `zoomos_sessions` — сессионные куки

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `cookies` | TEXT NOT NULL | JSON-сериализованный список куки Playwright |
| `created_at` | TIMESTAMPTZ DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ DEFAULT NOW() | |

Хранится одна запись. Используется для повторной авторизации без логина.

---

#### `zoomos_shop_schedules` — расписания автоматических проверок

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT NOT NULL FK → zoomos_shops(id) CASCADE | Может быть несколько расписаний на магазин |
| `label` | VARCHAR(50) | Метка расписания |
| `cron_expression` | VARCHAR(100) NOT NULL DEFAULT '0 0 8 * * *' | Spring cron (6 частей: сек мин час д/м м д/н) |
| `is_enabled` | BOOLEAN NOT NULL DEFAULT FALSE | |
| `time_from` | VARCHAR(5) | Фильтр: с какого времени брать выкачки |
| `time_to` | VARCHAR(5) | Фильтр: до какого времени |
| `drop_threshold` | INTEGER NOT NULL DEFAULT 10 | |
| `error_growth_threshold` | INTEGER NOT NULL DEFAULT 30 | |
| `baseline_days` | INTEGER NOT NULL DEFAULT 7 | |
| `min_absolute_errors` | INTEGER NOT NULL DEFAULT 5 | |
| `trend_drop_threshold` | INTEGER NOT NULL DEFAULT 30 | |
| `trend_error_threshold` | INTEGER NOT NULL DEFAULT 100 | |
| `date_offset_from` | INTEGER NOT NULL DEFAULT -1 | Смещение от сегодня: -1 = вчера |
| `date_offset_to` | INTEGER NOT NULL DEFAULT 0 | Смещение: 0 = сегодня |
| `last_run_at` | TIMESTAMPTZ | Время последнего запуска |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

#### `zoomos_parser_patterns` — справочник паттернов парсера

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) NOT NULL | |
| `pattern` | TEXT NOT NULL | Значение колонки «Парсер» |
| UNIQUE | `(site_name, pattern)` | |

Заполняется автоматически при парсинге. Используется в UI для автодополнения фильтра.

---

#### `zoomos_redmine_issues` — локальные задачи Redmine

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) NOT NULL UNIQUE | |
| `issue_id` | INTEGER NOT NULL | ID задачи в Redmine |
| `issue_status` | VARCHAR(100) | Статус задачи |
| `issue_url` | VARCHAR(500) | URL задачи |
| `is_closed` | BOOLEAN DEFAULT FALSE | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

#### `zoomos_settings` — глобальные настройки (key-value)

| Колонка | Тип | Описание |
|---|---|---|
| `key` | VARCHAR(100) PK | Ключ настройки |
| `value` | VARCHAR(255) NOT NULL | Значение |
| `description` | VARCHAR(500) | Описание |

---

#### `zoomos_check_profiles` — профили проверки

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT NOT NULL FK → zoomos_shops(id) CASCADE | |
| `label` | VARCHAR(100) | |
| `days_of_week` | VARCHAR(20) | Дни: `"1,2,3"` (1=пн, 7=вс), пусто = каждый день |
| `time_from` | VARCHAR(5) | Начало окна выкачки |
| `time_to` | VARCHAR(5) | Дедлайн |
| `cron_expression` | VARCHAR(255) | Для автозапуска |
| `drop_threshold` | INTEGER DEFAULT 10 | |
| `error_growth_threshold` | INTEGER DEFAULT 30 | |
| `baseline_days` | INTEGER DEFAULT 7 | |
| `min_absolute_errors` | INTEGER DEFAULT 5 | |
| `trend_drop_threshold` | INTEGER DEFAULT 30 | |
| `trend_error_threshold` | INTEGER DEFAULT 100 | |
| `stall_minutes` | INTEGER DEFAULT 60 | Порог зависания в минутах |
| `is_enabled` | BOOLEAN NOT NULL DEFAULT FALSE | |

---

#### `zoomos_profile_sites` — сайты внутри профиля

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `profile_id` | BIGINT NOT NULL FK → zoomos_check_profiles(id) CASCADE | |
| `site_name` | VARCHAR(255) NOT NULL | |
| `city_ids` | TEXT | Переопределяют настройки магазина, если заполнены |
| `account_filter` | VARCHAR(255) | Фильтр по аккаунту (пусто = любой) |
| `parser_include` | TEXT | |
| `parser_include_mode` | VARCHAR(3) DEFAULT 'OR' | |
| `parser_exclude` | TEXT | |
| `is_active` | BOOLEAN NOT NULL DEFAULT TRUE | |
| UNIQUE | `(profile_id, site_name)` | |

---

### Связи между таблицами

```
zoomos_shops ──< zoomos_city_ids        (shop_id, CASCADE)
zoomos_shops ──< zoomos_check_runs      (shop_id, CASCADE)
zoomos_shops ──< zoomos_shop_schedules  (shop_id, CASCADE)
zoomos_shops ──< zoomos_check_profiles  (shop_id, CASCADE)

zoomos_check_runs ──< zoomos_parsing_stats   (check_run_id, CASCADE)
zoomos_city_ids   ──< zoomos_parsing_stats   (city_id_ref, SET NULL)

zoomos_check_profiles ──< zoomos_profile_sites  (profile_id, CASCADE)

zoomos_city_names    — независимая (city_id VARCHAR PK)
zoomos_city_addresses — независимая (cityId + addressId UNIQUE)
zoomos_parser_patterns — независимая (site_name + pattern UNIQUE)
zoomos_redmine_issues  — независимая (site_name UNIQUE)
zoomos_settings        — независимая (key PK)
zoomos_sessions        — независимая (одна запись)
```

---

## 2. Алгоритм анализа

### Общий алгоритм (точка входа: `ZoomosAnalysisService.analyze`)

**Входные параметры:** `checkRunId`, `profileId` (опционально), `deadline` (ZonedDateTime, опционально), `stallMinutes` (порог зависания в минутах).

```
1. Загрузить ZoomosCheckRun из БД (с привязанным shop)
2. Загрузить все zoomos_parsing_stats для этого run:
   - основные записи: is_baseline=false, отсортированы по site_name, city_name
   - baseline-записи: is_baseline=true, отсортированы по start_time DESC

3. Прочитать пороги из run:
   - dropThreshold        (default: 10%)
   - errorGrowthThreshold (default: 30%)
   - trendDropThreshold   (default: 30%)
   - minAbsoluteErrors    (default: 5)

4. Загрузить справочники:
   - cityNamesMap   (cityId → cityName)
   - knownSiteMap   (siteName → ZoomosKnownSite)
   - cityIdMap      (siteName → ZoomosCityId, для данного shop)

5. Построить конфигурацию сайтов (SiteConfig):
   - если profileId задан → buildProfileConfigs (из zoomos_profile_sites)
   - иначе → buildLegacyConfigs (из zoomos_city_ids для данного shop)

6. Для каждого сайта из конфигурации → buildSiteResult(...)
7. Для сайтов без конфигурации, но с данными → buildSiteResult(...) с дефолтной конфигурацией
8. Отсортировать результаты по status.priority (CRITICAL=0 первый)
9. Вернуть List<ZoomosSiteResult>
```

---

### Алгоритм анализа одного сайта (`buildSiteResult`)

```
Шаг A: Per-city анализ
─────────────────────
IF expectedCities пуст (нет настроенных city_ids):
  - Применить фильтр парсера к siteStats
  - Если фильтр не дал результатов, но данные есть → добавить CATEGORY_MISSING issue
  - Один cityResult для всех данных (без привязки к cityId)
ELSE:
  - Для каждого ожидаемого cityId:
    * Найти stats по cityId → применить фильтр парсера
    * Добавить baseline-записи за период проверки
    * analyzeCityGroup(cityId, ...)
  - Посчитать сколько городов NOT_FOUND → если > 0, добавить CITIES_MISSING

Шаг B: Проверка аккаунта
──────────────────────────
IF config.accountFilter задан:
  - Проверить, есть ли хоть одна запись с matching accountName
  - Если нет → добавить ACCOUNT_MISSING issue

Шаг C: Агрегированные метрики и тренды (вычисляются по мастер-городу, или по всем)
────────────────────────────────────────────────────────────────────────────────────
- Найти latestStat (последняя завершённая запись по start_time)
- Вычислить baseline-медианы:
  * baselineInStock (медиана inStock по лучшим записям из baseline)
  * baselineErrorRate (медиана errorCount/totalProducts)
  * baselineSpeed (медиана parsingDurationMinutes/totalProducts*1000)

- Проверка ERROR_GROWTH:
  * currentRate = latestStat.errorCount / latestStat.totalProducts
  * Если currentRate > baselineErrorRate * (1 + errorGrowthThreshold/100)
    И latestStat.errorCount >= minAbsoluteErrors → добавить ERROR_GROWTH

- Проверка STOCK_TREND_DOWN (только если !ignoreStock):
  * Собрать inStock по дням (из baseline + текущего)
  * Если ≥ 3 дней подряд inStock снижается И суммарное падение ≥ trendDropThreshold/2 %
    → добавить STOCK_TREND_DOWN

- Проверка SPEED_TREND:
  * Вычислить медиану скорости по дням из baseline
  * Если последние 3 дня подряд скорость растёт → добавить SPEED_TREND (взаимоисключает SPEED_SPIKE)

- Проверка SPEED_SPIKE (только если SPEED_TREND не найден):
  * curSpeed = медиана скорости завершённых записей за текущий день
  * Если curSpeed > baselineSpeed * (1 + trendDropThreshold/100) → добавить SPEED_SPIKE

Шаг D: Поднятие CRITICAL/WARNING с городов на уровень сайта
────────────────────────────────────────────────────────────
- Для каждого cityResult, для каждого его issue:
  * Если уровень CRITICAL или WARNING → добавить к siteIssues (без дублей)

Шаг E: Итоговый статус
───────────────────────
- worstCityStatus = минимальный (по priority) статус среди всех cityResults
- siteStatus = минимальный статус из siteIssues (или worstCityStatus если он хуже)

Шаг F: Формирование ZoomosSiteResult
──────────────────────────────────────
- Если только 1 город → вынести cityId/cityName на уровень сайта
- isStalled = true если хотя бы один город имеет isStalled=true
- Вычислить sparkline-истории: inStockHistory, errorHistory, speedHistory (последние 7 точек)
```

---

### Логика baseline

**Что такое baseline:** исторические данные для сравнения с текущей выкачкой.

**Как собирается:**
- При запуске проверки задаётся параметр `baselineDays` (по умолчанию 7).
- Если `baselineDays > 0`, парсинг выполняется за расширенный диапазон: от `dateFrom - baselineDays` до `dateTo`.
- После парсинга записи разделяются по дате старта: записи с `startTime < dateFrom` помечаются `is_baseline=true` и сохраняются отдельно.

**Как используется:**
- Из baseline выбирается по одной лучшей записи на каждый день (`pickBestPerDay`): из завершённых, максимальная по `totalProducts`.
- По этим записям вычисляется **медиана** для inStock, errorRate, speed.
- Медиана выбрана, а не среднее, для устойчивости к выбросам.

**Формула скорости:** `parsingDurationMinutes / totalProducts * 1000` (минуты на 1000 товаров).
Учитываются только записи с `totalProducts > 100`.

---

### Логика мастер-города

**Назначение:** в некоторых сайтах данные одинаковы во всех городах — достаточно проверить один «мастер-город».

**Откуда берётся:**
1. Авторитетный источник: `zoomos_sites.master_city_id` (обновляется через `/zoomos/sites/{id}/master-city`).
2. Legacy fallback: `zoomos_city_ids.master_city_id` (устарело с V53, обновляется через `/zoomos/city-ids/{id}/master-city`).

**Как влияет на анализ:**
- При наличии мастер-города `expectedCities = Set.of(masterId)` — анализируется только один город.
- Агрегированные метрики (baseline, тренды) вычисляются только по записям мастер-города.
- Метрика `masterCityId` отображается в результатах для информации.

---

### Логика `ignore_stock`

Поле `ZoomosKnownSite.ignoreStock = true` означает, что сайт не предоставляет данные об остатках.

**Влияние:**
- Вместо проверки `inStock` проверяется только `totalProducts > 0`.
- Если `totalProducts == 0` → статус `NO_PRODUCTS` (CRITICAL).
- Проверки `STOCK_ZERO`, `STOCK_DROP`, `STOCK_TREND_DOWN` не выполняются.

---

### Алгоритм `analyzeCityGroup` — анализ одного города

```
1. Найти lastKnownStat (самая поздняя запись из baseline + текущих, по parsingDate + updatedTime)
   → Используется для подсказки при NOT_FOUND / STALLED

2. Если stats пуст → NOT_FOUND (CRITICAL), вернуть

3. Фильтрация dayStats (для однодневной проверки):
   - Берём записи за checkDate (dateFrom == dateTo)
   - Дополнительно: незавершённые записи за checkDate-1 (ночные выкачки)
   - Если isSingleDay и dayStats пуст → NOT_FOUND

4. Разделить dayStats на:
   - finished:   isFinished=true ИЛИ completionPercent >= 100
   - inProgress: остальные

5. Если finished НЕ пуст:
   latest = pickBestPerDay(finished)  ← из завершённых, max по totalProducts
   
   Если ignoreStock:
     → Если totalProducts == 0 → NO_PRODUCTS (CRITICAL)
   Иначе:
     → Если totalProducts == 0 И inStock == 0 И completion >= 100 → EMPTY_RESULT (WARNING)
     → Если inStock == 0 → STOCK_ZERO (CRITICAL)
     → Если inStock задан: вычислить baselineInStock (медиана из baseline)
       drop = (baselineInStock - inStock) / baselineInStock * 100
       Если drop >= dropThreshold → STOCK_DROP (CRITICAL)

6. Если finished ПУСТ (только inProgress):
   
   Определение STALLED:
   → Взять максимальный updatedTime среди всех inProgress
   → Если now - maxUpdatedTime >= stallMinutes → STALLED (CRITICAL)
   
   Если НЕ STALLED, для каждой inProgress-записи:
   → Если deadline < now → DEADLINE_MISSED (CRITICAL)
   → Иначе если startTime + updatedTime + completionPercent > 0:
       elapsed = updatedTime - startTime (в минутах)
       totalEstimated = elapsed / (completionPercent / 100)
       estFinish = startTime + totalEstimated
       reliable = completionPercent >= 10
       → Если estFinish > deadline → IN_PROGRESS_RISK (CRITICAL)
       → Иначе → IN_PROGRESS_OK (IN_PROGRESS)

7. Итоговый статус = наихудший из issues; если нет issues:
   - если есть finished → OK
   - если есть незавершённые → IN_PROGRESS
```

---

### Алгоритм `isSingleDay` vs период

Параметр `isSingleDay = run.dateFrom.equals(run.dateTo)`.

**Если `isSingleDay = true`:**
- `dayStats` фильтруется строго по `parsingDate = checkDate` ПЛЮС незавершённые записи за `checkDate - 1` (overnight-выкачки).
- Если `dayStats` пуст → сразу `NOT_FOUND`.

**Если `isSingleDay = false` (период):**
- `dayStats = stats` (все записи за период без дополнительной фильтрации по дате).

---

### Алгоритм определения зависания (STALLED)

Зависание определяется по самому свежему `updatedTime` среди **всех** `inProgress`-записей:

```
maxUpdatedTime = max(s.updatedTime for s in inProgress where s.updatedTime != null)
if now - maxUpdatedTime >= stallMinutes → STALLED
```

Важно: итерация по отдельным записям ошибочна — в списке могут быть старые записи предыдущего дня из `parseInProgressPage` (с `dateFrom-1`), которые ложно дают STALLED.

---

### Алгоритм прогноза завершения (IN_PROGRESS)

Формула: `totalEstimated = elapsed * 100 / completionPercent`

```
elapsed = updatedTime - startTime (в минутах)
totalEstimated = elapsed / (completionPercent / 100.0)
estFinish = startTime + totalEstimated
```

Прогноз считается **надёжным** (`estimatedFinishReliable = true`) при `completionPercent >= 10`.

---

## 3. Справочник статусов

### Уровни (`ZoomosResultLevel`)

| Уровень | priority | Описание |
|---|---|---|
| `CRITICAL` | 0 | Критическая проблема — отображается первым |
| `WARNING` | 1 | Предупреждение |
| `TREND` | 2 | Тренд — замедление или стабильное ухудшение |
| `IN_PROGRESS` | 3 | Выкачка идёт |
| `OK` | 4 | Всё в порядке |

Чем меньше `priority`, тем выше приоритет (CRITICAL всегда выше OK).

---

### Статусы (`StatusReason`)

| Код | Уровень | Условие срабатывания | Короткий ярлык |
|---|---|---|---|
| `NOT_FOUND` | CRITICAL | Нет записей для данного города/периода | Нет данных за период |
| `DEADLINE_MISSED` | CRITICAL | Выкачка в процессе, `deadline < now` | Дедлайн пропущен |
| `STALLED` | CRITICAL | `now - maxUpdatedTime >= stallMinutes` и нет завершённых | Выкачка зависла |
| `IN_PROGRESS_RISK` | CRITICAL | Идёт, но прогноз показывает что не успеет к дедлайну | Не успеет к дедлайну |
| `STOCK_ZERO` | CRITICAL | `latestStat.inStock == 0` | В наличии = 0 |
| `NO_PRODUCTS` | CRITICAL | `ignoreStock=true` и `totalProducts == 0` | Нет товаров |
| `STOCK_DROP` | CRITICAL | Падение inStock относительно baseline ≥ `dropThreshold` % | В наличии упало |
| `CITIES_MISSING` | CRITICAL | Хотя бы один ожидаемый город не нашёлся | Не все города выкачались |
| `ACCOUNT_MISSING` | CRITICAL | Нет записей с указанным `accountFilter` | Нет выкачки с нужным аккаунтом |
| `CATEGORY_MISSING` | CRITICAL | Фильтр `parserInclude` не дал результатов | Категория не найдена |
| `ERROR_GROWTH` | WARNING | Рост доли ошибок > baseline на `errorGrowthThreshold`%, при `errorCount >= minAbsoluteErrors` | Ошибок парсинга больше нормы |
| `STOCK_TREND_DOWN` | WARNING | inStock снижается ≥ 3 дней подряд, суммарное падение ≥ `trendDropThreshold/2`% | inStock снижается |
| `EMPTY_RESULT` | WARNING | Выкачка 100%, но totalProducts=0 и inStock=0 | Пустая выкачка |
| `SPEED_SPIKE` | TREND | Разовое замедление: curSpeed > baseline * (1 + trendDropThreshold/100) | Разовое замедление скорости |
| `SPEED_TREND` | TREND | Последние 3 дня скорость растёт подряд | Выкачка замедляется |
| `IN_PROGRESS_OK` | IN_PROGRESS | Выкачка идёт, прогноз в пределах нормы | В процессе |

---

### Порядок отображения проблем

Внутри каждого уровня проблемы сортируются по приоритету:

**CRITICAL:** NOT_FOUND → STALLED → DEADLINE_MISSED/IN_PROGRESS_RISK → STOCK_ZERO/NO_PRODUCTS → STOCK_DROP → CITIES_MISSING → ACCOUNT_MISSING → CATEGORY_MISSING

**WARNING:** ERROR_GROWTH → STOCK_TREND_DOWN → EMPTY_RESULT

**TREND:** SPEED_SPIKE → SPEED_TREND

---

## 4. Парсинг данных (export.zoomos.by)

### Страница источник

URL-шаблон: `{baseUrl}/shops-parser/{siteName}/parsing-history`

Параметры запроса:
- `dateFrom`, `dateTo` — формат `dd.MM.yyyy`
- `shop` — имя магазина (для ITEM), `-` (для API)
- `onlyFinished=1` — только завершённые (не указывается для in-progress запроса)
- `upd` — timestamp для обхода кэша

### Поля, собираемые при парсинге

Данные извлекаются из HTML-таблицы `table#parser-history-table` через JavaScript в Playwright.

| Поле в Stats | Колонка таблицы | Описание |
|---|---|---|
| `parsingId` | `id` | ID выкачки |
| `serverName` | `сервер` | Сервер |
| `clientName` | `клиент` | Клиент (пусто для API-выкачек) |
| `siteName` | `сайт` | Сайт |
| `cityName` | `город` | Строка `"3612 - Нижний Новгород"` |
| `addressName` | `адрес` | Строка `"[14342] Братск, Ленина пр-кт, 7"` |
| `startTime` | `старт (общий)` | Формат `"dd.MM.yy H:mm"` |
| `finishTime` | `финиш` | То же |
| `updatedTime` | `обновлено` | То же |
| `totalProducts` | `кол-во товаров` | |
| `categoryCount` | `кол-во категорий` | |
| `inStock` | `в наличии` | |
| `errorCount` | `кол-во ошибок` | |
| `completionTotal` | `завершено (всего)` | Строка `"100% (58%)"` |
| `completionPercent` | `завершено (всего)` | Число до первого `%` |
| `parsingDuration` | `время` | Строка `"27 мин"`, `"1 ч 23 мин"` |
| `parsingDurationMinutes` | `время` | Пересчитано в минуты |
| `parserDescription` | `парсер` | |
| `accountName` | `аккаунт` | После очистки от `[ID]` |

---

### Маппинг колонок (fallback-алиасы)

Если точное имя не найдено — ищется по вхождению:

| Точное имя | Алиасы |
|---|---|
| `старт (общий)` | `старт` |
| `кол-во товаров` | `товаров`, `количество товаров` |
| `кол-во ошибок` | `ошибок`, `количество ошибок` |
| `кол-во категорий` | `категорий`, `количество категорий` |
| `завершено (всего)` | `завершено` |
| `в наличии` | `наличии` |

---

### Логика определения `cityId` из строки города

Метод: `ZoomosCheckService.extractCityId(String cityStr)`

```
"3612 - Нижний Новгород"  → "3612"   (подстрока до " - ")
"3612"                     → "3612"   (только цифры → возвращается как есть)
"Москва"                   → null     (нет разделителя, не число)
```

---

### Логика определения `addressId`

Метод: `ZoomosCheckService.extractAddressId(String addressStr)`

```
"[14342] Братск, Ленина пр-кт, 7"  → "14342"   (число между [ и ])
"Братск, Ленина пр-кт, 7"           → null      (нет [ ])
```

---

### Логика парсинга аккаунта

Метод: `ZoomosPlaywrightHelper.parseAccountName(String raw)`

```
"[123] user@account"  → "user@account"  (убирает [ID] префикс)
"user@account"         → "user@account"  (возвращает как есть)
null/пусто             → null
```

---

### Фильтр парсера (parser_include / parser_exclude)

Применяется к полю `parserDescription` (колонка «Парсер»):

**Логика include:**
- Разделитель — точка с запятой (`;`), так как паттерны могут содержать запятые.
- Режим `OR` (default): достаточно совпадения хотя бы с одним паттерном.
- Режим `AND`: все паттерны должны содержаться в строке.
- Если `parserDescription` пуст — строка пропускается (не фильтруется).
- Если `parserInclude` пуст — фильтрация не применяется (все строки проходят).

**Логика exclude (всегда OR):**
- Если хотя бы одна подстрока из `parserExclude` содержится в `parserDescription` → строка исключается.

---

### Типы проверки (CheckType)

| Тип | Страница | Описание |
|---|---|---|
| `API` | `parsing-history?shop=-` | Глобальная API-выкачка (поле «Клиент» пустое) |
| `ITEM` | `parsing-history?shop={shopName}` | Выкачка по карточкам товаров клиента |

---

### In-progress запрос

Выполняется дополнительно для сайтов, по которым не найдено данных (или не все города/адреса покрыты):
- URL-период: `dateFrom-1` ... `dateTo` (на день раньше, для ночных overnight-выкачек).
- Параметр `onlyFinished` не передаётся.
- Все полученные записи принудительно помечаются `isFinished=false`.

---

### Авторизация

1. Попытка загрузить куки из таблицы `zoomos_sessions`.
2. Если куки невалидны (редирект на `/login`) — выполняется логин:
   - `POST /login` с полями `j_username`, `j_password`.
3. После успешной авторизации куки сохраняются обратно в `zoomos_sessions`.

---

## 5. REST API

**Базовый путь:** `/zoomos`

### ZoomosAnalysisController

| Метод | URL | Параметры | Ответ |
|---|---|---|---|
| GET | `/zoomos` | — | HTML (index page) |
| POST | `/zoomos/settings` | form params: пороги | redirect /zoomos |
| POST | `/zoomos/shops/add` | `shopName` | redirect |
| POST | `/zoomos/shops/{id}/delete` | — | redirect |
| POST | `/zoomos/shops/{shopId}/priority` | — | JSON `{success, isPriority}` |
| POST | `/zoomos/shops/{shopId}/toggle-enabled` | — | JSON `{success, isEnabled}` |
| POST | `/zoomos/shops/{shopName}/sync` | — | redirect |
| GET | `/zoomos/check/run` | redirect | redirect /zoomos |
| **POST** | **`/zoomos/check/run`** | `shopId`, `dateFrom`, `dateTo`, `timeFrom`, `timeTo`, `dropThreshold`, `errorGrowthThreshold`, `baselineDays`, `minAbsoluteErrors`, `trendDropThreshold`, `trendErrorThreshold` | JSON `{success, operationId}` |
| GET | **`/zoomos/check/analyze/{runId}`** | `profileId` (opt), `deadline` (ISO, opt), `stallMinutes` (opt) | JSON `List<ZoomosSiteResult>` |
| GET | `/zoomos/check/latest` | `shopId` | JSON `{runId, okCount, warningCount, errorCount, notFoundCount, status, dateFrom, dateTo, startedAt, completedAt}` |
| GET | `/zoomos/check/results-new/{runId}` | — | HTML (страница результатов) |
| GET | `/zoomos/check/run/{runId}/info` | — | JSON `{shopName, dateFrom, dateTo, startedAt}` |
| GET | `/zoomos/check/history` | `shop` (opt) | HTML |
| POST | `/zoomos/check/history/delete` | `ids` (List<Long>) | redirect |
| GET | `/zoomos/shops/{shopId}/last-instock` | — | JSON `{inStock, date, sitesCount, previousInStock, change}` |
| GET | `/zoomos/city-names` | — | HTML |
| GET | `/zoomos/city-addresses` | `cityIds` (opt), `siteName` (opt) | JSON `{cityId: [{id, name}]}` |
| POST | `/zoomos/city-names/save` | `cityId`, `cityName` | JSON `{success}` |
| POST | `/zoomos/city-names/{cityId}/delete` | — | JSON `{success}` |
| POST | `/zoomos/city-addresses/save` | `cityId`, `addressId`, `addressName` (opt) | JSON `{success}` |
| POST | `/zoomos/city-addresses/delete` | `cityId`, `addressId` | JSON `{success}` |
| GET | `/zoomos/sites` | — | HTML |
| POST | `/zoomos/sites/add` | `siteNames`, `checkType` | JSON `{success, added, skipped}` |
| POST | `/zoomos/sites/{id}/delete` | — | JSON `{success, deletedCityIds}` |
| POST | `/zoomos/sites/{id}/check-type` | `checkType` | JSON `{success, updatedCityIds}` |
| POST | `/zoomos/sites/{id}/priority` | — | JSON `{success, isPriority}` |
| POST | `/zoomos/sites/{id}/ignore-stock` | — | JSON `{success, ignoreStock}` |
| POST | `/zoomos/sites/{id}/master-city` | `masterCityId` (opt) | JSON `{success}` |
| POST | `/zoomos/sites/{id}/fetch-equal-prices` | — | JSON `{success}` |
| GET | `/zoomos/sites/{id}/historical-cities` | — | JSON `List<String>` |
| POST | `/zoomos/city-ids/{id}/toggle` | — | JSON `{success, isActive}` |
| POST | `/zoomos/city-ids/{id}/update` | `cityIds` | JSON `{success}` |
| POST | `/zoomos/city-ids/{id}/master-city` | `masterCityId` (opt) | JSON `{success}` |
| POST | `/zoomos/city-ids/{id}/check-type` | `checkType` | JSON `{success}` |
| POST | `/zoomos/city-ids/{id}/update-addresses` | body: `{cityId: [addressId]}` | JSON `{success}` |
| POST | `/zoomos/city-ids/{id}/parser-filter` | `include`, `includeMode`, `exclude` | JSON `{success}` |
| POST | `/zoomos/city-ids/{id}/config-issue` | `type` (opt), `note` (opt) | JSON `{success, hasConfigIssue, configIssueType, configIssueNote}` |
| POST | `/zoomos/city-ids/{id}/delete` | — | JSON `{success}` |
| POST | `/zoomos/shops/{shopId}/city-ids/add` | `siteName`, `cityIds` (opt) | JSON `{success}` |
| POST | `/zoomos/shops/{shopId}/city-ids/delete-all` | — | JSON `{success}` |
| GET | `/zoomos/parser-patterns` | `siteName` | JSON `List<String>` |
| GET | `/zoomos/schedule` | — | HTML |
| GET | `/zoomos/schedule/last-run-times` | `shopId` | JSON `{scheduleId: "dd.MM.yyyy HH:mm"}` |
| POST | `/zoomos/schedule/{shopId}/new` | — | JSON `{success, scheduleId}` |
| POST | `/zoomos/schedule/item/{scheduleId}` | `cronExpression`, `label`, `timeFrom`, `timeTo`, `dropThreshold`, `errorGrowthThreshold`, `baselineDays`, `minAbsoluteErrors`, `dateOffsetFrom`, `dateOffsetTo`, `trendDropThreshold`, `trendErrorThreshold` | JSON `{success}` |
| POST | `/zoomos/schedule/item/{scheduleId}/toggle` | — | JSON `{success, isEnabled}` |
| POST | `/zoomos/schedule/item/{scheduleId}/delete` | — | JSON `{success}` |
| POST | `/zoomos/api/schedule/{shopId}/toggle` | — | JSON `{success, isEnabled}` |
| GET | `/zoomos/clients` | — | HTML |
| GET | `/zoomos/api/priority-alerts` | — | JSON `List` (пока пустой) |

### ZoomosRedmineController

**Базовый путь:** `/zoomos/redmine`

| Метод | URL | Параметры | Ответ |
|---|---|---|---|
| GET | `/zoomos/redmine/options` | — | JSON `{enabled, trackers, statuses, priorities, users, customFields, defaults}` |
| GET | `/zoomos/redmine/issue/{issueId}` | — | JSON `{id, subject, description, trackerId, statusId, priorityId, assignedToId, customFieldValues, comments}` |
| GET | `/zoomos/redmine/check` | `site` | JSON `{enabled, existing: List}` |
| GET | `/zoomos/redmine/check-batch` | `sites` (List) | JSON `{siteName: {issueId, status, url, isClosed}}` |
| POST | `/zoomos/redmine/create` | body: `RedmineCreateRequest` | JSON `{success, issue: {id, status, url, site, shortMessage}}` |
| PUT | `/zoomos/redmine/update/{issueId}` | body: `RedmineCreateRequest` | JSON `{success, issue: {id, url, statusName, isClosed}}` |
| DELETE | `/zoomos/redmine/local-delete/{site}` | — | JSON `{success}` |

---

### Формат JSON для `/zoomos/check/analyze/{runId}`

Возвращает `List<ZoomosSiteResult>`, отсортированных по `status.priority` (CRITICAL первым).

Каждый элемент:
```json
{
  "siteName": "wildberries.ru",
  "cityId": "3612",
  "cityName": "Нижний Новгород",
  "accountName": "user@account",
  "checkType": "ITEM",
  "status": "CRITICAL",
  "statusReasons": [
    {
      "reason": "STOCK_DROP",
      "message": "В наличии упало на 25% (порог 10%)",
      "level": "CRITICAL",
      "shortLabel": "В наличии упало"
    }
  ],
  "latestStat": { /* ZoomosParsingStats без lazy полей */ },
  "baselineInStock": 1500.0,
  "baselineErrorRate": 0.02,
  "baselineSpeedMinsPer1000": 45.0,
  "shopParam": "shopname",
  "historyBaseUrl": "https://export.zoomos.by/shops-parser/wildberries.ru/parsing-history",
  "ignoreStock": false,
  "masterCityId": null,
  "estimatedFinish": null,
  "estimatedFinishReliable": null,
  "isStalled": false,
  "inStockHistory": [{"date": "2026-04-22", "inStock": 1520}, ...],
  "errorHistory": [...],
  "speedHistory": [...],
  "cityResults": [ /* CityResult[] */ ],
  "inProgressCities": [],
  "cityIdsId": 42,
  "hasConfigIssue": false,
  "configIssueType": null,
  "configIssueNote": null,
  "siteId": 7,
  "isPriority": false,
  "itemPriceConfigured": true,
  "equalPrices": null,
  "equalPricesCheckedAt": null,
  "dateFrom": "28.04.2026",
  "dateTo": "28.04.2026"
}
```

---

## 6. DTO схемы

### `ZoomosSiteResult`

| Поле | Тип | Описание |
|---|---|---|
| `siteName` | String | |
| `cityId` | String | Только при одном городе |
| `cityName` | String | Только при одном городе |
| `accountName` | String | |
| `checkType` | String | `API` или `ITEM` |
| `status` | ZoomosResultLevel | |
| `statusReasons` | List\<SiteIssue\> | Отсортированные проблемы |
| `latestStat` | ZoomosParsingStats | Последняя завершённая запись |
| `baselineInStock` | Double | Медиана inStock из baseline |
| `baselineErrorRate` | Double | Медиана errorCount/totalProducts |
| `baselineSpeedMinsPer1000` | Double | Медиана мин/1000 товаров |
| `shopParam` | String | Имя магазина (`-` для API) |
| `historyBaseUrl` | String | Базовый URL истории на export.zoomos.by |
| `ignoreStock` | boolean | |
| `masterCityId` | String | |
| `estimatedFinish` | ZonedDateTime | Прогноз завершения |
| `estimatedFinishReliable` | Boolean | Достоверность прогноза |
| `isStalled` | Boolean | Зависла ли выкачка |
| `inStockHistory` | List\<SparklinePoint\> | История inStock (7 точек) |
| `errorHistory` | List\<SparklinePoint\> | История ошибок (7 точек) |
| `speedHistory` | List\<SparklinePoint\> | История скорости (7 точек) |
| `cityResults` | List\<CityResult\> | Результаты по городам |
| `inProgressCities` | List\<CityResult\> | Только города в статусе IN_PROGRESS |
| `cityIdsId` | Long | ID записи в zoomos_city_ids |
| `hasConfigIssue` | Boolean | |
| `configIssueType` | String | |
| `configIssueNote` | String | |
| `siteId` | Long | ID в zoomos_sites |
| `isPriority` | Boolean | |
| `itemPriceConfigured` | Boolean | |
| `equalPrices` | Boolean | |
| `equalPricesCheckedAt` | String | Формат `dd.MM.yyyy HH:mm` |
| `dateFrom` | String | Формат `dd.MM.yyyy` |
| `dateTo` | String | Формат `dd.MM.yyyy` |

---

### `CityResult`

Java record:

| Поле | Тип | Описание |
|---|---|---|
| `cityId` | String | |
| `cityName` | String | |
| `status` | ZoomosResultLevel | |
| `inStock` | Integer | |
| `issues` | List\<SiteIssue\> | |
| `estimatedFinish` | ZonedDateTime | |
| `estimatedFinishReliable` | Boolean | |
| `isStalled` | boolean | |
| `baselineInStock` | Double | |
| `inStockDelta` | Integer | Абсолютное изменение vs baseline |
| `inStockDeltaPercent` | Integer | Процентное изменение vs baseline |
| `cityIdsId` | Long | |
| `hasConfigIssue` | Boolean | |
| `configIssueType` | String | |
| `configIssueNote` | String | |
| `lastKnownDate` | LocalDate | Дата последней известной выкачки (для подсказки) |
| `lastKnownInStock` | Integer | |
| `lastKnownCompletionPercent` | Integer | |
| `lastKnownIsStalled` | Boolean | |
| `lastKnownUpdatedTime` | ZonedDateTime | |

---

### `SiteIssue`

Java record:

| Поле | Тип | Описание |
|---|---|---|
| `reason` | StatusReason | Тип проблемы |
| `message` | String | Форматированное сообщение с параметрами |
| `level` | ZoomosResultLevel | (вычисляемое из `reason.level`) |
| `shortLabel` | String | Короткий ярлык (из `reason.shortLabel`) |

---

### `SparklinePoint`

| Поле | Тип | Описание |
|---|---|---|
| `date` | LocalDate | Дата точки |
| `inStock` | Integer | Значение (inStock, errorCount или скорость в зависимости от типа) |

---

### `ZoomosCheckParams`

| Поле | Тип | Описание |
|---|---|---|
| `shopId` | Long | |
| `scheduleId` | Long | null если не привязан к расписанию |
| `dateFrom` | LocalDate | |
| `dateTo` | LocalDate | |
| `timeFrom` | String | `HH:mm` или null |
| `timeTo` | String | `HH:mm` или null |
| `dropThreshold` | int | |
| `errorGrowthThreshold` | int | |
| `baselineDays` | int | |
| `minAbsoluteErrors` | int | |
| `trendDropThreshold` | int | |
| `trendErrorThreshold` | int | |
| `operationId` | String | UUID для WebSocket топика |

---

### `RedmineCreateRequest`

| Поле | Тип | Описание |
|---|---|---|
| `site` | String | Имя сайта |
| `city` | String | Название города (для описания) |
| `subject` | String | Тема задачи |
| `description` | String | Описание (если пусто — генерируется автоматически) |
| `shortMessage` | String | Краткое описание проблемы |
| `trackerId` | int | ID трекера Redmine |
| `statusId` | int | ID статуса |
| `priorityId` | int | ID приоритета |
| `assignedToId` | int | ID исполнителя (0 = не назначен) |
| `historyUrl` | String | URL истории выкачки |
| `matchingUrl` | String | URL матчинга |
| `customFields` | Map\<Integer, String\> | Кастомные поля {fieldId → value} |

---

## 7. Интеграции

### Redmine (tt.zoomos.by)

**Конфигурация:** `RedmineConfig` (`@ConfigurationProperties(prefix = "redmine")`), хранится в `application.yml`.

**Ключевые параметры конфига:**
- `baseUrl` — URL Redmine (напр. `https://tt.zoomos.by`)
- `apiKey` — API-ключ (передаётся в заголовке `X-Redmine-API-Key`)
- `projectId` — ID проекта для создания задач
- `trackerId`, `statusId`, `priorityId`, `assignedToId` — значения по умолчанию

**Используемые API Redmine:**

| Метод | URL | Назначение |
|---|---|---|
| GET | `/trackers.json` | Список трекеров |
| GET | `/issue_statuses.json` | Список статусов |
| GET | `/enumerations/issue_priorities.json` | Список приоритетов |
| GET | `/projects/{projectId}/memberships.json?limit=100` | Список участников |
| GET | `/custom_fields.json` | Кастомные поля |
| GET | `/issues.json?project_id=...&subject=~{site}&status_id=*&limit=25` | Поиск задач по сайту |
| GET | `/issues/{issueId}.json?include=journals` | Детали задачи с комментариями |
| POST | `/issues.json` | Создание задачи |
| PUT | `/issues/{issueId}.json` | Обновление задачи |

**Важная особенность:** Redmine-сервер (Apache + mod_jk + Phusion Passenger) возвращает HTTP 404 с пустым телом на POST/PUT/DELETE, но операции **выполняются успешно**. Используется `postIgnoring404()` — выполняет POST, игнорирует 404, затем ищет задачу через GET.

**HTTP-клиенты:**
- Для GET: `HttpClient` с `followRedirects(NORMAL)`.
- Для POST/PUT: `HttpClient` с `followRedirects(NEVER)` — иначе POST→GET превращается в GET и возвращает другой статус.

**Параллельная проверка (batch-check):**
- Используется `ExecutorService` (пул 8 потоков) для параллельных GET-запросов к Redmine при проверке нескольких сайтов одновременно.

**Кастомные поля Redmine (используются в Zoomos Check):**

| Имя поля в Redmine | Ключ в коде | Описание |
|---|---|---|
| `В чем ошибка` | `cfError` | Тип ошибки выкачки |
| `Способ выкачки` | `cfMethod` | Метод выкачки |
| `Вариант настройки` | `cfVariant` | Вариант конфигурации |

---

### WebSocket / STOMP

**Конфигурация:** стандартный Spring WebSocket (STOMP), endpoint `/ws`.

**Топики прогресса проверки:**

| Топик | Формат сообщения | Описание |
|---|---|---|
| `/topic/zoomos-check/{operationId}` | `{current, total, message, percent}` | Прогресс конкретного запуска (по UUID) |
| `/topic/zoomos-check/shop/{shopId}` | `{current, total, message, percent}` | Прогресс для конкретного магазина |

**Поля сообщения прогресса:**

| Поле | Тип | Описание |
|---|---|---|
| `current` | int | Обработано сайтов |
| `total` | int | Всего сайтов |
| `message` | String | Текстовый статус |
| `percent` | int | Процент завершения (current*100/total) |

**Жизненный цикл:**
1. `POST /zoomos/check/run` → возвращает `{operationId}` немедленно.
2. Проверка запускается асинхронно через `zoomosCheckExecutor`.
3. В процессе парсинга каждого сайта отправляется сообщение в оба топика.
4. По завершении — финальное сообщение `"Проверка завершена"`.

**Транзакционная безопасность:** перед отправкой финального WebSocket-сообщения выполняется `REQUIRES_NEW`-транзакция для сохранения `COMPLETED`-статуса в БД. Это гарантирует, что к моменту реакции JS на WebSocket, статус уже виден в БД.

---

## 8. Конфигурация

### `ZoomosConfig` (application.yml, префикс `zoomos`)

| Параметр | Тип | По умолчанию | Описание |
|---|---|---|---|
| `zoomos.baseUrl` | String | `https://export.zoomos.by` | URL Zoomos-сервера |
| `zoomos.username` | String | — | Логин для авторизации |
| `zoomos.password` | String | — | Пароль |
| `zoomos.timeoutSeconds` | int | `30` | Таймаут Playwright (в секундах) |
| `zoomos.retryAttempts` | int | `3` | Кол-во повторных попыток при таймауте |
| `zoomos.retryDelaySeconds` | int | `3` | Задержка между попытками (в секундах) |

### Глобальные настройки (таблица `zoomos_settings`)

Редактируются через UI (`POST /zoomos/settings`), хранятся в таблице как key-value.

| Ключ | По умолчанию | Описание |
|---|---|---|
| `default.drop_threshold` | `10` | Порог падения inStock (%) |
| `default.error_growth_threshold` | `30` | Порог роста ошибок парсинга (%) |
| `default.baseline_days` | `7` | Дней для baseline-анализа |
| `default.min_absolute_errors` | `5` | Мин. абсолютных ошибок для WARNING |
| `default.trend_drop_threshold` | `30` | Тренд: порог падения/замедления (%) |
| `default.trend_error_threshold` | `100` | Тренд: порог роста ошибок (%) |
| `default.stall_minutes` | `60` | Порог зависания в минутах |

Значение `stall_minutes` читается через `ZoomosSettingsService.getStallMinutes()` — не хранится в `zoomos_check_runs`.

### Пороги в профилях (`zoomos_check_profiles`)

Каждый профиль имеет собственные пороги (те же поля, что и в `zoomos_check_runs`). Профиль может иметь дополнительно `stall_minutes`.

### Executor для фоновых проверок

Bean: `zoomosCheckExecutor` (Spring `TaskExecutor`). Используется для параллельного запуска нескольких проверок одновременно (разных магазинов).
