# План: 3 фичи для Zoomos Check — настройки сайтов

**Статус:** 🔄 Задача 3 реализована
**Ветка:** `feature/master-city-id`  
**Последнее обновление:** 2026-04-02

## Контекст
Три взаимосвязанные доработки справочника сайтов Zoomos Check. Затрагивают `ZoomosKnownSite` (справочник), `ZoomosCityId` (клиент→сайт), логику проверки и UI страниц `/zoomos/sites`, `/zoomos` (index), `check-results`.

---

## Задача 1: Флаг конфигурационной проблемы (уровень клиент→сайт)

Привязка по ключу **клиент + сайт** (`ZoomosCityId`). Один и тот же сайт может работать у одного клиента нормально, а у другого — иметь устаревшие ссылки. Флаг хранится в `zoomos_city_ids`.

- [ ] **1.1 — Миграция V52**  
  `src/main/resources/db/migration/V52__add_config_issue_to_city_ids.sql`
  ```sql
  ALTER TABLE zoomos_city_ids
      ADD COLUMN has_config_issue BOOLEAN NOT NULL DEFAULT FALSE,
      ADD COLUMN config_issue_note TEXT;
  ```

- [ ] **1.2 — Entity `ZoomosCityId.java`**  
  Добавить два поля:
  ```java
  @Column(name = "has_config_issue", nullable = false)
  @Builder.Default
  private boolean hasConfigIssue = false;

  @Column(name = "config_issue_note", columnDefinition = "TEXT")
  private String configIssueNote;
  ```

- [ ] **1.3 — Endpoint в `ZoomosAnalysisController.java`**
  ```
  POST /zoomos/city-ids/{id}/config-issue
    @RequestParam(required=false) String note
    → cityId.setHasConfigIssue(note != null && !note.isBlank());
    → cityId.setConfigIssueNote(note);
    → return { success: true, hasConfigIssue }
  ```

- [ ] **1.4 — UI `zoomos/index.html`** (страница управления сайтами клиента)  
  В строке каждого `ZoomosCityId` — кнопка-toggle с иконкой `fa-wrench`.  
  При `hasConfigIssue=true` → кнопка подсвечена (bg-warning), tooltip с `configIssueNote`.  
  Клик → popover/modal для ввода/очистки заметки + AJAX сохранение.

- [ ] **1.5 — UI `zoomos/check-results.html`**  
  В `checkResults()` добавить в модель `configIssueByShopSite: Map<siteName, note>` (allCityIds уже отфильтрованы по shop):
  ```java
  Map<String, String> configIssueByShopSite = allCityIds.stream()
      .filter(ZoomosCityId::isHasConfigIssue)
      .collect(Collectors.toMap(ZoomosCityId::getSiteName,
          c -> c.getConfigIssueNote() != null ? c.getConfigIssueNote() : ""));
  model.addAttribute("configIssueByShopSite", configIssueByShopSite);
  ```
  В шаблоне рядом с именем сайта (строка ~188, после мастер-города):
  ```html
  <span th:if="${configIssueByShopSite != null and configIssueByShopSite[entry.key] != null}"
        class="badge bg-warning text-dark ms-1"
        th:title="${'Конфиг. проблема: ' + configIssueByShopSite[entry.key]}">
      <i class="fas fa-wrench"></i> Конфиг
  </span>
  ```

---

## Задача 2: Перенос master_city_id с уровня клиента на уровень сайта

Сейчас `master_city_id` в `zoomos_city_ids` (per-клиент). Нужно перенести в `zoomos_sites` (per-сайт).

- [ ] **2.1 — Диагностика конфликтов (выполнить вручную перед V53)**
  ```sql
  SELECT site_name, COUNT(DISTINCT master_city_id) AS variants,
         STRING_AGG(DISTINCT master_city_id, ', ') AS values
  FROM zoomos_city_ids
  WHERE master_city_id IS NOT NULL AND master_city_id <> ''
  GROUP BY site_name
  HAVING COUNT(DISTINCT master_city_id) > 1;
  ```
  Если есть конфликты — сообщить пользователю до создания миграции.

- [ ] **2.2 — Миграция V53**  
  `src/main/resources/db/migration/V53__move_master_city_id_to_sites.sql`
  ```sql
  ALTER TABLE zoomos_sites ADD COLUMN master_city_id VARCHAR(50) NULL;

  UPDATE zoomos_sites s SET master_city_id = (
      SELECT master_city_id FROM zoomos_city_ids c
      WHERE c.site_name = s.site_name
        AND c.master_city_id IS NOT NULL AND c.master_city_id <> ''
      ORDER BY c.id LIMIT 1
  )
  WHERE EXISTS (
      SELECT 1 FROM zoomos_city_ids c
      WHERE c.site_name = s.site_name
        AND c.master_city_id IS NOT NULL AND c.master_city_id <> ''
  );

  COMMENT ON COLUMN zoomos_city_ids.master_city_id IS
    'DEPRECATED с V53: перенесено в zoomos_sites.master_city_id';
  ```

- [ ] **2.3 — Entity `ZoomosKnownSite.java`**
  ```java
  @Column(name = "master_city_id", length = 50)
  private String masterCityId;
  ```

- [ ] **2.4 — `ZoomosCheckService.java` (строки ~440-461)**  
  Перед обходом entries добавить override из `ZoomosKnownSite`:
  ```java
  Map<String, String> masterCityBySiteName = knownSiteRepository.findAll().stream()
      .filter(s -> s.getMasterCityId() != null && !s.getMasterCityId().isBlank())
      .collect(Collectors.toMap(ZoomosKnownSite::getSiteName, ZoomosKnownSite::getMasterCityId));
  entries.forEach(e -> {
      String siteMaster = masterCityBySiteName.get(e.getSiteName());
      if (siteMaster != null) e.setMasterCityId(siteMaster);
  });
  ```
  `buildAddressFilterContext` не меняется — читает `entry.getMasterCityId()` как раньше.

- [ ] **2.5 — `ZoomosAnalysisController.java` — `masterCityBySite`**  
  Строки 686-691 в `checkResults()` и аналогично в `checkResultsGroup()`:
  ```java
  Map<String, String> masterCityBySite = knownSiteRepository.findAllByMasterCityIdNotNull()
      .stream().collect(Collectors.toMap(ZoomosKnownSite::getSiteName, ZoomosKnownSite::getMasterCityId));
  ```

- [ ] **2.6 — Новый endpoint смены master city**
  ```
  POST /zoomos/sites/{id}/master-city
    @RequestParam(required=false) String masterCityId
    → site.setMasterCityId(masterCityId); knownSiteRepository.save(site);
    → return { success: true }
  ```
  Старый `POST /city-ids/{id}/master-city` — оставить как deprecated-редирект на `ZoomosKnownSite`.

- [ ] **2.7 — UI `zoomos/index.html`**  
  Убрать `.master-city-input` из строк `ZoomosCityId`. Добавить inline-инпут на уровне сайта, POST-ит на `/sites/{id}/master-city`.

- [ ] **2.8 — UI `zoomos/sites.html`**  
  Добавить колонку "Мастер-город" с inline-редактируемым `<input>`.

- [ ] **2.9 — `ZoomosKnownSiteRepository.java`**  
  Добавить: `List<ZoomosKnownSite> findAllByMasterCityIdNotNull();`

---

## Задача 3: Парсинг CITIES_EQUAL_PRICES

Страница настроек: `https://export.zoomos.by/shops-parser/{siteName}/settings`.  
`CITIES_EQUAL_PRICES=1` → цены одинаковы во всех городах → достаточно одного города.  
Парсинг только по кнопке (не при каждой проверке).

- [x] **3.1 — Миграция V52** (V52, т.к. V52/V53 ещё не созданы)
  `src/main/resources/db/migration/V52__add_cities_equal_prices_to_sites.sql`
  ```sql
  ALTER TABLE zoomos_sites
      ADD COLUMN cities_equal_prices BOOLEAN,
      ADD COLUMN cities_equal_prices_checked_at TIMESTAMPTZ;
  ```

- [x] **3.2 — Entity `ZoomosKnownSite.java`**
  ```java
  @Column(name = "cities_equal_prices")
  private Boolean citiesEqualPrices;  // null = ещё не проверялось

  @Column(name = "cities_equal_prices_checked_at")
  private ZonedDateTime citiesEqualPricesCheckedAt;
  ```

- [x] **3.3 — Метод `fetchCitiesEqualPrices(String siteName)` в `ZoomosParserService.java`**
  Паттерн: тот же Playwright что в `parseApiPage()`:
  1. `page.navigate("…/shops-parser/{siteName}/settings?upd=…")`
  2. `page.waitForLoadState(NETWORKIDLE)`
  3. JS evaluate: найти строку с `CITIES_EQUAL_PRICES` в таблице настроек, вернуть значение
  4. Сохранить в `ZoomosKnownSite.citiesEqualPrices` + `citiesEqualPricesCheckedAt = now()`
  5. Если `citiesEqualPrices=true` И `masterCityId == null` → вернуть `suggestMasterCity:true` + список исторических городов

  Batch-метод `fetchCitiesEqualPricesForAll()` — один Playwright-контекст на все сайты, запускать через `zoomosCheckExecutor`.

- [x] **3.4 — `ZoomosParsingStatsRepository.java`**
  Добавить:
  ```java
  @Query("SELECT DISTINCT s.cityName FROM ZoomosParsingStats s WHERE s.siteName = :siteName AND s.cityName IS NOT NULL ORDER BY s.cityName")
  List<String> findDistinctCityNamesBySiteName(@Param("siteName") String siteName);
  ```

- [x] **3.5 — Endpoints в `ZoomosAnalysisController.java`**
  ```
  POST /zoomos/sites/{id}/fetch-equal-prices
    → parserService.fetchCitiesEqualPrices(siteName)
    → return { success, citiesEqualPrices, suggestMasterCity?, historicalCities? }

  POST /zoomos/sites/fetch-equal-prices-all
    → async через zoomosCheckExecutor
    → return { success, message: "Запущено в фоне" }

  GET /zoomos/sites/{id}/historical-cities
    → return List<String>
  ```

- [x] **3.6 — UI `zoomos/sites.html`**
  Колонка "Равные цены": кнопка `fa-sync-alt`, после проверки бейдж `=1` (зелёный) / `=0` (серый) + дата.  
  Кнопка "Проверить все" в шапке.  
  Модальное окно: если `citiesEqualPrices=1` и `masterCityId` не задан → список исторических городов для выбора.

- [x] **3.7 — UI `zoomos/check-results.html`**
  В модель добавить `equalPricesBySite: Map<siteName, Boolean>` и `siteIdByName: Map<siteName, Long>`.  
  Рядом с именем сайта (строка ~192): кнопка `fa-cog` + бейдж "= цены" / "разные цены".

---

## Порядок реализации

1. [ ] Задача 1 (флаг конфиг-проблемы) — V52, простой CRUD
2. [ ] Диагностика конфликтов (шаг 2.1) — SELECT вручную
3. [ ] Задача 2 (перенос master_city_id) — V53 + логика + UI
4. [ ] Задача 3 (CITIES_EQUAL_PRICES) — V54 + парсер + UI

---

## Верификация

- [ ] `mvn spring-boot:run -Dspring-boot.run.profiles=silent` — чистый запуск
- [ ] `/zoomos/sites` — колонки: мастер-город, равные цены
- [ ] `/zoomos` (index) — флаг конфиг-проблемы в строках CityId, мастер-город на уровне сайта
- [ ] Кнопка "Проверить настройки" для сайта → парсинг отрабатывает, значение сохраняется
- [ ] `check-results` → бейдж конфиг-проблемы и кнопка настроек отображаются
- [ ] `psql -d zoomos_v4 -c "SELECT site_name, master_city_id, cities_equal_prices FROM zoomos_sites LIMIT 5;"`
- [ ] `psql -d zoomos_v4 -c "SELECT id, site_name, has_config_issue FROM zoomos_city_ids WHERE has_config_issue LIMIT 5;"`
