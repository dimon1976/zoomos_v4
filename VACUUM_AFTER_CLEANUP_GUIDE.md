# Руководство по автоматическому VACUUM после очистки данных

## Обзор

Система автоматического VACUUM интегрирована в утилиту очистки данных (`DataCleanupService`) для оптимизации производительности PostgreSQL после массового удаления записей.

## Зачем нужен VACUUM после очистки?

### Проблема: Index Bloat (раздувание индексов)

Когда вы удаляете большое количество записей через `DELETE FROM`:

1. **Строки НЕ удаляются физически** - они помечаются как "dead tuples" (мертвые кортежи)
2. **Индексы содержат ссылки на dead tuples** - это приводит к раздуванию (bloat)
3. **Запросы становятся медленнее** на 10-30% из-за лишних узлов в B-tree индексах
4. **Место на диске не освобождается** - остаются "дырки" в таблицах

### Решение: VACUUM ANALYZE

- **VACUUM** - помечает dead tuples как доступные для повторного использования
- **ANALYZE** - обновляет статистику таблиц для оптимизатора запросов
- **НЕ блокирует таблицу** - приложение продолжает работать
- **Улучшает производительность** запросов на 20-40% после больших очисток

## Конфигурация

### Файл: `application.properties`

```properties
# Auto-VACUUM после очистки данных
database.cleanup.auto-vacuum.enabled=true                    # Включить/выключить auto-vacuum
database.cleanup.auto-vacuum.threshold-records=1000000       # Порог срабатывания (1 млн записей)
database.cleanup.auto-vacuum.run-async=true                  # Асинхронное выполнение
```

### Параметры

| Параметр | Значение по умолчанию | Описание |
|----------|----------------------|----------|
| `enabled` | `true` | Включить автоматический VACUUM после очистки |
| `threshold-records` | `1000000` | Минимальное количество удаленных записей для запуска VACUUM |
| `run-async` | `true` | Асинхронное выполнение в фоновом режиме |

## Режимы работы

### 1. Асинхронный режим (рекомендуется)

```properties
database.cleanup.auto-vacuum.run-async=true
```

**Поведение:**
- ✅ VACUUM запускается в фоновом потоке `CompletableFuture`
- ✅ Результат очистки возвращается немедленно
- ✅ Приложение продолжает работать без задержек
- ✅ Прогресс отслеживается в логах

**Подходит для:**
- Продакшен среды
- Большие таблицы (> 10M строк)
- Когда нужна быстрая обратная связь пользователю

**Логи:**
```
INFO: Очистка завершена: удалено 5000000 записей за 120000 мс
INFO: Запуск автоматического VACUUM ANALYZE после удаления 5000000 записей
INFO: VACUUM ANALYZE запущен асинхронно в фоновом режиме
INFO: Выполнение VACUUM ANALYZE для таблицы: av_data
INFO: VACUUM ANALYZE завершен для av_data: 12500 мс
INFO: Автоматический VACUUM ANALYZE завершен: обработано 1 таблиц за 12 сек
```

### 2. Синхронный режим

```properties
database.cleanup.auto-vacuum.run-async=false
```

**Поведение:**
- ⏳ VACUUM выполняется до возврата результата очистки
- ⏳ Занимает ~10-30 минут для больших таблиц
- ✅ Гарантирует завершение VACUUM перед ответом
- ⚠️ Пользователь ждет окончания всех операций

**Подходит для:**
- Плановое обслуживание в ночное время
- Небольшие таблицы (< 1M строк)
- Когда критична гарантия выполнения VACUUM

## Сценарии использования

### Сценарий 1: Малые очистки (< 1 млн записей)

**Пример:** Удаление 500 тысяч старых записей

```
Удалено: 500,000 записей
Результат: VACUUM НЕ запускается автоматически
```

**Причина:**
- Порог не достигнут (`threshold-records=1000000`)
- PostgreSQL AutoVacuum справится автоматически
- Экономия ресурсов

### Сценарий 2: Средние очистки (1-10 млн записей)

**Пример:** Удаление 5 миллионов записей из av_data

```
Удалено: 5,000,000 записей
Результат: ✅ Запускается автоматический VACUUM ANALYZE
Время: ~15-20 минут (асинхронно)
Таблицы: av_data
```

**Эффект:**
- ✅ Обновлена статистика таблицы
- ✅ Dead tuples очищены
- ✅ Производительность запросов восстановлена
- ⚠️ Индексы остаются слегка раздутыми (рекомендуется REINDEX раз в месяц)

### Сценарий 3: Большие очистки (> 10 млн записей)

**Пример:** Удаление 20 миллионов записей из av_data + import_sessions

```
Удалено: 20,000,000 записей
Результат: ✅ Запускается автоматический VACUUM ANALYZE
Время: ~30-40 минут (асинхронно)
Таблицы: av_data, import_sessions
```

**Рекомендации после очистки:**
1. ✅ VACUUM ANALYZE выполняется автоматически
2. 🔧 Запустить REINDEX через UI `/maintenance/database` в выходные
3. 📊 Проверить bloat через "Анализ раздувания таблиц"

## Использование через UI

### Страница очистки: `/maintenance/database`

**Шаг 1: Выбор параметров очистки**
- Дата отсечки (cutoff date)
- Типы данных (AV_DATA, IMPORT_SESSIONS, и т.д.)
- Клиенты для исключения

**Шаг 2: Предпросмотр**
- Нажмите "Предпросмотр" для оценки количества записей
- Проверьте сколько будет удалено

**Шаг 3: Выполнение очистки**
- Нажмите "Удалить данные"
- Подтвердите действие
- Дождитесь результата (VACUUM запустится автоматически в фоне)

**Шаг 4: Мониторинг**
- Проверьте логи приложения для отслеживания прогресса VACUUM
- История очистки сохраняется в таблице `data_cleanup_history`

## Оценка времени выполнения

### Время VACUUM ANALYZE

Зависит от размера таблицы и количества удаленных записей:

| Размер таблицы | Удалено записей | Время VACUUM | Рекомендация |
|----------------|----------------|--------------|--------------|
| 1M строк | 100K-500K | 30 сек - 2 мин | Async OK |
| 10M строк | 1M-5M | 5-10 минут | Async OK |
| 50M строк | 5M-10M | 15-25 минут | Async рекомендуется |
| 100M строк | 10M-20M | 30-60 минут | Async обязателен |

### Время REINDEX (опционально, для bloat > 30%)

| Размер таблицы | Количество индексов | Время REINDEX CONCURRENTLY |
|----------------|--------------------|-----------------------------|
| 10M строк | 3 индекса | 15-30 минут |
| 50M строк | 3 индекса | 45-90 минут |
| 100M строк | 3 индекса | 90-180 минут |

## Настройка для разных окружений

### Продакшен (большие базы > 50M строк)

```properties
# Продакшен - асинхронный режим
database.cleanup.auto-vacuum.enabled=true
database.cleanup.auto-vacuum.threshold-records=1000000
database.cleanup.auto-vacuum.run-async=true
```

**Преимущества:**
- Не блокирует пользователей
- Оптимальная производительность
- Автоматическая оптимизация после очистки

### Staging/Dev (средние базы 10-50M строк)

```properties
# Staging - асинхронный режим с низким порогом
database.cleanup.auto-vacuum.enabled=true
database.cleanup.auto-vacuum.threshold-records=500000
database.cleanup.auto-vacuum.run-async=true
```

### Локальная разработка (малые базы < 10M строк)

```properties
# Dev - синхронный режим или отключен
database.cleanup.auto-vacuum.enabled=false
database.cleanup.auto-vacuum.threshold-records=1000000
database.cleanup.auto-vacuum.run-async=false
```

**Причина отключения:**
- Малый объем данных
- AutoVacuum PostgreSQL справится сам
- Экономия времени разработки

## Мониторинг и диагностика

### Проверка эффективности VACUUM

**1. Проверка статистики таблиц:**

```sql
SELECT
    schemaname,
    relname,
    n_live_tup AS live_tuples,
    n_dead_tup AS dead_tuples,
    round(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_pct,
    last_vacuum,
    last_autovacuum
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY n_dead_tup DESC;
```

**Хорошие показатели:**
- `dead_pct < 10%` - таблица в отличном состоянии
- `last_vacuum` - недавняя дата

**2. Проверка bloat (раздувания):**

Используйте UI `/maintenance/database` → раздел "Анализ раздувания таблиц"

**Критерии:**
- `bloat < 20%` - норма
- `bloat 20-30%` - рекомендуется мониторинг
- `bloat > 30%` - требуется REINDEX

### Логи приложения

**Успешное выполнение:**
```
[DataCleanupService] Очистка завершена: удалено 5000000 записей за 120000 мс
[DataCleanupService] Запуск автоматического VACUUM ANALYZE после удаления 5000000 записей
[DataCleanupService] VACUUM ANALYZE запущен асинхронно в фоновом режиме
[DataCleanupService] Выполнение VACUUM ANALYZE для таблицы: av_data
[DataCleanupService] VACUUM ANALYZE завершен для av_data: 12500 мс
[DataCleanupService] Автоматический VACUUM ANALYZE завершен: обработано 1 таблиц за 12 сек
```

**Ошибка выполнения:**
```
[DataCleanupService] Не удалось выполнить VACUUM ANALYZE для av_data: connection timeout
[DataCleanupService] Ошибка при асинхронном выполнении VACUUM ANALYZE
```

### История очистки

Все операции очистки сохраняются в таблице `data_cleanup_history`:

```sql
SELECT
    id,
    entity_type,
    cleanup_date,
    cutoff_date,
    records_deleted,
    execution_time_ms / 1000.0 AS execution_time_sec,
    status,
    initiated_by
FROM data_cleanup_history
ORDER BY cleanup_date DESC
LIMIT 10;
```

## REINDEX после больших очисток

### Когда нужен REINDEX?

**После очистки > 10 млн записей (> 20% данных таблицы):**

1. ✅ VACUUM ANALYZE выполнился автоматически
2. ⚠️ Индексы остаются раздутыми на 15-25%
3. 🔧 Рекомендуется REINDEX CONCURRENTLY раз в месяц

### Как запустить REINDEX?

**Способ 1: Через UI (рекомендуется)**

1. Откройте `/maintenance/database`
2. Прокрутите до раздела "Переиндексация"
3. Нажмите кнопку "REINDEX всех индексов"
4. Подтвердите действие
5. Дождитесь завершения (~45-90 минут для av_data с 50M строк)

**Способ 2: Через psql (для продвинутых)**

```sql
-- REINDEX CONCURRENTLY не блокирует таблицу
REINDEX INDEX CONCURRENTLY idx_av_data_client_created;
REINDEX INDEX CONCURRENTLY idx_av_data_operation_created;
REINDEX INDEX CONCURRENTLY idx_av_data_created_at_brin;
```

**Способ 3: Автоматический (планировщик)**

Используйте существующий планировщик:

```properties
# Ежемесячное полное обслуживание с REINDEX
maintenance.scheduler.full-maintenance.cron=0 0 4 1 * *
maintenance.scheduler.enabled=true
```

## Рекомендуемый график обслуживания

### Еженедельно (автоматически)

```properties
maintenance.scheduler.database-cleanup.cron=0 0 3 * * SUN
```

- ✅ Очистка старых данных
- ✅ Автоматический VACUUM ANALYZE (если > 1M записей)
- ✅ Проверка системы

### Ежемесячно (ручное или автоматическое)

- 🔧 Проверка bloat через UI
- 🔧 REINDEX при bloat > 30%
- 📊 Анализ производительности БД

### Раз в квартал

- 🔍 Полный аудит индексов (неиспользуемые индексы)
- 🔍 Анализ медленных запросов
- 🔍 Планирование capacity (рост данных)

## Часто задаваемые вопросы (FAQ)

### 1. Будет ли VACUUM блокировать приложение?

**Нет!** `VACUUM ANALYZE` НЕ блокирует таблицу:
- ✅ Чтение данных работает нормально
- ✅ Запись данных работает нормально
- ⚠️ Только `VACUUM FULL` блокирует таблицу (мы его НЕ используем)

### 2. Можно ли отключить auto-vacuum?

**Да**, если ваша база данных небольшая (< 10M строк):

```properties
database.cleanup.auto-vacuum.enabled=false
```

PostgreSQL AutoVacuum справится автоматически.

### 3. Сколько места освободит VACUUM?

**VACUUM НЕ освобождает место на диске!**
- ❌ Размер таблицы остается прежним
- ✅ Dead tuples помечаются для повторного использования
- ✅ Новые INSERT займут место dead tuples

**Для физического освобождения нужен:**
- `VACUUM FULL` (блокирует таблицу, 30-60 минут)
- Или экспорт → удаление таблицы → импорт (downtime)

### 4. Как часто нужен REINDEX?

**Зависит от интенсивности удалений:**
- Малые очистки (< 5% данных) → REINDEX раз в 3-6 месяцев
- Средние очистки (5-20% данных) → REINDEX раз в 1-2 месяца
- Большие очистки (> 20% данных) → REINDEX каждый месяц

**Ориентир:** Проверяйте bloat через UI, при > 30% делайте REINDEX.

### 5. Почему VACUUM выполняется только при > 1M записей?

**Оптимизация ресурсов:**
- VACUUM занимает 10-30 минут для больших таблиц
- Для малых очисток (< 1M) AutoVacuum PostgreSQL справится автоматически
- Порог можно настроить через `threshold-records`

### 6. Что если VACUUM завершился с ошибкой?

**Проверьте логи:**
```
[DataCleanupService] Ошибка при выполнении VACUUM ANALYZE
```

**Возможные причины:**
1. **Недостаточно прав** → Проверьте права пользователя БД
2. **Timeout подключения** → Увеличьте `spring.datasource.hikari.connection-timeout`
3. **Блокировка таблицы** → Подождите завершения других операций
4. **Нехватка места** → Освободите место на диске (PostgreSQL требует временное пространство)

**Решение:** Запустите VACUUM вручную через UI `/maintenance/database`

## Troubleshooting (Устранение проблем)

### Проблема 1: VACUUM не запускается автоматически

**Симптомы:**
- Удалено > 1M записей
- В логах нет сообщений о VACUUM

**Решение:**

1. Проверьте конфигурацию:
```properties
database.cleanup.auto-vacuum.enabled=true  # Должно быть true
```

2. Проверьте порог:
```properties
database.cleanup.auto-vacuum.threshold-records=1000000  # Удалено больше этого значения?
```

3. Проверьте статус очистки (должен быть SUCCESS):
```sql
SELECT * FROM data_cleanup_history ORDER BY cleanup_date DESC LIMIT 1;
```

### Проблема 2: VACUUM работает слишком долго

**Симптомы:**
- VACUUM выполняется > 60 минут
- Приложение тормозит

**Решение:**

1. Проверьте размер таблицы:
```sql
SELECT pg_size_pretty(pg_total_relation_size('av_data'));
```

2. Проверьте количество dead tuples:
```sql
SELECT n_dead_tup FROM pg_stat_user_tables WHERE relname = 'av_data';
```

3. **Если таблица огромная (> 100M строк):**
   - Увеличьте `maintenance_work_mem` в PostgreSQL
   - Или дождитесь завершения (это нормально)

### Проблема 3: После VACUUM запросы всё равно медленные

**Причина:** Index bloat > 30%

**Решение:**

1. Проверьте bloat через UI `/maintenance/database`
2. Если bloat > 30% → Запустите REINDEX
3. Проверьте статистику:
```sql
ANALYZE av_data;
```

## Архитектура решения

### Файлы и классы

**1. Конфигурация:**
- `application.properties` - параметры auto-vacuum

**2. Сервисы:**
- `DataCleanupService.java:155-157` - интеграция в executeCleanup()
- `DataCleanupService.java:599-677` - методы VACUUM

**3. Методы:**
- `performAutoVacuum()` - управление async/sync
- `executeVacuumAnalyze()` - выполнение VACUUM для всех таблиц
- `getTableNameForEntityType()` - маппинг типа на имя таблицы

### Поток выполнения

```
1. Пользователь запускает очистку через UI
   ↓
2. DataCleanupService.executeCleanup() удаляет записи
   ↓
3. Проверка: deletedRecords >= threshold?
   ↓ Да
4. performAutoVacuum() → запуск в async/sync режиме
   ↓
5. executeVacuumAnalyze() → для каждой таблицы
   ↓
6. VACUUM ANALYZE table_name (через JDBC)
   ↓
7. Логирование результата
```

## Дополнительные ресурсы

### Документация PostgreSQL

- [VACUUM](https://www.postgresql.org/docs/current/sql-vacuum.html)
- [ANALYZE](https://www.postgresql.org/docs/current/sql-analyze.html)
- [REINDEX](https://www.postgresql.org/docs/current/sql-reindex.html)
- [Routine Vacuuming](https://www.postgresql.org/docs/current/routine-vacuuming.html)

### Мониторинг в Zoomos v4

- UI обслуживания БД: `http://localhost:8081/maintenance/database`
- Анализ bloat: раздел "Анализ раздувания таблиц"
- История очистки: `SELECT * FROM data_cleanup_history`
- Логи приложения: `logs/spring.log`

---

**Версия документа:** 1.0
**Дата:** 2025-10-16
**Проект:** Zoomos v4
**Автор:** Claude Code
