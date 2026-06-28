# План: Миграция check-results.html на Tabler-only

## Контекст

Страница `check-results.html` (~2200 строк) поддерживает два layout — Bootstrap и Tabler.
Это приводит к нарастающим проблемам: шим Bootstrap (~70 строк) растёт, модалы не открываются
повторно, стили конфликтуют. Пользователь использует **только Tabler** (он стоит по умолчанию
в cookie). Задача: жёстко зафиксировать Tabler, удалить весь код совместимости, сделать всё
детерминированным.

**Tabler JS** (`@tabler/core@1.4.0/dist/js/tabler.min.js`) включает Bootstrap 5 внутри, но
**не экспортирует `window.bootstrap`**. Поэтому шим необходим — но он должен быть единственным
и надёжным, без условий `if (typeof bootstrap === 'undefined')`.

---

## Критические файлы

- `src/main/resources/templates/zoomos/check-results.html` — всё в одном файле

---

## Шаги реализации

### Шаг 1 — Зафиксировать layout

**Строки 2-10** — заменить динамический выбор layout на жёсткий:

```html
<!-- БЫЛО -->
th:replace="~{__${_layout ?: 'layout/main'}__ :: html( ... )}"

<!-- СТАЛО -->
th:replace="~{layout/tabler-main :: html( ... )}"
```

Убрать зависимость от модели `_layout`. `ThemeControllerAdvice.java` трогать не нужно —
он по-прежнему нужен для остальных 62 страниц.

---

### Шаг 2 — Упростить шим: убрать условие

**Строки 749-820** — убрать обёртку `if (typeof bootstrap === 'undefined')`:

```javascript
// БЫЛО
if (typeof bootstrap === 'undefined') {
    window.bootstrap = { Collapse: {...}, Modal: {...} };
}

// СТАЛО — всегда инициализируем, Tabler никогда не экспортирует window.bootstrap
window.bootstrap = {
    Collapse: { ... },  // без изменений
    Modal: (function() { ... })()  // уже обновлён с WeakMap
};
```

Это делает код детерминированным: шим всегда активен, нет неожиданных переключений.

---

### Шаг 3 — Удалить 3 скрытых trigger-кнопки (HTML)

**Строки 539-540, 670, 710** — удалить элементы:
```html
<!-- Удалить -->
<button id="__triggerRedmineModal" data-bs-toggle="modal" data-bs-target="#redmineModal" hidden tabindex="-1"></button>
<button id="__triggerConfigIssueModal" data-bs-toggle="modal" data-bs-target="#configIssueModal" hidden tabindex="-1"></button>
<button id="__triggerEqPricesModal" data-bs-toggle="modal" data-bs-target="#eqPricesModal" hidden tabindex="-1"></button>
```

Все три модала уже открываются через прямой API:
- `configIssueModal` → строка 2007: `bootstrap.Modal.getOrCreateInstance(el).show()` ✅
- `eqPricesModal` → строка 2171: `bootstrap.Modal.getOrCreateInstance(el).show()` ✅
- `redmineModal` → строка 1386: `new bootstrap.Modal(modalEl)` — **нужно обновить** (Шаг 4)

---

### Шаг 4 — Обновить открытие redmineModal

**Строка ~1386** — заменить `new bootstrap.Modal(modalEl)` на `getOrCreateInstance`:

```javascript
// БЫЛО
const modal = new bootstrap.Modal(modalEl);
modal.show();

// СТАЛО
bootstrap.Modal.getOrCreateInstance(modalEl).show();
```

Также найти и удалить любой вызов `document.getElementById('__triggerRedmineModal')?.click()`.

---

### Шаг 5 — Починить закрытие модалей через `data-bs-dismiss`

В шиме (уже реализовано через `{ once: true }`): при каждом `show()` вешаем обработчики
на кнопки `data-bs-dismiss="modal"`. Проверить что это работает для всех трёх модалей.

Также проверить кнопку `×` (`.btn-close`) — она тоже должна закрывать через шим.

---

### Шаг 6 — Проверить Collapse

Tabler's bundled Bootstrap обрабатывает `data-bs-toggle="collapse"` через data-api.
Наш шим вызывается **только программно** из JS (8 мест). Конфликта нет — убедиться,
что все три collapse (issuesCollapse, verifiedSitesCollapse, trendsCollapse) работают.

Если при клике на заголовок collapse открывается дважды (раз от data-api, раз от шима),
убрать программный вызов шима там где он дублирует data-api.

---

## Что НЕ трогаем

- `ThemeControllerAdvice.java` — нужен для 62 других страниц
- Другие шаблоны — только `check-results.html`
- `data-bs-toggle="collapse"` HTML атрибуты — Tabler их обрабатывает нативно
- CSS стили в `th:fragment="styles"` — не зависят от layout

---

## Порядок выполнения

1. Шаг 1: зафиксировать layout (1 строка)
2. Шаг 2: убрать обёртку шима (2 строки убрать)
3. Шаг 3: удалить 3 trigger-кнопки (3 строки HTML)
4. Шаг 4: обновить redmineModal
5. Шаг 5: проверить `data-bs-dismiss` через шим
6. `mvn compile -q` — проверка компиляции
7. Запустить сервер, открыть check-results, проверить: все три модала (открыть → закрыть → открыть повторно), все collapse, кнопки `=цены`, конфиг-проблема

---

## Верификация

```bash
mvn compile -q
# Открыть /zoomos/check/results/{id}
```

Чек-лист:
- [ ] Страница открывается в Tabler layout без артефактов Bootstrap
- [ ] configIssueModal: открыть → закрыть × → открыть снова → работает
- [ ] eqPricesModal: открыть → закрыть → открыть снова → работает
- [ ] redmineModal: открыть → закрыть → открыть снова → работает
- [ ] Collapse "На что обратить внимание": раскрыть/свернуть
- [ ] Backdrop клик закрывает модал
- [ ] Кнопка "Отмена" / "Закрыть" внутри модала закрывает его
- [ ] Нет дублирующихся открытий collapse
- [ ] Переключение на Bootstrap через `?theme=bootstrap` НА ДРУГИХ страницах всё ещё работает
