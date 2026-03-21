---
name: systematic-debugging
description: "4-phase systematic debugging: root cause first, no fixes without diagnosis. Use when debugging Spring Boot errors, LazyInitializationException, @Transactional issues, NPE, or any recurring bug. Trigger keywords: баг, ошибка, не работает, зависает, exception, отладить, найти причину."
user-invocable: true
context: fork
agent: general-purpose
argument-hint: "[описание проблемы или класс/метод]"
allowed-tools: Read, Grep, Glob, Bash
---

Отвечай на русском языке.

# Systematic Debugging

**ГЛАВНОЕ ПРАВИЛО: НИКАКИХ ИСПРАВЛЕНИЙ БЕЗ ПРЕДВАРИТЕЛЬНОГО ИЗУЧЕНИЯ ПЕРВОПРИЧИНЫ.**

Патч симптомов без понимания причины создаёт технический долг и маскирует реальные проблемы.

---

## Фаза 1: Изучение первопричины

1. **Прочитай ошибку полностью** — stack trace, exception message, строка кода
2. **Воспроизведи проблему стабильно** — найди минимальный шаг для повторения
3. **Проверь последние изменения**: `git log --oneline -10`, `git diff HEAD~1`
4. **Добавь диагностику** для многокомпонентных систем:
   - Для @Transactional проблем: проверь propagation и isolation
   - Для LazyInitializationException: найди где сессия Hibernate закрывается
   - Для NPE: трассируй откуда null приходит в цепочке вызовов
   - Для async проблем: проверь executor config и afterCommit() паттерн

**Spring Boot частые ловушки:**
- `LazyInitializationException` → коллекция не загружена вне транзакции → добавь `@Transactional` или используй `JOIN FETCH`
- `@Transactional` не работает → self-invocation (вызов через `this.`) или неверный propagation
- `@Async` не работает → вызов из того же бина без прокси
- `ConcurrentModificationException` → изменение коллекции во время итерации
- N+1 запросы → проверь `hibernate.show_sql=true`, добавь `@EntityGraph` или `JOIN FETCH`

---

## Фаза 2: Анализ паттернов

1. **Найди похожий рабочий код** в проекте — как решена аналогичная задача
2. **Изучи разницу** между рабочим и сломанным вариантом
3. **Составь список отличий** — каждое отличие = потенциальная причина

```bash
# Найти похожие реализации
grep -r "похожий_паттерн" src/main/java/ --include="*.java" -l
```

---

## Фаза 3: Формирование и тестирование гипотезы

Сформулируй гипотезу по шаблону:
> "Я думаю, **X** вызывает **Y** потому что **Z**"

Правила тестирования:
- Меняй **одну переменную за раз**
- Не накладывай несколько исправлений одновременно
- Если 3 гипотезы подряд не подтвердились → вернись к Фазе 1

---

## Фаза 4: Реализация

1. **Сначала напиши failing тест** (если применимо)
   ```bash
   cd /e/workspace/zoomos_v4 && mvn test -Dtest=ИмяТеста -q 2>&1
   ```
2. **Сделай одно сфокусированное изменение**
3. **Проверь что тест стал green**
4. **Убедись что ничего не сломалось**: `mvn compile -q 2>&1`

---

## Красные флаги (вернись к Фазе 1)

- Думаешь "быстрый фикс на сейчас"
- Хочешь "просто попробовать поменять это"
- Каждое исправление открывает новую проблему в другом месте → вероятно, проблема архитектурная

---

## Команды диагностики для Zoomos v4

```bash
# Последние логи сервера
netstat -ano | findstr :8081

# Проверка компиляции
cd /e/workspace/zoomos_v4 && mvn compile -q 2>&1

# Запустить конкретный тест
cd /e/workspace/zoomos_v4 && mvn test -Dtest=КлассТеста -q 2>&1

# SQL логи (добавить в application.properties временно)
# spring.jpa.show-sql=true
# spring.jpa.properties.hibernate.format_sql=true

# Состояние БД
PGPASSWORD=root psql -U postgres -d zoomos_v4 -c "SELECT ..."
```
