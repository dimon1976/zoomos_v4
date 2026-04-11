# Claude Code — Настройка .claude/ для Zoomos v4

Подробное руководство по агентам, скилам, командам и хукам.

---

## Содержание

1. [Как всё устроено](#как-всё-устроено)
2. [Агенты](#агенты)
3. [Скилы (slash-команды)](#скилы-slash-команды)
4. [Команды](#команды)
5. [Хуки](#хуки)
6. [Примеры типовых задач](#примеры-типовых-задач)
7. [Улучшение маршрутизации](#улучшение-маршрутизации)

---

## Как всё устроено

```
.claude/
├── agents/          # Специализированные субагенты (автовыбор по контексту)
├── skills/          # Slash-команды (/server, /commit и др.)
├── commands/        # Slash-команды для сложных workflow
├── hooks/           # Python-скрипты автоматизации (запускаются автоматически)
├── settings.json    # Конфигурация хуков
└── settings.local.json  # Локальные настройки (не коммитить)
```

**Агенты vs Скилы vs Команды:**

| Тип | Кто вызывает | Когда |
|-----|-------------|-------|
| Агент | Claude автоматически | Контекст совпадает с description агента |
| Агент (явно) | Пользователь | `"Используй zoomos-check-expert — ..."` |
| Скил | Пользователь | `/server start`, `/commit`, `/flyway добавь колонку` |
| Команда | Пользователь | `/code-review`, `/validate-and-fix` |
| Хук | Система | Автоматически на события (старт сессии, стоп, вызов Bash) |

---

## Агенты

Агенты — это специализированные AI-субагенты с конкретными инструментами и знаниями. Claude сам выбирает нужный агент по описанию задачи. При необходимости можно вызвать явно.

**Явный вызов:**
```
"Используй file-processing-expert — импорт ломает даты для STRING полей"
"Попроси zoomos-check-expert разобраться с evaluateGroup"
```

### Проект-специфичные агенты

---

#### `file-processing-expert`

**Когда автовыбирается:** импорт/экспорт файлов, Excel/CSV, штрихкоды, /handbook, DataMerger, статистика экспорта.

**Сервисы:** `AsyncImportService`, `AsyncExportService`, `ImportProcessorService`, `ExportProcessorService`, `BarcodeHandbookService`, `BarcodeUtils`, `DataMergerService`, `ExportStatisticsService`

**Примеры запросов:**
```
"Даты в competitorTime импортируются неправильно — формат ломается"
"Нулевые цены отображаются как 0, а не пустая ячейка в экспорте"
"Экспорт 500k строк слишком долго"
"Штрихкод нормализуется неправильно — EAN-14 не конвертируется в EAN-13"
"Поиск по /handbook не находит товары по ШК"
"Слияние данных в /utils/data-merger дублирует строки"
"Добавь новый EntityType BH_BRAND_URL"
```

---

#### `websocket-async-architect`

**Когда автовыбирается:** WebSocket, прогресс-бары, @Async, executor, CompletableFuture, REQUIRES_NEW, фоновые задачи.

**Сервисы:** `WebSocketConfig`, STOMP endpoints, `TaskExecutor` (import/export/redirect/cleanup/fileAnalysis/utils), `TransactionSynchronization.afterCommit()`

**Примеры запросов:**
```
"Добавь прогресс-бар для операции слияния данных в DataMergerService"
"WebSocket уведомления пропадают при нескольких одновременных операциях"
"Нужен новый executor для Redmine задач с таймаутом 5 минут"
"Как запустить фоновую задачу с сохранением транзакционного контекста?"
"Прогресс-бар не приходит на фронтенд"
"Как правильно использовать @Async с @Transactional?"
```

---

#### `zoomos-check-expert`

**Когда автовыбирается:** Zoomos Check, evaluateGroup, Playwright, Redmine, расписания проверок, баннер priority-alerts.

**Сервисы:** `ZoomosCheckService`, `ZoomosAnalysisController`, `ZoomosRedmineController`, `RedmineService`, `ZoomosShopSchedule`

**Примеры запросов:**
```
"evaluateGroup говорит OK, но реально товары упали на 15%"
"Выкачка упала — сайт не выкачивается второй день"
"Создай Redmine задачу для сайта wildberries.ru"
"Playwright не может авторизоваться, кука ZoomosSession протухла"
"Расписание не запускается — cron выражение не работает"
"Баннер priority-alerts не обновляется"
"Счётчики OK/Warn/Error неправильные"
```

---

#### `database-maintenance-expert`

**Когда автовыбирается:** Flyway миграции, VACUUM, ANALYZE, очистка БД, DataCleanupService, HikariCP.

**Примеры запросов:**
```
"Создай Flyway миграцию для добавления колонки is_active в zoomos_shops"
"Добавь колонку last_seen_at в таблицу zoomos_sites"
"Нужна новая таблица для хранения логов парсинга"
"БД замедлилась после массового удаления — нужен VACUUM"
"Flyway validation failed — checksums do not match"
"Bloat в таблице — нужен REINDEX"
```

> **Совет:** для быстрого создания файла миграции без анализа БД используй скил `/flyway`.

---

#### `redirect-expert`

**Когда автовыбирается:** HTTP редиректы, финальный URL, стратегии (Curl/Playwright/HttpClient/Jsoup), прокси, антибот, /utils/redirect-finder.

**Примеры запросов:**
```
"CurlStrategy не работает для goldapple.ru"
"Определить финальный URL после всех редиректов для списка ссылок"
"Playwright стратегия зависает на cloudflare"
"Прокси-ротация не работает"
"Нужна новая стратегия для антибот-защиты"
"SSRF уязвимость — нужна валидация URL"
```

---

### Универсальные агенты

---

#### `code-review-expert`

**Когда автовыбирается:** после значительных изменений кода, запрос ревью.

Проводит ревью по 6 аспектам: архитектура, качество кода, безопасность, производительность, тесты, документация.

**Примеры:**
```
"Сделай code review изменений в ImportProcessorService"
"Проверь новый код на безопасность"
"Используй code-review-expert для анализа PR"
```

---

#### `research-expert`

**Когда автовыбирается:** исследование технологий, документация библиотек, сравнение подходов.

**Примеры:**
```
"Исследуй лучшие практики для Testcontainers PostgreSQL"
"Найди документацию по Apache POI для работы с формулами"
"Сравни подходы для rate limiting в Spring Boot"
```

---

#### `refactoring-expert`

**Когда автовыбирается:** дублированный код, длинные методы, сложные условия, code smells.

**Примеры:**
```
"Этот сервис стал слишком большим — рефактори"
"Убери дублирование между ImportProcessorService и ExportProcessorService"
"Метод processBatch() занимает 200 строк — разбей"
```

---

#### `code-search`

**Когда автовыбирается:** поиск файлов, функций, паттернов по кодовой базе.

**Примеры:**
```
"Найди все места где используется BarcodeUtils"
"Где обрабатывается событие TaskCompleted?"
"Найди все @Scheduled методы в проекте"
```

---

### Специализированные агенты

| Агент | Назначение | Пример вызова |
|-------|-----------|--------------|
| `database-expert` | Оптимизация запросов, схема БД, транзакции | "Оптимизируй этот JPA запрос с N+1" |
| `postgres-expert` | JSONB, партиционирование, pg-специфика | "Как добавить GIN индекс для JSONB?" |
| `git-expert` | Merge conflicts, история, branching | "Помоги разрешить merge conflict" |
| `css-styling-expert` | Bootstrap 5, Thymeleaf стили, адаптивность | "Верстка таблицы на мобильном ломается" |
| `accessibility-expert` | WCAG, ARIA, keyboard navigation | "Проверь доступность формы импорта" |
| `playwright-expert` | E2E тесты, browser automation | "Напиши E2E тест для /zoomos/check" |
| `documentation-expert` | Структура документации, дублирование | "Улучши структуру docs/zoomos-check.md" |
| `testing-expert` | Jest, Vitest, структура тестов | "Почему тест flaky — разберись" |

---

## Скилы (slash-команды)

Скилы вызываются через `/имя [аргументы]` в диалоге с Claude.

---

### `/server [start|stop|restart|status]`

Управление сервером Spring Boot на порту 8081.

```bash
/server start       # запустить с профилем silent
/server stop        # остановить (находит PID, убивает правильно)
/server restart     # stop → start
/server status      # проверить запущен ли
```

**Под капотом:** `netstat -ano | findstr :8081` → `taskkill /F /PID <PID>` → `mvn spring-boot:run -Dspring-boot.run.profiles=silent`

---

### `/commit [сообщение]`

Check → auto-fix → commit цикл до чистого состояния.

```bash
/commit "feat: добавить экспорт в PDF"
/commit   # сообщение генерируется автоматически по diff
```

**Цикл:** компиляция → lint → если есть ошибки → авто-фикс → снова проверка → commit.

---

### `/check`

Pre-commit проверка без коммита.

```bash
/check   # проверить компиляцию, изменения, безопасность
```

**Проверяет:** `mvn compile -q`, наличие sensitive данных (.env, паролей), незакоммиченные изменения.

---

### `/flyway [описание изменения]`

Создать следующий файл Flyway миграции.

```bash
/flyway добавить колонку is_active в таблицу zoomos_shops
/flyway создать таблицу export_logs
/flyway добавить индекс на zoomos_check_runs.created_at
```

**Создаёт:** `src/main/resources/db/migration/V{N+1}__{описание}.sql` с правильным номером версии.

---

### `/db [SQL|alias]`

Быстрые запросы к PostgreSQL базе zoomos_v4.

```bash
/db clients               # SELECT * FROM clients LIMIT 10
/db shops                 # SELECT * FROM shops LIMIT 10
/db vacuum                # VACUUM ANALYZE статистика
/db migrations            # SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC
/db "SELECT COUNT(*) FROM zoomos_check_runs WHERE status = 'OK'"
/db "SELECT * FROM zoomos_settings WHERE key LIKE 'maint.%'"
```

---

### `/explain [класс/файл]`

Объяснение кода с аналогией и ASCII-диаграммой.

```bash
/explain ImportProcessorService
/explain src/main/java/com/java/service/ZoomosCheckService.java
/explain evaluateGroup метод
```

**Формат ответа:** назначение → как работает → ASCII-диаграмма потока → нетривиальные особенности.

---

### `/zoomos-check [shop|list]`

Последние результаты Zoomos Check.

```bash
/zoomos-check wildberries     # последние проверки для магазина
/zoomos-check list            # список всех магазинов
/zoomos-check                 # статус последнего запуска
```

---

### `/systematic-debugging [описание проблемы]`

4-фазная отладка: root cause → паттерн → гипотеза → фикс. **Запрет патчить симптомы без понимания причины.**

```bash
/systematic-debugging LazyInitializationException в ZoomosCheckService
/systematic-debugging прогресс-бар не приходит для операции слияния
/systematic-debugging @Transactional не откатывается при ошибке
/systematic-debugging N+1 запросы в ExportProcessorService
```

**4 фазы:**
1. **Root cause** — читать stack trace, `git diff HEAD~1`, добавить диагностику
2. **Паттерн** — найти аналогичный рабочий код, сравнить
3. **Гипотеза** — "X вызывает Y потому что Z", тестировать по одной
4. **Фикс** — сначала failing тест, потом минимальный фикс, потом GREEN

---

### `/verification-before-completion`

Активирует протокол верификации: запрет говорить "готово" без запуска проверок.

```bash
/verification-before-completion   # применить протокол к текущей задаче
```

**Обязательные команды перед "готово":**
```bash
mvn compile -q                    # компиляция
mvn test -Dtest=КлассТеста -q    # тест
netstat -ano | findstr :8081      # сервер
```

---

### `/test-driven-development [задача]`

Red-Green-Refactor цикл для JUnit 5 + Maven.

```bash
/test-driven-development реализовать расчёт трендов для ZoomosCheckService
/test-driven-development добавить валидацию EAN-13 в BarcodeUtils
```

**Цикл:**
1. Написать failing тест → `mvn test -Dtest=...` → убедиться RED
2. Написать минимальный код → `mvn test` → убедиться GREEN
3. Рефакторинг → тест остаётся GREEN

---

### `/writing-plans [задача]`

Детальный план реализации перед кодингом. **Нет кода до одобрения плана.**

```bash
/writing-plans добавить экспорт в формат XLSX с несколькими листами
/writing-plans реализовать автоматическую повторную отправку Redmine задач
/writing-plans добавить кэширование для BarcodeHandbookService
```

**Структура плана:** цель → затронутые файлы → шаги (каждый ≤5 мин с тестом).

---

### `/brainstorming [задача]`

2-3 подхода с трейдоффами, без кода до выбора. Идеально для архитектурных решений.

```bash
/brainstorming как хранить историю изменений цен без bloat в БД
/brainstorming как организовать retry логику для HTTP редиректов
/brainstorming какой подход выбрать для кэширования результатов Zoomos Check
```

**Формат:** Подход 1/2/3 → суть → плюсы → минусы → когда выбирать → "Какой подход выбираем?"

---

## Команды

Команды — более сложные slash-команды для multi-step workflow.

### `/code-review`

Параллельный code review по нескольким аспектам одновременно.

```bash
/code-review                    # ревью всех изменённых файлов
/code-review ImportProcessorService.java   # ревью конкретного файла
```

Проверяет: архитектуру, безопасность, производительность, тесты, документацию.

---

### `/validate-and-fix`

Проверка → обнаружение проблем → авто-фикс → повтор.

```bash
/validate-and-fix   # запустить полный цикл валидации
```

Включает: компиляцию, тесты, checkstyle, потенциальные NPE, незакрытые ресурсы.

---

### `/research [тема]`

Структурированное исследование с параллельным поиском.

```bash
/research Spring Boot 3.2 Flyway migration best practices
/research Testcontainers PostgreSQL интеграционные тесты Java
```

---

### `/server [start|stop|status]`

(Дублирует скил `/server` — можно использовать любой вариант)

---

### `/git/commit [сообщение]`

Умный git commit: анализирует diff → предлагает conventional commit сообщение.

```bash
/git/commit
/git/commit "fix: исправить NPE в ExportProcessorService"
```

---

### `/git/push [remote] [branch]`

Push с проверками: убеждается что ветка актуальна, нет конфликтов.

```bash
/git/push
/git/push origin main
```

---

### `/git/status`

Подробный статус: что изменено, что не закоммичено, отставание от remote.

---

### `/git/checkout [ветка|файл]`

Умный checkout: создаёт ветку если не существует, предупреждает о незакоммиченных изменениях.

```bash
/git/checkout feature/export-pdf
/git/checkout main
```

---

### `/git/ignore-init`

Создать/обновить `.gitignore` для Java/Spring Boot/Maven проекта.

---

### `/checkpoint/create [описание]`

Создать git tag как checkpoint (точка возврата).

```bash
/checkpoint/create перед рефакторингом ImportProcessorService
/checkpoint/create рабочее состояние экспорта v2
```

---

### `/checkpoint/list`

Список всех checkpoints с датами.

---

### `/checkpoint/restore [имя]`

Вернуться к checkpoint.

```bash
/checkpoint/restore перед-рефакторингом-importprocessorservice
```

---

### `/gh/repo-init`

Инициализация GitHub репозитория: .gitignore, README, первый коммит, push.

---

### `/dev/cleanup`

Очистка рабочей директории: temp файлы, Maven target, логи.

---

## Хуки

Хуки запускаются автоматически системой Claude Code на определённые события. Настроены в `.claude/settings.json`.

### `pre_tool_use.py` — Safety Guard

**Событие:** каждый вызов Bash инструмента (до выполнения)

**Что блокирует:**

| Паттерн | Причина блокировки | Альтернатива |
|---------|-------------------|-------------|
| `taskkill /IM java.exe` | Убивает ВСЕ JVM в системе | `netstat -ano | findstr :8081` → `taskkill /F /PID <PID>` |
| `rm -rf ~` / `rm -rf /e/workspace/zoomos_v4` | Удаление критических путей | Указать конкретные файлы |
| `cat .env` / `mv .env` | Доступ к секретам | Использовать `application.properties` |

**Пример вывода при блокировке:**
```
ЗАПРЕЩЕНО: 'taskkill /IM java.exe' убивает ВСЕ JVM в системе!
Правильный способ:
  1. netstat -ano | findstr :8081
  2. taskkill /F /PID <конкретный_PID>
```

---

### `session_start.py` — Git Context

**Событие:** старт каждой сессии Claude Code

**Что делает:** добавляет в контекст сессии текущую ветку, количество изменённых файлов и последний коммит.

**Пример контекста:**
```
Zoomos v4 | ветка: main | 3 незакоммиченных файла | последний коммит: c82e852 fix: code review PR #57
```

Claude видит этот контекст с первого сообщения — не нужно объяснять на какой ветке ты и что происходит.

---

### `notification.py` — Windows Звуки

**События и звуки:**

| Событие | Звук | Когда |
|---------|------|-------|
| `SessionStart` | Двойной восходящий тон (C5→E5) | Новая сессия открыта |
| `Stop` | Двойной высокий тон (A5→C6) | Claude завершил задачу |
| `SubagentStop` | Одиночный тон (E5) | Субагент (агент) завершил работу |

Реализован через `winsound.Beep(frequency, duration)` — без внешних зависимостей, работает из коробки на Windows.

---

### Настройка хуков (`settings.json`)

```json
{
  "hooks": {
    "PreToolUse": [{ "matcher": "Bash", "hooks": [...] }],
    "SessionStart": [{ "matcher": "*", "hooks": [...] }],
    "Stop": [{ "matcher": "*", "hooks": [...] }],
    "SubagentStop": [{ "matcher": "*", "hooks": [...] }]
  }
}
```

**Добавить новый хук (пример — PostToolUse после Edit):**
```json
"PostToolUse": [
  {
    "matcher": "Edit",
    "hooks": [{
      "type": "command",
      "command": "python .claude/hooks/my_hook.py",
      "timeout": 5000
    }]
  }
]
```

---

## Примеры типовых задач

### Отладить баг в импорте

```
1. /systematic-debugging даты ломаются при импорте через POI для STRING полей
   → Claude проходит 4 фазы, находит root cause, предлагает фикс
2. /verification-before-completion
   → Перед "готово" запускает mvn compile + mvn test
3. /commit "fix: исправить локаль DataFormatter для STRING полей"
```

---

### Добавить новую колонку в таблицу

```
1. /flyway добавить колонку retry_count в таблицу zoomos_check_runs
   → Создаст V{N+1}__add_retry_count_to_zoomos_check_runs.sql
2. Отредактировать Entity класс (автоматически предложит агент)
3. /check   → убедиться что компилируется
4. /commit "feat: добавить поле retry_count для Zoomos Check runs"
```

---

### Запланировать сложную фичу

```
1. /brainstorming как добавить экспорт истории проверок в Excel
   → Получить 2-3 подхода с трейдоффами, выбрать один
2. /writing-plans реализовать экспорт истории Zoomos Check в Excel
   → Получить детальный план по шагам с тестами
3. Реализовать шаг за шагом (каждый шаг ≤5 минут)
4. /commit "feat: экспорт истории проверок в Excel"
```

---

### Полный рефакторинг

```
1. Описать что нужно рефакторить
   → Автоматически подхватит refactoring-expert
2. /writing-plans рефакторинг ExportProcessorService — выделить стратегии
3. /checkpoint/create перед рефакторингом ExportProcessorService
4. Реализовать план
5. /validation-and-fix
6. /commit "refactor: выделить стратегии экспорта в отдельные классы"
```

---

### Проверить работу хуков

```bash
# Проверить session_start
echo '{}' | python .claude/hooks/session_start.py

# Проверить звук
echo '{}' | python .claude/hooks/notification.py stop

# Проверить settings.json синтаксис
cat .claude/settings.json | python -m json.tool
```

---

## Улучшение маршрутизации

Если агент не сработал когда должен, или сработал лишний раз — обнови его description:

```
"Используй file-processing-expert — но у меня ещё не сработало автоматически
 когда я написал 'Excel файл не генерируется'. Добавь этот пример в агент."
```

После правки:
```bash
git add .claude/agents/ .claude/skills/
git commit -m "chore: уточнена маршрутизация file-processing-expert"
```

**Что обновлять:**
- Агент не сработал → добавить пример в `Examples:` и/или keyword в `description`
- Агент сработал лишний раз → добавить `NOT when:` в description
- Не тот агент → добавить разграничение в description обоих агентов
