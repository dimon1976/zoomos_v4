# План: Серверное хранение отметок "сайт проверен" / "ошибка проверена"

**Статус:** ⏳ Не начато  
**Ветка:** `feature/master-city-id`  
**Последнее обновление:** 2026-04-02

## Контекст
Отметки "сайт проверен" и "ошибка проверена" на странице check-results хранились **только в localStorage браузера** — теряются при очистке кэша, переходе на другой браузер/машину. Нужно перенести хранение в БД с сохранением localStorage как локального кэша (для быстродействия).

## Архитектура решения

Добавить два TEXT-поля в `zoomos_check_runs` (хранить как JSON):
- `verified_issues` — JSON-массив ключей вида `site|city|addressId|type|message`  
- `verified_sites` — JSON-массив имён сайтов

Логика на клиенте: **localStorage как кэш**, сервер как источник истины.  
При загрузке страницы — данные приходят из модели (Thymeleaf), не из localStorage.  
При каждом изменении — debounced AJAX-сохранение на сервер + синхронное обновление localStorage.

---

## Шаги реализации

- [ ] **1 — Миграция V52** _(или следующий свободный номер)_  
  `src/main/resources/db/migration/V52__add_verified_marks_to_check_runs.sql`
  ```sql
  ALTER TABLE zoomos_check_runs
      ADD COLUMN verified_issues TEXT,
      ADD COLUMN verified_sites   TEXT;

  COMMENT ON COLUMN zoomos_check_runs.verified_issues IS
    'JSON-массив ключей проверенных ошибок: ["site|city|addrId|type|msg", ...]';
  COMMENT ON COLUMN zoomos_check_runs.verified_sites IS
    'JSON-массив сайтов, полностью помеченных как проверенные: ["site1.ru", ...]';
  ```
  > Примечание: если V52 уже занят планом site-settings — использовать следующий свободный номер.

- [ ] **2 — Entity `ZoomosCheckRun.java`**  
  Добавить два поля:
  ```java
  @Column(name = "verified_issues", columnDefinition = "TEXT")
  private String verifiedIssues;   // хранится как JSON

  @Column(name = "verified_sites", columnDefinition = "TEXT")
  private String verifiedSites;    // хранится как JSON
  ```

- [ ] **3 — Endpoint сохранения в `ZoomosAnalysisController.java`**
  ```
  POST /zoomos/check/results/{runId}/marks
    @RequestBody Map<String, List<String>> body
      { "issues": [...], "sites": [...] }
    → run.setVerifiedIssues(toJson(body.get("issues")));
    → run.setVerifiedSites(toJson(body.get("sites")));
    → checkRunRepository.save(run);
    → return { success: true }
  ```
  Вспомогательный метод `toJson(List)` — использовать `ObjectMapper` (уже в контексте).

- [ ] **4 — Передача данных в шаблон**  
  В `checkResults()` контроллера добавить в модель (рядом со строкой ~1093):
  ```java
  model.addAttribute("verifiedIssuesJson",
      run.getVerifiedIssues() != null ? run.getVerifiedIssues() : "[]");
  model.addAttribute("verifiedSitesJson",
      run.getVerifiedSites() != null ? run.getVerifiedSites() : "[]");
  ```

- [ ] **5 — Обновление JS в `check-results.html`**

  **5.1 — Инициализация (строки 763-768):**  
  Вместо чтения только из localStorage — приоритет серверным данным:
  ```javascript
  // Серверные данные (из Thymeleaf)
  const serverIssues = /*[[${verifiedIssuesJson}]]*/ '[]';
  const serverSites  = /*[[${verifiedSitesJson}]]*/ '[]';

  // Инициализировать из сервера (приоритет), localStorage — только фоллбэк
  let hiddenIssues = new Set(
      JSON.parse(serverIssues).length > 0
          ? JSON.parse(serverIssues)
          : JSON.parse(localStorage.getItem(LS_KEY) || '[]')
  );
  ```

  **5.2 — Добавить debounced сохранение на сервер:**
  ```javascript
  let saveTimer = null;
  function scheduleSaveToServer() {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(saveToServer, 1500);  // debounce 1.5s
  }

  function saveToServer() {
      const verifiedSites = JSON.parse(localStorage.getItem(VERIFIED_KEY) || '[]');
      fetch('/zoomos/check/results/' + RUN_ID + '/marks', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
              issues: [...hiddenIssues],
              sites: verifiedSites
          })
      });
      // Ошибки сети — тихо игнорировать (данные в localStorage всё равно есть)
  }
  ```

  **5.3 — Вызывать `scheduleSaveToServer()` в `saveHidden()`:**
  ```javascript
  function saveHidden() {
      localStorage.setItem(LS_KEY, JSON.stringify([...hiddenIssues]));
      scheduleSaveToServer();  // ← добавить
  }
  ```

  **5.4 — Вызывать `scheduleSaveToServer()` в `markSiteVerified()` и `unmarkSiteVerified()`:**
  ```javascript
  function markSiteVerified(site) {
      const verified = JSON.parse(localStorage.getItem(VERIFIED_KEY) || '[]');
      if (!verified.includes(site)) {
          verified.push(site);
          localStorage.setItem(VERIFIED_KEY, JSON.stringify(verified));
          scheduleSaveToServer();  // ← добавить
      }
      // ... остальной код без изменений
  }
  ```

---

## Порядок реализации

1. [ ] Миграция V52 (или следующий номер) — добавить колонки
2. [ ] Entity `ZoomosCheckRun.java` — два поля
3. [ ] Endpoint `POST /marks` в контроллере
4. [ ] Передача данных в модель Thymeleaf
5. [ ] JS: инициализация из серверных данных
6. [ ] JS: debounced сохранение на сервер при каждом изменении

---

## Верификация

- [ ] Открыть check-results, пометить сайт/ошибку → подождать 2 сек
- [ ] `psql -d zoomos_v4 -c "SELECT id, verified_sites FROM zoomos_check_runs ORDER BY id DESC LIMIT 3;"`  
  → должен появиться JSON с именем сайта
- [ ] Обновить страницу → отметки должны восстановиться (из сервера, не localStorage)
- [ ] Очистить localStorage вручную (DevTools → Application → Storage → Clear) → обновить → отметки должны остаться
- [ ] Открыть ту же страницу в другом браузере → отметки должны присутствовать
