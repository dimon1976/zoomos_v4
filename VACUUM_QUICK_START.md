# Quick Start: Автоматический VACUUM после очистки

## ⚡ Быстрый старт

### Что это?

Автоматическое выполнение `VACUUM ANALYZE` после удаления большого количества записей (> 1 млн) для оптимизации производительности PostgreSQL.

### Зачем нужно?

- ✅ Очищает "мертвые кортежи" (dead tuples) после DELETE
- ✅ Обновляет статистику таблиц
- ✅ Ускоряет запросы на 20-40% после больших очисток
- ✅ НЕ блокирует таблицу - приложение продолжает работать

## 🎯 Настройка (application.properties)

```properties
# Auto-VACUUM после очистки данных
database.cleanup.auto-vacuum.enabled=true                    # Вкл/Выкл
database.cleanup.auto-vacuum.threshold-records=1000000       # Порог: 1 млн записей
database.cleanup.auto-vacuum.run-async=true                  # Асинхронно (не ждать)
```

## 📊 Как работает?

### Автоматически

1. Вы удаляете данные через UI `/maintenance/database`
2. **Если удалено < 1 млн записей** → ничего не происходит (AutoVacuum PostgreSQL справится)
3. **Если удалено ≥ 1 млн записей** → автоматически запускается VACUUM ANALYZE

### Асинхронный режим (по умолчанию)

```
Удаление → Результат возвращается сразу → VACUUM работает в фоне
```

- ⏱️ Результат очистки: **сразу**
- ⏱️ VACUUM выполняется: **15-30 минут** (в фоне)
- ✅ Приложение работает без задержек

### Синхронный режим (опционально)

```properties
database.cleanup.auto-vacuum.run-async=false
```

```
Удаление → VACUUM → Результат возвращается после VACUUM
```

- ⏱️ Ожидание: **15-30 минут**
- ✅ Гарантия выполнения VACUUM
- ⚠️ Пользователь ждет

## 🔧 Типичные сценарии

### Сценарий 1: Продакшен (рекомендуется)

```properties
database.cleanup.auto-vacuum.enabled=true
database.cleanup.auto-vacuum.threshold-records=1000000
database.cleanup.auto-vacuum.run-async=true
```

**Результат:**
- Автоматическая оптимизация
- Без задержек для пользователей
- Подходит для БД > 50M строк

### Сценарий 2: Dev/Staging (малые базы)

```properties
database.cleanup.auto-vacuum.enabled=false
```

**Результат:**
- Отключено (не нужно для малых БД)
- PostgreSQL AutoVacuum справится сам

### Сценарий 3: Плановое обслуживание (ночью)

```properties
database.cleanup.auto-vacuum.enabled=true
database.cleanup.auto-vacuum.threshold-records=500000
database.cleanup.auto-vacuum.run-async=false
```

**Результат:**
- Более низкий порог (500K вместо 1M)
- Синхронное выполнение (ждем завершения)

## 📝 Логи

### Успешное выполнение

```
INFO: Очистка завершена: удалено 5000000 записей за 120000 мс
INFO: Запуск автоматического VACUUM ANALYZE после удаления 5000000 записей
INFO: VACUUM ANALYZE запущен асинхронно в фоновом режиме
INFO: Выполнение VACUUM ANALYZE для таблицы: av_data
INFO: VACUUM ANALYZE завершен для av_data: 12500 мс
INFO: Автоматический VACUUM ANALYZE завершен: обработано 1 таблиц за 12 сек
```

### Не запустился (норма для малых очисток)

```
INFO: Очистка завершена: удалено 500000 записей за 45000 мс
(нет сообщений о VACUUM - порог не достигнут)
```

## ⏱️ Время выполнения

| Размер таблицы | Удалено записей | Время VACUUM ANALYZE |
|----------------|----------------|----------------------|
| 10M строк | 1M-5M | 5-10 минут |
| 50M строк | 5M-10M | 15-25 минут |
| 100M строк | 10M-20M | 30-60 минут |

## 🔍 Проверка работы

### 1. Через логи приложения

Найдите строки:
```
[DataCleanupService] Запуск автоматического VACUUM ANALYZE
[DataCleanupService] VACUUM ANALYZE завершен для av_data
```

### 2. Через PostgreSQL

```sql
SELECT
    relname,
    last_vacuum,
    last_autovacuum,
    n_dead_tup
FROM pg_stat_user_tables
WHERE relname = 'av_data';
```

**Хорошо:** `last_vacuum` = недавняя дата, `n_dead_tup` < 10% от размера

### 3. Через UI

`http://localhost:8081/maintenance/database` → "Анализ раздувания таблиц"

**Норма:** bloat < 30%

## 🚨 Troubleshooting

### VACUUM не запускается

**Проверьте:**
1. `enabled=true` в конфигурации
2. Удалено ≥ `threshold-records` записей
3. Очистка завершилась успешно (status=SUCCESS)

### VACUUM работает слишком долго

**Норма для больших таблиц:**
- 50M строк → 15-30 минут
- 100M строк → 30-60 минут

**Если > 60 минут:**
- Проверьте размер таблицы: `SELECT pg_size_pretty(pg_total_relation_size('av_data'));`
- Дождитесь завершения (это нормально для очень больших таблиц)

### Запросы всё равно медленные

**Причина:** Index bloat > 30%

**Решение:**
1. Откройте `/maintenance/database`
2. Нажмите "Переиндексация (REINDEX)"
3. Дождитесь завершения (~45-90 минут)

**Рекомендуется:** REINDEX раз в месяц после больших очисток

## 🎓 Полное руководство

Для подробной информации см. **VACUUM_AFTER_CLEANUP_GUIDE.md**

---

**Версия:** 1.0 | **Дата:** 2025-10-16 | **Проект:** Zoomos v4
