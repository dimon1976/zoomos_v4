# Zoomos Check — Проверка выкачки

> Последнее обновление: 2026-03 (рефакторинг: ZoomosCheckParams DTO, CheckRunStatus/ZoomosCheckType enum, FetchType.LAZY для shop, AddressFilterContext record, TIMESTAMPTZ миграция, индексы производительности V50; исправлен ключ baseline с addressId, оптимизация JOIN FETCH для check-history, timezone-корректное форматирование дат, точечный прогресс-индикатор при ручном запуске)

## Назначение

Модуль проверяет полноту и качество выкачки данных с `export.zoomos.by` за заданный период. Парсинг выполняется через Playwright (headless Chrome). Результаты хранятся в БД и отображаются на странице `/zoomos/check/results/{runId}`.

---

## URL-маршруты

| URL | Описание |
|-----|----------|
| `/zoomos` | Список магазинов, запуск проверки |
| `/zoomos/clients` | Проверки по клиентам (магазины, привязанные к кабинетам) |
| `/zoomos/check/results/{runId}` | Страница результатов (вердикт, issues, тренды, детали) |
| `/zoomos/check/history` | История всех запусков |
| `/zoomos/sites` | Справочник сайтов (checkType, ignoreStock, isPriority) |
| `/zoomos/schedule` | CRUD cron-расписаний per-магазин |
| `/zoomos/api/priority-alerts` | JSON проблем приоритетных сайтов (для баннера) |

---

## Структура БД (Flyway V23–V50)

```sql
zoomos_check_runs
  id, shop_id, date_from, date_to, time_from, time_to  -- "HH:mm" или null
  status (RUNNING/COMPLETED/FAILED)
  ok_count, warning_count, error_count, not_found_count
  drop_threshold (default 10%), error_growth_threshold (default 30%)
  min_absolute_errors (default 5)
  baseline_days, trend_drop_threshold, trend_error_threshold
  started_at, completed_at

zoomos_parsing_stats
  id, check_run_id, site_name, city_name, server_name, client_name
  start_time, finish_time, total_products, in_stock, error_count
  completion_total, parsing_duration, check_type (API/ITEM), is_finished
  parsing_id, category_count, completion_percent, parsing_duration_minutes
  is_baseline (V29), address_id, address_name

-- ВАЖНО: entity ZoomosKnownSite → таблица zoomos_sites (не zoomos_known_sites!)
zoomos_sites
  id, site_name (UNIQUE), check_type (API/ITEM)
  is_priority BOOLEAN DEFAULT FALSE
  ignore_stock BOOLEAN DEFAULT FALSE  -- сайты без данных inStock

zoomos_shop_schedules
  id, shop_id (UNIQUE FK → zoomos_shops), cron_expression, is_enabled
  time_from, time_to, drop_threshold, error_growth_threshold, baseline_days
  date_offset_from (default -1), date_offset_to (default 0)
  last_run_at, created_at, updated_at

zoomos_redmine_issues
  id, site_name (UNIQUE), issue_id, issue_status, is_closed, issue_url
  created_at, updated_at

-- V42: привязка магазина к клиенту
zoomos_shops
  ...client_id BIGINT FK → clients(id) ON DELETE SET NULL

-- V43: глобальные настройки Zoomos Check
zoomos_settings
  key VARCHAR(100) PRIMARY KEY, value VARCHAR(255), description VARCHAR(500)
  -- Ключи: default.drop_threshold, default.error_growth_threshold,
  --         default.baseline_days, default.min_absolute_errors,
  --         default.trend_drop_threshold, default.trend_error_threshold

-- V43: клиенты — признак активности и порядок сортировки
clients
  ...is_active BOOLEAN NOT NULL DEFAULT TRUE
  ...sort_order INTEGER NOT NULL DEFAULT 0

-- V49: конвертация created_at/updated_at в TIMESTAMPTZ для zoomos_redmine_issues
-- V50: индексы производительности
--   idx_check_runs_status                 ON zoomos_check_runs(status)
--   idx_shop_schedules_shop_id            ON zoomos_shop_schedules(shop_id)
--   idx_shop_schedules_is_enabled         ON zoomos_shop_schedules(is_enabled) WHERE is_enabled=TRUE
--   idx_parsing_stats_baseline_lookup     ON zoomos_parsing_stats(site_name, is_baseline, start_time DESC)
--   idx_parsing_stats_site_addr_completion ON zoomos_parsing_stats(site_name, address_id, completion_percent, start_time DESC)
```

---

## Логика оценки (`ZoomosCheckService.evaluateAndBuildIssues`)

Единственная точка оценки группы выкачек — метод `evaluateAndBuildIssues(...)`. Возвращает `GroupEvalResult(status, issues)` — статус и готовые issue-сообщения за один проход. Заменяет ранее раздельные `evaluateGroup()` + `buildGroupIssues()`.

### Статусы

Только **OK** / **WARNING** / **ERROR**. NOT_FOUND упразднён — "нет данных" = ERROR.

### Ключевые концепции

- **baseline** (`MedianStats`) — медиана `inStock`, `totalProducts`, `parsingDurationMinutes` за `baselineDays` дней до начала проверки. Если `baselineDays = 0` или данных < 1 записи → `baseline = null`.
- **Важно**: baseline загружается только из записей **текущего** `check_run_id` (поле `is_baseline=true`), предзагружается одним запросом в начале `checkResults()`. Это исключает кросс-run загрязнение медианы.
- **Сравнение**: с `baseline.inStock` если доступен, иначе с `prev.inStock` (предыдущая запись).
- **ignoreStock** — флаг из `zoomos_sites.ignore_stock`: inStock-метрика пропускается, используется `totalProducts`.

### Алгоритм evaluateAndBuildIssues (sortedAsc, baseline)

```
1. Пусто → OK

2. newest = последняя запись, prev = предпоследняя (или null)
   Одиночная запись без baseline:
   - completionPercent < 100 → WARNING
   - totalProducts == 0 && completionPercent >= 100 → WARNING ("нет товаров")
   - !ignoreStock && inStock == 0 && totalProducts > 0 → WARNING (первая запись, не ERROR)
   - иначе → OK

3. completionPercent < 100 → hasWarning = true ("выкачка не завершена")

4. 100% выкачка и всегда нет товаров → hasWarning = true

5. alwaysZeroInStock = все записи имеют inStock == 0 или null

6. Если !ignoreStock && !alwaysZeroInStock: [PRIMARY — inStock]
   refInStock = baseline.inStock ?? prev.inStock
   - newStock == 0 && refInStock > 0 → hasError = true
   - drop = (refInStock - newStock) / refInStock > dropThreshold% → hasError = true

   Если ignoreStock || alwaysZeroInStock: [FALLBACK — totalProducts]
   refTotal = baseline.totalProducts ?? prev.totalProducts
   - drop > dropThreshold% → hasError = true
   - Ошибки парсинга (prevErrors vs newErrors):
     * prevErrors == 0 && newErrors >= minAbsoluteErrors → hasWarning
     * рост > errorGrowthThreshold% && newErrors >= minAbsoluteErrors → hasWarning
   - alwaysZeroInStock && !ignoreStock && есть товары → hasWarning

7. Скорость (только если !hasError && !hasWarning && baseline.durationMinutes доступен):
   curDuration > baseline.durationMinutes * 1.5 → hasWarning

8. hasError → ERROR | hasWarning → WARNING | иначе → OK
```

### `MedianStats` record

```java
public record MedianStats(Integer inStock, Integer totalProducts, Integer durationMinutes) {}
```

Вычисляется методом `computeBaselineMedian(List<ZoomosParsingStats>)`. Медиана по каждой метрике независимо (null-записи фильтруются).

---

## Сообщения в issues (формируются внутри evaluateAndBuildIssues)

При наличии baseline — показывается `[медиана: X]` вместо значения prev:

| Ситуация | Тип | Сообщение |
|----------|-----|-----------|
| inStock упал до 0 | ERROR | `"В наличии: [медиана: 850] → 0 (−100%)"` |
| inStock упал > threshold | ERROR | `"Падение 'В наличии': [медиана: 850] → 120 (−86%)"` |
| totalProducts упал (ignoreStock) | ERROR | `"Падение товаров: [медиана: 5000] → 100 (−98%)"` |
| Рост ошибок | WARNING | `"Рост ошибок: 10 → 150 (+1400%)"` |
| Новые ошибки | WARNING | `"Ошибки парсинга: 0 → 25"` |
| 100% выкачка, нет товаров | WARNING | `"100% выкачка, нет товаров — нужна проверка"` |
| inStock всегда 0, есть товары | WARNING | `"В наличии: всегда 0 — нужна проверка"` |
| Медленная выкачка (только если нет других проблем) | WARNING | `"Медленная выкачка: N мин (базовый: M мин)"` |

**Ошибки парсинга** генерируются только когда `ignoreStock || alwaysZeroInStock` (если inStock доступен — только в тренды, не в основные issues).

---

## Тренд-анализ (TREND_WARNING)

Отдельный механизм — `evaluateTrend()` / `computeMedianBaseline()`.

- Метрики нормализованы по числу товаров: `stockRatio`, `errorRate`, `durationRate`
- `TREND_WARNING` issues отображаются в отдельном **свёрнутом** блоке "Тренды" (Блок 2.5)
- Не влияют на `canDeliver`, не попадают в блок "На что обратить внимание"
- Требует `baselineDays > 0` и не менее 3 исторических записей

---

## canDeliver

```java
boolean canDeliver = issues.stream()
    .noneMatch(i -> "ERROR".equals(i.get("type")));
```

- **ERROR** блокирует (`canDeliver = false`)
- **WARNING** — не блокирует (в т.ч. IN_PROGRESS / "нет данных в процессе")
- **TREND_WARNING** — вынесены в `trendIssues`, не проверяются

---

## noData-issues (города/адреса без данных)

Вместо статуса `NOT_FOUND` — тип `ERROR` + флаг `noData=true`.
Вместо статуса `IN_PROGRESS` — тип `WARNING` + флаг `noData=true` (включая "frozen").

`noData=true` используется для:
- Отображения иконки спиннера / ban в issues-списке (Блок 2)
- CSV-группировки в блок "НЕТ ДАННЫХ"
- Добавления заглушки в Блок 3 вместо таблицы данных
- Счётчика `liveNotFoundCount` (тайл "Нет/Идёт")

---

## Парсинг URL и авторизация

```
{baseUrl}/shops-parser/{site}/parsing-history
  ?upd={ts}&dateFrom=...&dateTo=...&shop={shopParam}&onlyFinished=1
```

- **API-тип**: `shop=-`, фильтр по пустому полю "Клиент"
- **ITEM-тип**: `shop={shopName}`
- Куки (`ZoomosSession`), автообновление при редиректе на `/login`
- `cityId` из строки `"3509 - Вологда"` → `"3509"` для прямых ссылок

### Фильтрация по времени

- Нижняя граница: `startTime >= rangeStart`
- Верхняя граница: `finishTime <= rangeEnd` (null finishTime → `startTime`)
- Реализация: `ZoomosCheckService.filterByTime()`

---

## Страница результатов (check-results.html)

4 блока:

| Блок | ID | Описание |
|------|----|----------|
| 1 | — | Вердикт (`canDeliver`), счётчики OK/Warn/Error/Нет-Идёт |
| 2 | `issuesCollapse` | "На что обратить внимание" — ERROR + WARNING (без TREND), авто-раскрыт если ≤ 2 сайта |
| 2.5 | `trendsCollapse` | Тренды — TREND_WARNING (свёрнут по умолчанию) |
| 3 | `groups-section` | Детали по сайтам — **lazy-loaded** через `GET /check/results/{runId}/groups` → фрагмент `check-results-groups.html :: groupsBlock` |

Данные модели (`checkResults()`):
- `issues` — mainIssues (без TREND_WARNING)
- `trendIssues` — только TREND_WARNING
- `canDeliver`, `liveOkCount`, `liveWarnCount`, `liveErrCount`, `liveNotFoundCount`
- `groups` НЕ передаётся в основной странице — только в `checkResultsGroups()`

**Lazy loading БЛОКА 3**:

- При клике "Показать детали" → `loadGroups()` делает `fetch('/check/results/{runId}/groups')`
- Кнопка ↓ в БЛОКЕ 2 → `scrollToGroup(siteName)` сначала вызывает `loadGroups()`, затем скроллит
- После загрузки: `initGroupsJs()` инициализирует chevron, expandAll/collapseAll, Redmine batch-check

**N+1 SQL оптимизация** (NOT_FOUND detection):

- `findLatestFinishedBySiteAndAddressIds()` — batch PostgreSQL `DISTINCT ON` по всем адресам
- `findLatestFinishedBySites()` — batch по всем сайтам/городам
- Результат: N+1 → 2 SQL запроса

---

## Приоритетные сайты

- Toggle: `POST /zoomos/sites/{id}/priority`
- Глобальный баннер: fetch `/zoomos/api/priority-alerts` при загрузке любой страницы
- В issues: `isPriority=true` → звёздочка, всплывает первым в сортировке

---

## Расписание (ZoomosSchedulerService)

- `ThreadPoolTaskScheduler` — bean `zoomosSchedulerTaskScheduler`, 3 потока
- Cron: Unix 5 полей → Spring 6 полей (добавляется `"0 "` в начало)
- `dateOffsetFrom` default −1 (вчера), `dateOffsetTo` default 0 (сегодня)
- Хранится в `zoomos_shop_schedules`

---

## Redmine интеграция

Создание/обновление задач в `tt.zoomos.by` со страницы результатов. Одна задача на домен.

**Особенность сервера**: HTTP 404 при POST/PUT, но операции выполняются успешно.
Workaround: `postIgnoring404()` / `putIgnoring404()` + поиск через `findRecentIssueBySubject()`.

Эндпоинты: `/zoomos/redmine/check-batch`, `/zoomos/redmine/check`, `/zoomos/redmine/create`, `/zoomos/redmine/update/{id}`, `DELETE /zoomos/redmine/local-delete/{site}`

**Auto-save**: `findIssuesBySite()` автоматически сохраняет первую найденную задачу в `zoomos_redmine_issues` — последующие загрузки страницы сразу показывают кнопку "Изменить" без async API-запроса.

**UI (Block 2 — check-results.html)**:
- Коллапс по сайту: `issuesBySite` (LinkedHashMap), `errorCountBySite`/`warnCountBySite` передаются в модель
- Кнопка Redmine одна на сайт (`btn-redmine-site` / `btn-redmine-site-edit`). Коллапс сайта управляется через `data-collapse-target` + ручной JS-toggle в `site-issues-header.addEventListener('click')`, который игнорирует клики в `.ms-auto` и `.btn-mark-site-done`
- Phase 1 выбор проблем: `#rmIssueSelect` → чекбоксы → `buildSiteDescription()` с collapse-тегами. Показывается и при редактировании (>1 issues): выбранные проблемы попадают в `rmNotes` (комментарий), а не в описание
- Кнопка «Проверено» (`btn-mark-site-done`) в заголовке сайта — скрывает все проблемы сайта разом; JS `e.stopPropagation()` не даёт сворачиваться коллапсу
- Verified-badge: localStorage `verified-sites-{runId}`, отображается иконка ✓ в заголовке сайта
- Блок «Проверено мной» (`#verifiedSitesBlock`): verified-сайты физически перемещаются в отдельный collapsible-блок через `moveSiteToVerified()` (работает при первой загрузке и при нажатии кнопки). Блок скрыт пока нет verified-сайтов. Восстановление из localStorage происходит ДО авто-раскрытия, чтобы verified-сайты не раскрывались в основном списке
- Фикс: `btnClass` для Redmine-кнопки — `isClosed ? 'btn-success' : 'btn-danger'` (был инвертирован)
- Фикс: `currentSelectedIssues` из Phase 1 скрываются через `hideIssue()` после успешного создания задачи
- Фикс: `showCopyBlock` при update передаёт `body.shortMessage` вместо литерала 'обновлена'
- Кнопка "Статусы Redmine": `#btnRefreshRedmine` → `runBatchCheck()` (отложен через `setTimeout` для быстрого рендера страницы)
- Удалённая задача в Redmine: batch-check вызывает `DELETE /local-delete/{site}` + откатывает кнопку
- `data-historyurl` и `data-matchingurl` убраны из DOM (раньше замедляли страницу); URL вычисляются в JS функциями `computeHistoryUrl()` / `computeMatchingUrl()` через глобальные переменные `BASE_URL`, `DATE_FROM`, `DATE_TO`, `SHOP_NAME`
- Городское название (`cityDisplay`): в `buildGroupIssues` хранится только читаемая часть после " - " (null если ID без имени)

Конфиг: `redmine.base-url`, `redmine.api-key`, `redmine.project-id`, и т.д. в `application.properties`.

---

## Ключевые типы и DTO

| Класс | Описание |
|-------|---------|
| `CheckRunStatus` | enum RUNNING / COMPLETED / FAILED — хранится как STRING в `zoomos_check_runs.status` |
| `ZoomosCheckType` | enum API / ITEM — хранится как STRING в `zoomos_parsing_stats.check_type` |
| `ZoomosCheckParams` | @Value @Builder DTO — заменяет 12-параметровую сигнатуру `runCheck()` |
| `AddressFilterContext` | private record внутри `ZoomosCheckService` — контекст фильтрации city/address |

**Запуск проверки** через `checkService.runCheck(ZoomosCheckParams.builder()...build())` — из контроллера и планировщика.

---

## Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `ZoomosCheckService.java` | Playwright-парсинг, `evaluateAndBuildIssues()`, `computeBaselineMedian()`, `filterByTime()`, WebSocket |
| `ZoomosAnalysisController.java` | `/zoomos/*` роуты, `checkResults()`, schedule CRUD, priority API |
| `ZoomosParserService.java` | Магазины и city_ids |
| `ZoomosSchedulerService.java` | Cron-расписания |
| `ZoomosKnownSite.java` | `@Table zoomos_sites`, поля `isPriority`, `ignoreStock` |
| `ZoomosSettingsService.java` | Глобальные настройки (таблица `zoomos_settings`, key-value) |
| `RedmineService.java` | Вся бизнес-логика Redmine |
| `ZoomosRedmineController.java` | REST endpoints `/zoomos/redmine/*` |
| `check-results.html` | Страница результатов (4 блока + тренды) |
| `layout/main.html` | Глобальный priority-alerts баннер |
