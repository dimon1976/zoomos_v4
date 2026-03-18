---
name: feature-delivery
description: "Plan and execute medium-size feature work in Zoomos v4 that touches controller + service + template + async/status flow. Use when Claude Code needs a repeatable delivery checklist for imports, exports, maintenance tools, Zoomos pages, or other cross-layer changes where it is easy to forget DTOs, templates, migrations, docs, tests, or status updates."
user-invocable: true
disable-model-invocation: false
---

# Zoomos v4 feature delivery checklist

Отвечай на русском языке.

Используй этот skill для изменений, которые проходят через несколько слоёв приложения.

## 1. Сначала определи контур изменения
Зафиксируй, какие слои затрагиваются:
- Controller / route
- DTO / mapper
- Service / async flow
- Repository / entity / migration
- Thymeleaf template / JS / CSS
- Docs / Claude assets

Если изменение не сквозное и затрагивает только один файл, не раздувай решение.

## 2. Построй минимальный маршрут пользователя
Для каждой фичи коротко сформулируй:
- откуда пользователь начинает,
- что нажимает или отправляет,
- какой статус/redirect получает,
- где видит результат или ошибку.

Если этого маршрута нет, сначала восстанови его на бумаге, потом меняй код.

## 3. Проверь обязательные связки

### Для форм и страниц
- Согласованы ли `th:field`, `name`, `id`, DTO поля и `@RequestParam`/`@ModelAttribute`.
- Есть ли `RedirectAttributes` или другой user feedback.
- Обновлены ли breadcrumbs и ссылки назад.

### Для async операций
- Создаётся ли сущность статуса синхронно.
- Есть ли `operationId`/session identifier.
- Понятно ли, где смотреть прогресс (`operations/status`, WebSocket topic, flash message).

### Для БД-изменений
- Нужен ли Flyway migration.
- Не сломает ли изменение существующие данные.
- Требуется ли обновить документацию в `docs/`.

## 4. Выбирай агентов точечно
- `import-export-orchestrator` — если меняется пользовательский pipeline import/export.
- `websocket-async-architect` — если есть фоновые задачи, progress, scheduler.
- `thymeleaf-ui-expert` — если меняется страница, форма, fragment или UX.
- `database-expert` / `database-postgres-expert` — если меняется схема, SQL, индексы.
- `zoomos-check-expert` — если задача внутри Zoomos Check.
- `redirect-expert` — если задача касается redirect finder и антибот логики.

Не запускай всех сразу: выбери минимальный набор.

## 5. Финальная проверка перед коммитом
- Код собирается хотя бы на затронутом участке.
- Изменение проверено командой уровня проекта (`mvn test`, `mvn -q -DskipTests compile` или точечные тесты).
- Обновлена документация, если изменилась функциональность, поток пользователя или эксплуатация.
- В финальном отчёте есть список файлов, проверок и известных ограничений.
