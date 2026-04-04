# План: 3 фичи для Zoomos Check — настройки сайтов

**Статус:** ✅ Все задачи реализованы
**Ветка:** `feature/master-city-id`
**Последнее обновление:** 2026-04-04

## Контекст
Три взаимосвязанные доработки справочника сайтов Zoomos Check. Затрагивают `ZoomosKnownSite` (справочник), `ZoomosCityId` (клиент→сайт), логику проверки и UI страниц `/zoomos/sites`, `/zoomos` (index), `check-results`.

**Порядок реализации по факту:** Задача 3 → Задача 2 → Задача 1 (из-за нумерации миграций).

---

## Задача 1: Флаг конфигурационной проблемы (уровень клиент→сайт)

Привязка по ключу **клиент + сайт** (`ZoomosCityId`). Флаг хранится в `zoomos_city_ids`.

- [x] **1.1 — Миграция V54** (фактический номер, V52/V53 заняты под задачи 3/2)
  `src/main/resources/db/migration/V54__add_config_issue_to_city_ids.sql`
  ```sql
  ALTER TABLE zoomos_city_ids
      ADD COLUMN has_config_issue BOOLEAN NOT NULL DEFAULT FALSE,
      ADD COLUMN config_issue_note TEXT;
  ```

- [x] **1.2 — Entity `ZoomosCityId.java`**
  Добавлены два поля:
  ```java
  @Column(name = "has_config_issue", nullable = false)
  @Builder.Default
  private boolean hasConfigIssue = false;

  @Column(name = "config_issue_note", columnDefinition = "TEXT")
  private String configIssueNote;
  ```

- [x] **1.3 — Endpoint `POST /zoomos/city-ids/{id}/config-issue`** в `ZoomosAnalysisController.java`
  `@RequestParam(required=false) String note` → устанавливает/снимает флаг, возвращает `{ success, hasConfigIssue }`.

- [x] **1.4 — UI `zoomos/index.html`**
  В строке каждого `ZoomosCityId` — кнопка `fa-wrench`.
  При `hasConfigIssue=true` → `bg-warning border-warning`, tooltip с заметкой.
  Клик → модальное окно с `<textarea>` для ввода/очистки заметки + AJAX сохранение.
  Кнопки «Сохранить» и «Снять флаг» → `POST /city-ids/{id}/config-issue`.

- [x] **1.5 — UI `zoomos/check-results.html`**
  В `checkResults()` в модель добавлен `configIssueByShopSite: Map<siteName, note>`.
  В шаблоне рядом с именем сайта — бейдж `bg-warning fa-wrench Конфиг` с tooltip.

---

## Задача 2: Перенос master_city_id с уровня клиента на уровень сайта

- [x] **2.1 — Диагностика конфликтов** — миграция V53 берёт `ORDER BY id LIMIT 1` при конфликтах.

- [x] **2.2 — Миграция V53**
  `src/main/resources/db/migration/V53__move_master_city_id_to_sites.sql`
  Добавлен столбец `master_city_id VARCHAR(50)` в `zoomos_sites`, скопированы данные из `zoomos_city_ids`.
  Старый столбец помечен `DEPRECATED с V53`.

- [x] **2.3 — Entity `ZoomosKnownSite.java`**
  Добавлено поле `masterCityId` (`length=50`).

- [x] **2.4 — `ZoomosCheckService.java` — `buildAddressFilterContext`**
  Загружает site-level override из `knownSiteRepository.findAllByMasterCityIdNotNull()`,
  применяет через `siteMasterOverride.getOrDefault(entry.getSiteName(), entry.getMasterCityId())`.

- [x] **2.5 — `ZoomosAnalysisController.java` — `masterCityBySite`**
  В `checkResults()` и `checkResultsGroups()` — карта из `knownSiteRepository.findAllByMasterCityIdNotNull()`.

- [x] **2.5a — Bugfix: NOT_FOUND и COPIED циклы** в `checkResults()` и `checkResultsGroups()`
  Использовали `cid.getMasterCityId()` напрямую после переноса данных в `zoomos_sites`.
  Исправлено: `masterCityBySite.getOrDefault(site, cid.getMasterCityId())` во всех трёх местах.

- [x] **2.6 — Endpoint `POST /zoomos/sites/{id}/master-city`**
  Сохраняет в `ZoomosKnownSite`. Старый `POST /city-ids/{id}/master-city` каскадит на сайт через `parserService.updateMasterCityId()`.

- [x] **2.7 — UI `zoomos/index.html`**
  `master-city-input` читает из `siteMasterCityMap[entry.siteName]`, постит на `/sites/{siteId}/master-city`.

- [x] **2.8 — UI `zoomos/sites.html`**
  Колонка «Мастер-город» с inline-редактируемым `<input>`.

- [x] **2.9 — `ZoomosKnownSiteRepository.java`**
  Добавлен метод `findAllByMasterCityIdNotNull()`.

---

## Задача 3: Парсинг CITIES_EQUAL_PRICES

- [x] **3.1 — Миграция V52**
  `src/main/resources/db/migration/V52__add_cities_equal_prices_to_sites.sql`
  Добавлены `cities_equal_prices BOOLEAN` и `cities_equal_prices_checked_at TIMESTAMPTZ` в `zoomos_sites`.

- [x] **3.2 — Entity `ZoomosKnownSite.java`**
  Добавлены `citiesEqualPrices` (Boolean, null = не проверялось) и `citiesEqualPricesCheckedAt`.

- [x] **3.3 — `ZoomosParserService.java`**
  Метод `fetchCitiesEqualPrices(siteName)` — Playwright парсит `/shops-parser/{siteName}/settings`,
  JS-скрипт ищет строку `CITIES_EQUAL_PRICES` в таблице (`cells[1]` — название, `cells[3]` — значение).
  Сохраняет в `ZoomosKnownSite`. Если `=1` и `masterCityId == null` → возвращает `suggestMasterCity: true`.
  Batch-метод `fetchCitiesEqualPricesForAll()` — один браузер на все сайты.

- [x] **3.4 — `ZoomosParsingStatsRepository.java`**
  Добавлен `findDistinctCityNamesBySiteName(siteName)` — исторические города сайта.

- [x] **3.5 — Endpoints в `ZoomosAnalysisController.java`**
  - `POST /zoomos/sites/{id}/fetch-equal-prices`
  - `POST /zoomos/sites/fetch-equal-prices-all` (async)
  - `GET /zoomos/sites/{id}/historical-cities`

- [x] **3.6 — UI `zoomos/sites.html`**
  Колонки «Мастер-город» и «Равные цены» (кнопка `fa-magnifying-glass`, бейдж `= цены`/`разные цены` + дата).
  Кнопка «Проверить все» в шапке. Модальное окно с историческими городами.
  Иконки исправлены для FA6 Free: `far fa-star` → `fas fa-star` (opacity 0.35 для неактивных),
  `far fa-eye` → `fas fa-eye` (opacity 0.35 для неактивных).

- [x] **3.7 — UI `zoomos/check-results.html`**
  В модель добавлены `equalPricesBySite` и `siteIdByName`.
  Бейдж `= цены`/`разные цены` рядом с именем сайта.
  Исправлен цвет текста: `badge bg-danger` → `badge bg-danger text-white`.

---

## Исправленные проблемы (в ходе реализации)

### Баг 1: CITIES_EQUAL_PRICES не парсился (Задача 3)
**Проблема:** JS-скрипт в `ZoomosParserService` использовал `cells[0]` для проверки названия параметра
и `cells[1]` для значения — но реальная DOM-структура таблицы настроек другая:
- `cells[0]` — пустая ячейка 8px
- `cells[1]` — название параметра (`<b>CITIES_EQUAL_PRICES</b>`)
- `cells[2]` — тип (select)
- `cells[3]` — значение (input)

**Исправление:** `cells[0]→cells[1]` для проверки названия, `cells[1]→cells[3]` для чтения значения.

---

### Баг 2: NOT_FOUND и COPIED циклы игнорировали site-level master_city_id (Задача 2)
**Проблема:** После переноса `master_city_id` из `zoomos_city_ids` в `zoomos_sites` (миграция V53),
NOT_FOUND-проверки в `checkResults()` и `checkResultsGroups()` по-прежнему читали
`cid.getMasterCityId()` из `ZoomosCityId` (устаревшее поле). В результате, если мастер-город
был задан через новый UI (записывается в `ZoomosKnownSite`), все остальные города сайта
продолжали генерировать NOT_FOUND ошибки.

**Затронутые места (3 штуки):**
- `checkResults()` — NOT_FOUND цикл по `allCityIds` (строка ~864)
- `checkResultsGroups()` — NOT_FOUND цикл (строка ~1330)
- `checkResultsGroups()` — COPIED секция (строка ~1393)

**Исправление:** `cid.getMasterCityId()` → `masterCityBySite.getOrDefault(site, cid.getMasterCityId())`
во всех трёх местах. `masterCityBySite` уже был в scope — загружался из `knownSiteRepository.findAllByMasterCityIdNotNull()`.

---

### Проблема 3: Иконки выглядели некорректно в Tabler / FA6 Free (Задача 3, sites.html)
**Проблема:** Использовались `far fa-star` и `far fa-eye` (Regular стиль Font Awesome),
которые **недоступны** в бесплатной версии FA6. Браузер отображал пустые квадраты.
Также `fa-sync-alt` переименован в `fa-arrows-rotate` в FA6.

**Исправление:**
- `far fa-star` → `fas fa-star` + `style="opacity:0.35"` для неактивного состояния
- `far fa-eye` → `fas fa-eye` + `style="opacity:0.35"` для неактивного состояния
- Кнопка «Проверить равные цены»: `fa-arrows-rotate` → `fa-magnifying-glass` (чтобы не путать с toggle-кнопками)
- JS обновлён аналогично

---

### Проблема 4: Белый текст на красном фоне в check-results.html
**Проблема:** В теме Tabler `badge bg-danger` не наследует `color: white` автоматически —
в отличие от Bootstrap по умолчанию. Текст на красных бейджах был нечитаем.

**Исправление:** `badge bg-danger` → `badge bg-danger text-white` в трёх местах
(HTML-шаблон + 2 места в JS где `badge.className` задаётся программно).

---

## Верификация

- [x] Компиляция: `mvn compile` — без ошибок
- [ ] `mvn spring-boot:run -Dspring-boot.run.profiles=silent` — чистый запуск (проверить Flyway V52–V54)
- [ ] `/zoomos/sites` — колонки: мастер-город, равные цены
- [ ] `/zoomos` (index) — кнопка-wrench в строках CityId, мастер-город на уровне сайта
- [ ] Кнопка «Проверить настройки» → парсинг отрабатывает, значение сохраняется
- [ ] `check-results` → бейдж конфиг-проблемы и бейдж равных цен отображаются
- [ ] `psql -d zoomos_v4 -c "SELECT site_name, master_city_id, cities_equal_prices FROM zoomos_sites LIMIT 5;"`
- [ ] `psql -d zoomos_v4 -c "SELECT id, site_name, has_config_issue FROM zoomos_city_ids WHERE has_config_issue LIMIT 5;"`
