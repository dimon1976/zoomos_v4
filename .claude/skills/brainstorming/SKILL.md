---
name: brainstorming
description: "Structured design exploration before implementation. Present 2-3 approaches with trade-offs, NO code until design approved. Trigger keywords: придумай решение, как лучше сделать, brainstorm, мозговой штурм, варианты решения, какой подход выбрать."
user-invocable: true
context: fork
agent: general-purpose
argument-hint: "[проблема или задача]"
allowed-tools: Read, Grep, Glob, Bash
---

Отвечай на русском языке.

# Brainstorming

**ПРАВИЛО: НЕТ реализации до одобрения дизайна.**

Никакого кода, скаффолдинга или структур файлов — только исследование и дизайн.

---

## Процесс

### 1. Изучи контекст

```bash
# Изучи существующие паттерны в проекте
grep -r "похожий_класс" src/main/java/ --include="*.java" -l
ls src/main/java/com/java/service/
```

- Что уже сделано похожего в проекте?
- Какой стек и паттерны используются?
- Есть ли ограничения (из CLAUDE.md)?

### 2. Задавай вопросы по одному

Не задавай все вопросы сразу. По одному, ждём ответа перед следующим:

- "Какой масштаб — 10 записей или 10 миллионов?"
- "Нужна ли это realtime или batch-обработка?"
- "Это одноразово или будет использоваться регулярно?"

### 3. Предложи 2-3 подхода

Для каждого подхода:

```
## Подход N: [название]

**Суть:** Одно предложение что это.

**Как работает:**
[Краткое описание или ASCII-схема]

**Плюсы:**
- ...

**Минусы:**
- ...

**Когда выбирать:** [конкретное условие]
```

### 4. Получи одобрение

Задай прямой вопрос: "Какой подход выбираем?"

После выбора → используй `/writing-plans` для детального плана.

---

## Принципы для Zoomos v4

**KISS:** pet-проект, не усложнять. Предпочитай простые решения.

**YAGNI:** Не добавляй то что "может понадобиться". Только то что нужно сейчас.

**Следуй существующим паттернам:**
- Async операции → `afterCommit() + TaskExecutor` (как в ImportService)
- WebSocket прогресс → SimpMessagingTemplate → `/topic/progress/{id}`
- БД изменения → Flyway миграция V{N+1}
- Новый сервис → `@Service`, `@RequiredArgsConstructor`, `@Slf4j`

**Не изобретай велосипед** — проверь есть ли уже готовая утилита в `com.java.util`.

---

## ASCII-схема (если помогает)

```
[Запрос] → [Controller] → [AsyncService] → [TaskExecutor]
                                                   ↓
                                          [BackgroundJob]
                                                   ↓
                                        [WebSocket: /topic/...]
```
