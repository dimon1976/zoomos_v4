---
name: commit
description: "Pre-commit check → auto-fix issues → commit. Loops until all issues resolved. Use when user asks to commit, make a commit, or commit and push."
user-invocable: true
context: fork
agent: general-purpose
argument-hint: "[commit message]"
allowed-tools: Bash, Read, Glob, Grep, Edit, Write
---

Отвечай на русском языке.

Автоматический commit workflow для Zoomos v4.
Рабочая директория: `/e/workspace/zoomos_v4`

## Входные данные

Сообщение коммита: $ARGUMENTS (если не указано — составь по изменённым файлам)

---

## Алгоритм (максимум 3 итерации исправлений)

### Шаг 1: Pre-commit проверка

Выполни все 4 пункта. При каждом найденном замечании — исправь и вернись к Шагу 1.

**1.1 Компиляция**
```
cd /e/workspace/zoomos_v4 && mvn compile -q 2>&1
```
- OK → продолжай
- FAIL → исправь Java-ошибки через Edit → вернись к 1.1

**1.2 Изменённые файлы**
```
cd /e/workspace/zoomos_v4 && git status --short && git diff --name-only HEAD
```
Запомни список файлов — он нужен для стейджинга.

**1.3 Docs reminder** (по изменённым файлам)
- Есть изменения в `src/.../zoomos/` или `ZoomosCheckService` → нужно обновить `docs/zoomos-check.md`
- Есть новые `V*.sql` → нужно добавить запись в `CLAUDE.md` раздел "Recent Changes"
- Есть новые контроллеры/сервисы → нужно проверить раздел "Package Structure" в `CLAUDE.md`
- Нашёл замечание → внеси правки → вернись к Шагу 1

**1.4 Security scan** (только по изменённым Java-файлам)
- Захардкоженные секреты (password=, api-key=, token= в строках) → исправь
- SQL-конкатенация (потенциальная инъекция) → исправь
- Бизнес-логика, меняющая несколько сущностей без `@Transactional` → исправь
- Нашёл замечание → внеси правки → вернись к Шагу 1

### Шаг 2: Коммит

Когда все проверки пройдены без замечаний:

1. **Составь сообщение коммита**
   - Используй `$ARGUMENTS` если передано, иначе составь сам
   - Формат: `тип: краткое описание на русском` (feat/fix/refactor/docs/chore/ui)
   - Кратко: что изменилось и зачем

2. **Стейджинг** — используй конкретные пути из `git status`, НЕ `git add .` или `git add -A`
   ```
   git add path/to/file1 path/to/file2 ...
   ```
   Исключи: `.env`, `*.key`, `*credentials*`, большие бинарники

3. **Коммит**
   ```bash
   git commit -m "$(cat <<'EOF'
   {сообщение коммита}

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
   EOF
   )"
   ```

4. Сообщи hash коммита и итоговое сообщение.

---

## Правила

- Максимум **3 итерации** исправлений. Если после 3 попыток остаются нерешённые проблемы — остановись, подробно объясни что не получилось исправить автоматически, и попроси помощи.
- **Не делай коммит** при ошибках компиляции — никогда.
- **Не включай в стейджинг** файлы с секретами или бинарники.
- Если `$ARGUMENTS` содержит "push" или "и запушь" — после коммита выполни `git push`.
