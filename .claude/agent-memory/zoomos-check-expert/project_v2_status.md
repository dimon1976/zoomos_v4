---
name: check-results-v2 feature gap vs v1
description: Список функций которые есть в check-results.html (v1) но отсутствуют в check-results-v2.html
type: project
---

check-results-v2 — страница "Новый вид (бета)". Контроллер: ZoomosAnalysisController:1236 `/check/results-v2/{runId}`.

**Отсутствует в v2 (есть в v1):**
1. Блок деталей по сайтам (lazy-load `/check/results/{runId}/groups`) — в v2 нет вообще
2. Кнопка "= цены" (CITIES_EQUAL_PRICES) — fetchEqPricesForSite, fetchAllEqPrices, eqPricesModal
3. Кнопка "Сайт проверен" (btn-mark-site-done) — перенос целого сайта в "Проверено мной"
4. Блок "Проверено мной" (verifiedSitesBlock/verifiedSitesCollapse) с кнопкой "Сбросить всё"
5. Кнопки копирования "Ненастроенные" (copyNotConfiguredSites) и "Не выкачка" (copyConfigIssues)
6. Кнопка "Статусы Redmine" (btnRefreshRedmine) — принудительное обновление статусов
7. Кнопка "Перейти к деталям" (btn-go-detail) — скролл к блоку деталей по сайту
8. Счётчик "Нет/Идёт" (liveNotFoundCount badge) в шапке
9. Таймаут-алерт (run.timeoutCount)
10. Ссылка на настройку сайта (badge-not-configured с href на /shops-parser/{site}/settings)
11. localStorage ключ для v2: `v2_hidden_issues_{runId}` (в v1: `hidden-issues-{runId}`)

**Why:** v2 создавалась как упрощённый иерархический вид, намеренно без блока деталей. Планируется дополнение.

**How to apply:** При добавлении функций в v2 — не добавлять блок деталей (это осознанное решение). Остальные фичи можно портировать по требованию пользователя.
