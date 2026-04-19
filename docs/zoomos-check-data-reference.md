# Zoomos Check — Справочник данных и интеграций

> Описание данных, схемы БД и интеграций без бизнес-логики.
> Актуально на: 2026-04-19

---

## 1. Данные с export.zoomos.by

### URL-шаблон запроса

```
{baseUrl}/shops-parser/{siteName}/parsing-history
  ?upd={timestamp}&dateFrom=dd.MM.yyyy&dateTo=dd.MM.yyyy
  &shop={shopParam}&onlyFinished=1
```

- **API-сайты** (`checkType='API'`): `shop=-`
- **ITEM-сайты** (`checkType='ITEM'`): `shop={shopName}`

### Поля, извлекаемые со страницы (через Playwright)

| Поле | Тип | Описание |
|------|-----|---------|
| `parsingId` | Long | ID парсинга на сервере |
| `siteName` | String | Наименование сайта |
| `cityName` | String | Город: "3509 - Вологда" или "3509" |
| `serverName` | String | Наименование сервера парсинга |
| `startTime` | ZonedDateTime | Время начала выкачки |
| `finishTime` | ZonedDateTime | Время завершения (null если не завершена) |
| `totalProducts` | Integer | Всего товаров в каталоге |
| `inStock` | Integer | Товаров в наличии |
| `categoryCount` | Integer | Количество категорий |
| `errorCount` | Integer | Количество ошибок парсинга |
| `completionTotal` | String | "X из Y" |
| `completionPercent` | Integer | 0–100, процент завершения |
| `parsingDuration` | String | "HH:mm:ss" или "XчYмZс" |
| `parsingDurationMinutes` | Integer | Длительность в минутах |
| `clientName` | String | Наименование клиента |
| `addressId` | String | ID адреса: "14342" |
| `addressName` | String | Полное имя: "[14342] Братск, Ленина пр-кт, 7" |
| `updatedTime` | ZonedDateTime | Время последнего обновления записи |
| `isFinished` | Boolean | Завершена ли выкачка |
| `parserDescription` | String | Текст из колонки "Парсер" |

### Авторизация

- Таблица `zoomos_sessions` — one-record pattern, хранит куки браузера
- При редиректе на `/login` → переавторизация → повторный запрос

---

## 2. Схема БД — таблицы zoomos_*

### zoomos_shops
Магазины (клиенты Zoomos).

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `shop_name` | VARCHAR(255) UNIQUE | Параметр `shop=` в URL |
| `is_enabled` | BOOLEAN | default TRUE |
| `is_priority` | BOOLEAN | default FALSE |
| `last_synced_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | default NOW() |
| `client_id` | BIGINT FK | → `clients(id)` ON DELETE SET NULL |

---

### zoomos_city_ids
Конфигурация парсинга: какие города/адреса проверять для каждого магазина и сайта.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT FK | → `zoomos_shops(id)` ON DELETE CASCADE |
| `site_name` | VARCHAR(255) | Уникально вместе с shop_id |
| `city_ids` | TEXT | Запятая-разделённые ID городов: "3509,3510" |
| `address_ids` | TEXT | JSON: `{"cityId": ["14342", "15234"]}` или null |
| `master_city_id` | VARCHAR(50) | Если задан — проверяется только он |
| `check_type` | VARCHAR(10) | "API" или "ITEM" (default "API") |
| `is_active` | BOOLEAN | default TRUE |
| `parser_include` | TEXT | Подстроки включения для parser_description |
| `parser_include_mode` | VARCHAR(3) | "OR" или "AND" |
| `parser_exclude` | TEXT | Подстроки исключения |
| `has_config_issue` | BOOLEAN | default FALSE |
| `config_issue_note` | TEXT | Текст примечания к проблеме |
| `config_issue_type` | VARCHAR(30) | Тип проблемы (enum) |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### zoomos_check_runs
Журнал запусков проверок.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT FK | → `zoomos_shops(id)` ON DELETE CASCADE |
| `date_from` | DATE | Начало проверяемого периода |
| `date_to` | DATE | Конец проверяемого периода |
| `time_from` | VARCHAR(5) | "HH:mm" или null (→ 00:00) |
| `time_to` | VARCHAR(5) | "HH:mm" или null (→ 23:59) |
| `total_sites` | INTEGER | Всего активных сайтов (default 0) |
| `ok_count` | INTEGER | default 0 |
| `warning_count` | INTEGER | default 0 |
| `error_count` | INTEGER | default 0 |
| `not_found_count` | INTEGER | default 0 |
| `drop_threshold` | INTEGER | Порог падения inStock, % (default 10) |
| `error_growth_threshold` | INTEGER | Порог роста ошибок, % (default 30) |
| `baseline_days` | INTEGER | Дней для baseline-анализа (default 7) |
| `min_absolute_errors` | INTEGER | Мин. ошибок для WARNING (default 5) |
| `trend_drop_threshold` | INTEGER | Тренд: порог падения, % (default 30) |
| `trend_error_threshold` | INTEGER | Тренд: порог роста ошибок, % (default 100) |
| `status` | VARCHAR(20) | RUNNING / COMPLETED / FAILED |
| `error_message` | VARCHAR(1000) | |
| `timeout_count` | INTEGER | default 0 |
| `started_at` | TIMESTAMPTZ | |
| `completed_at` | TIMESTAMPTZ | null если RUNNING |

---

### zoomos_parsing_stats
Сырые данные парсинга — каждая строка = одна запись с export.zoomos.by.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `check_run_id` | BIGINT FK | → `zoomos_check_runs(id)` ON DELETE CASCADE |
| `city_id_ref` | BIGINT FK | → `zoomos_city_ids(id)` ON DELETE SET NULL |
| `parsing_id` | BIGINT | ID парсинга на сервере |
| `site_name` | VARCHAR(255) NOT NULL | |
| `city_name` | VARCHAR(255) | "3509 - Вологда" |
| `server_name` | VARCHAR(100) | |
| `start_time` | TIMESTAMPTZ | |
| `finish_time` | TIMESTAMPTZ | |
| `total_products` | INTEGER | |
| `in_stock` | INTEGER | |
| `category_count` | INTEGER | |
| `error_count` | INTEGER | default 0 |
| `completion_total` | VARCHAR(30) | "X из Y" |
| `completion_percent` | INTEGER | 0–100 |
| `parsing_duration` | VARCHAR(50) | |
| `parsing_duration_minutes` | INTEGER | |
| `client_name` | VARCHAR(255) | |
| `address_id` | VARCHAR | "14342" |
| `address_name` | TEXT | "[14342] Братск, Ленина пр-кт, 7" |
| `updated_time` | TIMESTAMPTZ | |
| `is_finished` | BOOLEAN | default TRUE |
| `is_baseline` | BOOLEAN | default FALSE |
| `parser_description` | TEXT | |
| `parsing_date` | DATE NOT NULL | |
| `check_type` | VARCHAR(10) | API / ITEM |
| `checked_at` | TIMESTAMPTZ | |

---

### zoomos_sites
Глобальный справочник известных сайтов (без привязки к магазину).

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) UNIQUE | |
| `check_type` | VARCHAR(10) | "API" или "ITEM" (default "ITEM") |
| `description` | VARCHAR(500) | |
| `is_priority` | BOOLEAN | default FALSE |
| `ignore_stock` | BOOLEAN | Игнорировать inStock-метрику (default FALSE) |
| `master_city_id` | VARCHAR(50) | Мастер-город на уровне сайта |
| `item_price_configured` | BOOLEAN | null=не проверено, false=не настроен, true=настроен |
| `cities_equal_prices` | BOOLEAN | null=не проверено |
| `cities_equal_prices_checked_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |

---

### zoomos_shop_schedules
Cron-расписания проверок для магазинов.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `shop_id` | BIGINT NOT NULL | → `zoomos_shops(id)` |
| `label` | VARCHAR(50) | Наименование расписания |
| `cron_expression` | VARCHAR(255) | 5-полевое cron (default "0 0 8 * * *") |
| `is_enabled` | BOOLEAN | default FALSE |
| `time_from` | VARCHAR(5) | "HH:mm" или null |
| `time_to` | VARCHAR(5) | "HH:mm" или null |
| `drop_threshold` | INTEGER | default 10 |
| `error_growth_threshold` | INTEGER | default 30 |
| `baseline_days` | INTEGER | default 7 |
| `min_absolute_errors` | INTEGER | default 5 |
| `trend_drop_threshold` | INTEGER | default 30 |
| `trend_error_threshold` | INTEGER | default 100 |
| `date_offset_from` | INTEGER | Смещение дня "от" (default -1, вчера) |
| `date_offset_to` | INTEGER | Смещение дня "до" (default 0, сегодня) |
| `last_run_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### zoomos_sessions
Сессионные куки авторизации (one-record pattern).

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | Всегда 1 запись |
| `cookies` | TEXT NOT NULL | Сериализованные куки браузера |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### zoomos_redmine_issues
Связь сайтов с задачами Redmine.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) UNIQUE | |
| `issue_id` | INTEGER | ID задачи в Redmine |
| `issue_status` | VARCHAR(100) | |
| `issue_url` | VARCHAR(500) | |
| `is_closed` | BOOLEAN | default FALSE |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### zoomos_city_names
Справочник: cityId → наименование города.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `city_id` | VARCHAR(50) PK | ID города в Zoomos |
| `city_name` | VARCHAR(255) | |
| `updated_at` | TIMESTAMPTZ | |

---

### zoomos_city_addresses
Справочник адресов по городам.

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `city_id` | VARCHAR(50) | |
| `address_id` | VARCHAR(50) | |
| `address_name` | VARCHAR(500) | |
| `updated_at` | TIMESTAMPTZ | |
| UNIQUE | (city_id, address_id) | |

---

### zoomos_parser_patterns
Накапливаемый справочник строк из колонки "Парсер".

| Колонка | Тип | Описание |
|---------|-----|---------|
| `id` | BIGSERIAL PK | |
| `site_name` | VARCHAR(255) | |
| `pattern` | TEXT | Строка из колонки "Парсер" |
| UNIQUE | (site_name, pattern) | |

---

### zoomos_settings
Глобальные настройки (key-value).

| Ключ | Описание |
|------|---------|
| `default.drop_threshold` | Порог падения inStock, % |
| `default.error_growth_threshold` | Порог роста ошибок, % |
| `default.baseline_days` | Дней для baseline-анализа |
| `default.min_absolute_errors` | Мин. ошибок для WARNING |
| `default.trend_drop_threshold` | Тренд: порог падения, % |
| `default.trend_error_threshold` | Тренд: порог роста ошибок, % |

---

## 3. Интеграции

### 3.1 Redmine (tt.zoomos.by)

**REST API (ZoomosRedmineController):**

| Endpoint | Method | Описание |
|----------|--------|---------|
| `/zoomos/redmine/options` | GET | Трекеры, статусы, приоритеты, пользователи, custom fields, дефолты |
| `/zoomos/redmine/issue/{issueId}` | GET | Детали задачи по ID |
| `/zoomos/redmine/check` | GET | Проверить наличие задач для сайта |
| `/zoomos/redmine/check-batch` | GET | Batch-проверка для списка сайтов |
| `/zoomos/redmine/create` | POST | Создать задачу |
| `/zoomos/redmine/update/{issueId}` | PUT | Обновить задачу |
| `/zoomos/redmine/local-delete/{site}` | DELETE | Удалить локальную запись |

**RedmineCreateRequest (POST/PUT body):**
```json
{
  "site": "сайт.ру",
  "city": "1913 - Алматы",
  "trackerId": 2,
  "statusId": 1,
  "priorityId": 3,
  "assignedToId": 5,
  "subject": "Проблема парсинга сайт.ру",
  "description": "...",
  "shortMessage": "Падение inStock на 86%",
  "notes": "Комментарий при обновлении",
  "historyUrl": "https://export.zoomos.by/shops-parser/...",
  "matchingUrl": "https://matching.zoomos.by/...",
  "customFields": [
    { "id": 1, "value": "значение" }
  ]
}
```

**Custom fields в Redmine:**
- `cfError` — "В чём ошибка"
- `cfMethod` — "Способ выкачки"
- `cfVariant` — "Вариант настройки"

**Конфиг (application.properties):**
```properties
redmine.enabled=true
redmine.base-url=https://tt.zoomos.by
redmine.api-key=...
redmine.project-id=...
redmine.tracker-id=...
redmine.status-id=...
redmine.priority-id=...
redmine.assigned-to-id=...
```

**Важно:** HTTP 404 на POST/PUT/DELETE — операции выполняются успешно (Apache + mod_jk + Phusion Passenger). Workaround: `postIgnoring404()`.

---

### 3.2 WebSocket / STOMP

- Endpoint: `/ws`
- Message broker: `/topic`, `/queue`
- Application prefix: `/app`
- Transport: WebSocket + SockJS fallback

**Топики:**

| Топик | Описание |
|-------|---------|
| `/topic/zoomos-check/{operationId}` | Прогресс для конкретной операции |
| `/topic/zoomos-check/shop/{shopId}` | Прогресс по магазину (broadcast) |

**Формат сообщения прогресса:**
```json
{
  "operationId": "uuid",
  "shopId": 123,
  "processed": 5,
  "total": 10,
  "message": "Проверяем site.ru (API)...",
  "status": "RUNNING"
}
```
Статусы: `RUNNING`, `COMPLETED`, `FAILED`

---

## 4. REST API контроллеров

### POST /zoomos/check/run — запуск проверки

| Параметр | Тип | Описание |
|----------|-----|---------|
| `shopId` | Long | ID магазина |
| `scheduleId` | Long (опц.) | ID расписания |
| `dateFrom` | String | Дата "с" |
| `dateTo` | String | Дата "по" |
| `timeFrom` | String (опц.) | "HH:mm" |
| `timeTo` | String (опц.) | "HH:mm" |
| `dropThreshold` | int | % падения (default 10) |
| `errorGrowthThreshold` | int | % роста ошибок (default 30) |
| `baselineDays` | int | дней baseline (default 7) |
| `minAbsoluteErrors` | int | мин. ошибок (default 5) |
| `trendDropThreshold` | int | тренд-порог (default 30) |
| `trendErrorThreshold` | int | тренд-порог ошибок (default 100) |

Ответ: `{ "success": true, "operationId": "uuid" }`

### POST /zoomos/city-ids/{id}/update-addresses
Body JSON: `{ "cityId": ["addressId1", "addressId2"] }`

### POST /zoomos/city-ids/{id}/parser-filter
Параметры: `include`, `includeMode` ("OR"/"AND"), `exclude`

### POST /zoomos/city-ids/{id}/config-issue
Параметры: `type` (enum ConfigIssueType), `note` (текст)

### GET /zoomos/city-addresses
Параметры: `cityIds` (запятая-разделённые), `siteName`

Ответ:
```json
{
  "cityId": [
    { "id": "14342", "name": "Братск, Ленина пр-кт, 7" }
  ]
}
```

### POST /zoomos/shops/{shopId}/city-ids/add
Параметры: `siteName`, `cityIds` (опц., "3509,3510")

---

## 5. Enum-типы

### ZoomosCheckType
- `API` — полная выкачка через API
- `ITEM` — выкачка по карточкам из кабинета

### CheckRunStatus
- `RUNNING` — проверка выполняется
- `COMPLETED` — завершена успешно
- `FAILED` — завершилась с ошибкой

### Типы проблем (warningType)
| Код | Уровень | Описание |
|-----|---------|---------|
| `STOCK_DROP_100` | ERROR | В наличии упало до 0 |
| `STOCK_DROP` | ERROR | Падение свыше порога |
| `NOT_FOUND` | ERROR | Нет данных о парсинге |
| `NO_PRODUCTS` | WARNING | 100% выкачка, нет товаров |
| `ALWAYS_ZERO_STOCK` | WARNING | В наличии всегда 0 |
| `SLOW_PARSING` | WARNING | Медленная выкачка |
| `IN_PROGRESS` | WARNING | Выкачка в процессе |
| `ERROR_GROWTH` | WARNING | Рост ошибок парсинга |
| `TREND_STOCK` | TREND | Падение доли в наличии |
| `TREND_ERRORS` | TREND | Рост ошибок относительно baseline |
| `TREND_SPEED` | TREND | Замедление выкачки |

---

## 6. Ключевые файлы исходного кода

| Файл | Назначение |
|------|-----------|
| `ZoomosCheckService.java` | Playwright-парсинг, WebSocket |
| `ZoomosAnalysisController.java` | REST `/zoomos/*` |
| `ZoomosRedmineController.java` | REST `/zoomos/redmine/*` |
| `RedmineService.java` | Интеграция с Redmine API |
| `ZoomosPlaywrightHelper.java` | Навигация в Playwright |
| `ZoomosSchedulerService.java` | Cron-расписания |
| `ZoomosParserService.java` | Управление магазинами и конфигурацией |
| `ZoomosSettingsService.java` | Глобальные настройки |
| `ZoomosViewService.java` | Вспомогательные методы для отображения |
| Миграции Flyway V22–V56 | SQL-схема |
