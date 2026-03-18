# Claude Code recommendations for Zoomos v4

## Почему проекту нужны дополнительные Claude Code сущности

Текущая конфигурация уже покрывает:
- Zoomos Check и Redmine;
- redirect/antibot;
- WebSocket/async;
- базу данных;
- code review / testing / refactoring.

Но по структуре проекта есть ещё один сильный кластер, который пока описан слабее: **сквозные пользовательские потоки import/export + серверный Thymeleaf UI**.

Именно там у проекта много связей между слоями:
- controller → DTO → async service → file operation/session → status page;
- thymeleaf form → redirect → flash message → progress/status;
- background processing → websocket/notifications.

Из-за этого Claude Code легко чинит один слой и забывает соседний.

## Что показал анализ проекта

### 1. Проект — не просто backend
Это Spring Boot 3.2.12 / Java 17 приложение с Thymeleaf, PostgreSQL, Flyway, WebSocket и Playwright, а не только набором REST endpoint'ов. Это видно по зависимостям в `pom.xml` и по большому объёму HTML templates. Поэтому голые backend-агенты покрывают только часть работы.

### 2. Есть длинные сквозные user flows
Импорт строится через загрузку файлов, промежуточную analyze-страницу, затем старт async-операции и отдельную status-страницу. Экспорт аналогично стартует из UI и дальше живёт как операция со статусом. Это повышает ценность отдельного orchestration-агента.

### 3. Async и WebSocket уже развиты
В проекте несколько executor'ов и STOMP endpoint `/ws`, а значит Claude Code должен помнить не только про Java-код, но и про UX длительных операций: где пользователь увидит прогресс, откуда возьмётся operationId, как статус попадёт на страницу.

### 4. В репозитории уже есть Claude-инфраструктура
В проекте уже существуют `.claude/settings*.json`, `agents/`, `skills/`, GitHub workflows и набор команд. Поэтому правильнее не придумывать новую систему, а расширять текущую — точечно.

## Рекомендуемые hooks

Ниже — **рекомендации**, а не автоматическое включение. Они подобраны под реальные паттерны проекта.

### 1. Hook на изменения Java/Spring слоёв
**Зачем:** если меняются `controller|service|dto|repository`, часто нужно быстро прогнать compile или targeted tests.

**Идея:** запускать after-edit проверку вида:

```json
{
  "matcher": "Write|Edit|MultiEdit",
  "hooks": [{
    "type": "command",
    "command": "bash -lc 'git diff --name-only --cached -- . \"src/main/java/**/*.java\" \"src/test/java/**/*.java\" >/dev/null 2>&1; mvn -q -DskipTests compile'"
  }]
}
```

**Почему полезно именно здесь:** проект большой, с множеством Spring wiring-связей; compile часто ловит больше, чем локальный просмотр диффа.

### 2. Hook на изменения Flyway migration
**Зачем:** миграции в этом проекте почти всегда требуют проверки naming/ordering и часто тянут за собой docs.

**Идея:** при изменении `src/main/resources/db/migration/V*.sql`:
- напоминать обновить `docs/`;
- запускать хотя бы `mvn -q -DskipTests compile`;
- опционально валидировать, что версия миграции уникальна.

### 3. Hook на изменения Thymeleaf templates
**Зачем:** поломки здесь часто не compile-time, а связаны с потерянными `th:field`, неправильными ссылками, fragment include или несовпадением имён полей.

**Идея:** выводить reminder-checklist:
- сверить DTO / controller params;
- проверить breadcrumbs и flash messages;
- убедиться, что после submit у пользователя есть понятный redirect/status.

### 4. Hook на Stop для «сквозного изменения»
**Зачем:** если в diff одновременно есть Java + template + migration/doc, значит задача была многослойной и Claude Code стоит напомнить про cross-layer self-review.

**Идея:** на `Stop` определять mix файлов и печатать:
- проверен ли user flow end-to-end;
- обновлены ли docs;
- не забыты ли status page / notifications / websocket.

## Рекомендуемые agents

### 1. `import-export-orchestrator`
**Почему нужен:** в проекте уже есть domain-агенты, но нет агента, отвечающего именно за **сквозной pipeline import/export**.

**Когда вызывать:**
- изменение upload/analyze/start/status цепочки;
- проблема с `operationId`, session, redirect;
- изменение шаблонов импорта/экспорта;
- баг в пользовательском пути от загрузки файла до результата.

**Что он должен помнить:**
- user flow целиком;
- `FileOperation`, `ImportSession`, `ExportSession`;
- статусные страницы и обратную связь пользователю;
- согласованность контроллеров, DTO, шаблонов и async service.

### 2. `thymeleaf-ui-expert`
**Почему нужен:** серверный UI в проекте большой, а текущие агенты в основном backend/domain-oriented. Нужен узкий агент, который думает не про SPA, а про Thymeleaf, fragments, forms и UX длительных операций.

**Когда вызывать:**
- форма/страница/fragment;
- breadcrumbs, flash message, page actions;
- повторяющийся UI;
- улучшение UX на status/update страницах.

## Рекомендуемый skill

### `feature-delivery`
Это не domain skill, а **операционный skill-чеклист** для средних задач, которые проходят через несколько слоёв.

**Почему он полезен именно здесь:**
- в Zoomos v4 легко забыть один из слоёв: DTO, template, migration, docs, status page;
- часть изменений не требует узкого domain-эксперта, но требует дисциплины исполнения;
- skill можно вызывать как шаблон работы перед реализацией фичи.

**Что он даёт:**
- определение контура изменения;
- фиксацию user flow;
- обязательные cross-layer проверки;
- подсказку, каких агентов подключать точечно;
- финальный delivery checklist перед коммитом.

## Что я добавил в репозиторий

Чтобы рекомендации были не теоретическими, а готовыми к использованию, в репозиторий добавлены:
- агент `import-export-orchestrator`;
- агент `thymeleaf-ui-expert`;
- skill `feature-delivery`.

Это безопасное расширение: текущие настройки проекта не ломаются, но Claude Code получает новые точки маршрутизации для задач, которые раньше описывались слишком общо.

## Приоритет внедрения

1. **Сразу использовать новых agents/skill** — это даёт наибольшую пользу с минимальным риском.
2. **Добавить hooks для Java/Flyway/Thymeleaf reminder'ов** — сначала как мягкие уведомления.
3. **Только потом** ужесточать hooks до compile/test enforcement, если шум от ложных срабатываний окажется приемлемым.
