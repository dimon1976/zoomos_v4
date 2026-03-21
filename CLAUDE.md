# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zoomos v4 — Spring Boot 3.2.12, Java 17, PostgreSQL, Thymeleaf, Bootstrap 5. Pet-проект: импорт/экспорт файлов, Zoomos Check (проверка выкачки), HTTP редиректы, справочник ШК.

**Stack**: Spring Boot 3.2.12, Java 17, PostgreSQL, Hibernate/JPA, WebSocket (STOMP), Apache POI 5.2.3, OpenCSV 5.8, Maven, Flyway, Lombok

**Принципы**: KISS, YAGNI, MVP, итеративная разработка. Не усложняй.

## Development Commands

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=silent  # рекомендуется
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn clean package
mvn test -Dtest=ClassNameTest
psql -d zoomos_v4 -c "SELECT COUNT(*) FROM clients;"
mvn flyway:info
```

**URLs**: localhost:8081 | `/utils` | `/handbook` | `/maintenance` | `/statistics/setup` | `/zoomos`

**Профили**: `silent` (рекомендуется) · `dev` · `verbose` · `prod`

## Server Management (Windows — ОБЯЗАТЕЛЬНЫЕ правила)

**НИКОГДА не использовать** `taskkill /F /IM java.exe` — убивает все JVM в системе.

```bash
netstat -ano | findstr :8081   # найти PID
taskkill /F /PID <PID>          # остановить конкретный процесс
mvn spring-boot:run -Dspring-boot.run.profiles=silent
```

Запускать только **один** экземпляр сервера.

## AI Development Guidelines

- При старте использовать `--dangerously-skip-permissions`
- Разработка на Windows, общение на русском языке
- Использовать Context7 для изучения документации библиотек
- Закрывать сервер после тестирования

## Agents & Skills

### Агенты (автоматически выбираются по контексту)

| Агент | Когда использовать |
| ----- | ------------------ |
| `file-processing-expert` | Импорт/экспорт, Excel/CSV, даты, нулевые цены, EntityType, ШК/EAN, /handbook, DataMerger |
| `websocket-async-architect` | WebSocket, прогресс-бары, executors, @Async, CompletableFuture, REQUIRES_NEW, фоновые задачи |
| `zoomos-check-expert` | Zoomos Check, выкачка упала, evaluateGroup, Playwright, Redmine, расписания, баннер |
| `database-maintenance-expert` | Flyway миграции, добавь колонку/таблицу, VACUUM, PostgreSQL, DataCleanup |
| `redirect-expert` | Редиректы, финальный URL, стратегии, прокси, антибот, /utils/redirect-finder |

Явный вызов: `"Используй zoomos-check-expert — баннер priority-alerts не обновляется"`

### Улучшение маршрутизации (AGENT ROUTING FEEDBACK)

После сессий с изменениями файлов Stop-хук спрашивает про маршрутизацию.
Если пользователь описывает проблему — **немедленно обновить description агента/скилла**:

- Агент не сработал → добавить пример в `Examples:` и/или keyword в `Trigger keywords:`
- Агент сработал лишний раз → уточнить условия, добавить `NOT when:` в description
- Не тот агент → добавить разграничение между агентами в description обоих
- После правки → `git add .claude/agents/ .claude/skills/ && git commit -m "chore: уточнена маршрутизация ..."`

### Скилы (slash команды)

| Команда | Назначение |
| ------- | ---------- |
| `/server [start\|stop\|restart\|status]` | Управление сервером на порту 8081 |
| `/flyway [описание]` | Создать следующую Flyway миграцию |
| `/db [SQL\|alias]` | Быстрые запросы к БД (aliases: clients, shops, vacuum, migrations...) |
| `/commit [сообщение]` | Check → auto-fix → commit (цикл до чистого состояния) |
| `/check` | Pre-commit проверка: компиляция, изменения, безопасность |
| `/explain [класс/файл]` | Объяснение кода с аналогией и ASCII-диаграммой |
| `/zoomos-check [shop\|list]` | Последние проверки для магазина |
| `/systematic-debugging [проблема]` | Отладка по 4 фазам: root cause → паттерн → гипотеза → фикс |
| `/verification-before-completion` | Протокол верификации перед заявлением "готово" |
| `/test-driven-development [задача]` | Red-Green-Refactor, JUnit 5 + Maven |
| `/writing-plans [задача]` | Детальный план реализации перед кодингом |
| `/brainstorming [задача]` | Проектирование: 2-3 подхода с трейдоффами, без кода |

## Documentation Rules (ОБЯЗАТЕЛЬНО)

**Вся документация хранится в папке `docs/`** — один файл на каждое функциональное направление.

| Файл | Направление |
| --- | --- |
| [`docs/zoomos-check.md`](docs/zoomos-check.md) | Zoomos Check — evaluateGroup, тренды, расписание, Redmine |
| [`docs/maintenance.md`](docs/maintenance.md) | Система обслуживания — расписание, очистка БД, диагностика |
| [`docs/claude-setup.md`](docs/claude-setup.md) | Агенты, скилы, команды, хуки — как пользоваться .claude/ |

1. **После любых изменений** в функционале — обновить соответствующий файл в `docs/`.
2. **При добавлении нового направления** — создать `docs/{название}.md` и добавить строку в таблицу выше.
3. Это **обязательный последний шаг** любой задачи наравне с коммитом.

## Critical Non-Obvious Behaviors

### HTTP Redirect — CurlStrategy

НЕ использовать User-Agent headers (goldapple.ru и др. блокируют). Ручное следование редиректам вместо `curl -L`. Сохранять `initialRedirectCode` (301/302), не финальный статус.

### Import Dates

`DataFormatter` POI применяет US локаль → даты ломаются для STRING полей. Авто-исправление: [ImportProcessorService.java:716-741](src/main/java/com/java/service/imports/ImportProcessorService.java#L716-L741)

### Redmine (tt.zoomos.by)

POST/PUT/DELETE возвращают HTTP 404 с пустым телом, но операции **выполняются успешно** (Apache + mod_jk + Phusion Passenger). Использовать `postIgnoring404()` / `putIgnoring404()`.

### Zoomos Check

- Entity `ZoomosKnownSite` → таблица **`zoomos_sites`** (не `zoomos_known_sites`!)
- `chartData`: передавать только примитивы (`List<Map>`), не JPA-объекты → иначе `LazyInitializationException`
- Счётчики OK/Warn/Error вычисляются **динамически** из `siteCityStatuses`, не из `run.warningCount` в БД
- `canDeliver = false` только при ERROR или NOT_FOUND

### Нулевые цены

`0.0` → пустые ячейки в Excel: [XlsxFileGenerator.java:305-316](src/main/java/com/java/service/exports/generator/XlsxFileGenerator.java#L305-L316)

## Configuration (non-obvious)

```properties
spring.servlet.multipart.max-file-size=1200MB
maintenance.scheduler.enabled=false   # DISABLED by default — включать явно
```

## important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.
Use `file_path:line_number` pattern when referencing code locations.
