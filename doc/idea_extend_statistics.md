# Идея: Расширение функционала статистики с фильтрацией по полям

## Дата создания
2025-10-04

## Автор
Разработано совместно с Claude Code (zoomos-orchestrator)

## Проблема

На странице сравнения экспортов (`src/main/resources/templates/statistics/results.html`) отображается статистика по группам (например, по сайтам: OZON, Wildberries, Yandex.Market), но **не учитывается детализация по другим полям** типа статуса товара.

### Текущая ситуация

**Настройки шаблона экспорта:**
- `statisticsGroupField`: `product_additional4` (сайты)
- `statisticsCountFields`: `["competitor_price", "competitorPromotionalPrice"]`
- `statisticsFilterFields`: `["competitorStockStatus"]` ← **это поле пока не используется для пересчета!**

**Результат на странице:**
```
Группа: OZON
├─ competitor_price: 150 ↓5%
└─ competitorPromotionalPrice: 120 ↑3%
```

**Проблема:** Цифра 150 включает ВСЕ товары независимо от их статуса (в наличии, нет в наличии, под заказ и т.д.)

## Требуемое решение

### Интерактивный фильтр на странице сравнения

Добавить возможность выбора значения из поля фильтрации, при выборе которого **все метрики пересчитываются** только для строк с выбранным значением.

### Пример использования

**Шаг 1:** На странице сравнения появляется панель фильтров

```
┌─────────────────────────────────────────────────────┐
│ 🔍 Фильтрация данных                                │
├─────────────────────────────────────────────────────┤
│ Поле: [competitorStockStatus ▼]                     │
│ Значение: [В наличии ▼]                             │
│ [Применить] [Сбросить]                              │
└─────────────────────────────────────────────────────┘
```

**Шаг 2:** Пользователь выбирает поле `competitorStockStatus` и значение `В наличии`

**Шаг 3:** После применения фильтра статистика пересчитывается:

```
Группа: OZON
├─ competitor_price: 100 ↓3%  ← только товары "В наличии"
└─ competitorPromotionalPrice: 90 ↑2%

Группа: Wildberries
├─ competitor_price: 150 ↓1%
└─ competitorPromotionalPrice: 140 ↑1%
```

**Шаг 4:** При смене фильтра на `Нет в наличии`:

```
Группа: OZON
├─ competitor_price: 30 ↓10%  ← другие значения
└─ competitorPromotionalPrice: 20 ↓5%
```

**Шаг 5:** Кнопка "Сбросить" возвращает к общей статистике без фильтрации

## Технические требования

### 1. Универсальность значений
- Значения в поле фильтрации могут быть **произвольными** (не только "В наличии"/"Нет в наличии")
- Примеры: статусы (цифры, слова, фразы), категории, типы, коды и т.д.
- Система должна **автоматически определять** уникальные значения из данных экспорта

### 2. Ограничения
- **Количество уникальных значений:** до 10 (в среднем 3-5)
- **Множественные фильтры:** НЕ нужны (только один фильтр одновременно)
- **Визуальное выделение:** НЕ требуется специальная подсветка активного фильтра
- **Пересчет:** происходит на стороне сервера

### 3. Производительность
- При применении фильтра происходит новый запрос к серверу с параметрами фильтрации
- Данные уже предварительно агрегированы при экспорте

## Выбранное архитектурное решение

### Вариант А: Многомерная статистика (ВЫБРАНО)

При сохранении экспорта создавать статистику для **всех комбинаций** значений полей фильтрации.

#### Структура данных в `export_statistics`

**Расширенная схема (миграция V15):**
```sql
ALTER TABLE export_statistics
    ADD COLUMN filter_field_name VARCHAR(255),   -- например: "competitorStockStatus"
    ADD COLUMN filter_field_value VARCHAR(255);  -- например: "В наличии"

CREATE INDEX idx_export_statistics_filter
    ON export_statistics(export_session_id, filter_field_name, filter_field_value);
```

#### Пример данных

**При экспорте с настройками:**
- `statisticsGroupField`: `product_additional4` (сайты: OZON, Wildberries)
- `statisticsCountFields`: `["competitor_price", "competitorPromotionalPrice"]`
- `statisticsFilterFields`: `["competitorStockStatus"]`

**Сохраняются записи в таблице `export_statistics`:**

| id | export_session_id | group_value | count_field | filter_field_name | filter_field_value | count_value |
|----|-------------------|-------------|-------------|-------------------|-------------------|-------------|
| 1  | 123               | OZON        | competitor_price | NULL | NULL | 150 |
| 2  | 123               | OZON        | competitor_price | competitorStockStatus | В наличии | 100 |
| 3  | 123               | OZON        | competitor_price | competitorStockStatus | Нет в наличии | 30 |
| 4  | 123               | OZON        | competitor_price | competitorStockStatus | Под заказ | 20 |
| 5  | 123               | OZON        | competitorPromotionalPrice | NULL | NULL | 120 |
| 6  | 123               | OZON        | competitorPromotionalPrice | competitorStockStatus | В наличии | 90 |
| 7  | 123               | OZON        | competitorPromotionalPrice | competitorStockStatus | Нет в наличии | 20 |
| 8  | 123               | OZON        | competitorPromotionalPrice | competitorStockStatus | Под заказ | 10 |
| 9  | 123               | Wildberries | competitor_price | NULL | NULL | 200 |
| 10 | 123               | Wildberries | competitor_price | competitorStockStatus | В наличии | 150 |
| 11 | 123               | Wildberries | competitor_price | competitorStockStatus | Нет в наличии | 50 |
| ...| ...               | ...         | ...         | ...               | ...               | ... |

**Логика запросов:**
- **БЕЗ фильтра (общая статистика):** `WHERE filter_field_name IS NULL`
- **С фильтром:** `WHERE filter_field_name = 'competitorStockStatus' AND filter_field_value = 'В наличии'`

#### Преимущества выбранного решения
✅ **Быстрые запросы** - данные уже предварительно агрегированы
✅ **Простота** - на фронтенде просто меняем параметры запроса
✅ **Сравнение** - можно сравнивать отклонения между операциями для конкретных значений фильтра
✅ **Эффективность** - не требует хранения полных данных экспорта

#### Недостатки
⚠️ Увеличение количества записей в БД
- Но при 10 значениях × 3 сайта × 2 метрики = 60 записей вместо 6 - приемлемо

---

## Детальный план реализации

### Этап 1: Расширение модели данных

#### Файл 1: Миграция БД
**Путь:** `src/main/resources/db/migration/V15__add_filter_fields_to_export_statistics.sql`

```sql
-- V15__add_filter_fields_to_export_statistics.sql
-- Добавляем поля для многомерной фильтрации статистики

ALTER TABLE export_statistics
    ADD COLUMN filter_field_name VARCHAR(255),
    ADD COLUMN filter_field_value VARCHAR(255);

-- Индекс для быстрого поиска отфильтрованных данных
CREATE INDEX idx_export_statistics_filter
    ON export_statistics(export_session_id, filter_field_name, filter_field_value);

-- Комментарии для документации
COMMENT ON COLUMN export_statistics.filter_field_name IS 'Название поля фильтрации (например, competitorStockStatus)';
COMMENT ON COLUMN export_statistics.filter_field_value IS 'Значение фильтра (например, "В наличии"). NULL означает общую статистику без фильтра';
```

#### Файл 2: Сущность ExportStatistics
**Путь:** `src/main/java/com/java/model/entity/ExportStatistics.java`

**Изменения (добавить поля после строки 46):**
```java
@Column(name = "filter_field_name")
private String filterFieldName;

@Column(name = "filter_field_value")
private String filterFieldValue;
```

### Этап 2: Расширение логики сохранения статистики

**Файл:** `src/main/java/com/java/service/exports/ExportStatisticsWriterService.java`

**Изменения в методе `saveExportStatistics` (строки 86-116):**

Текущий код создает только общую статистику. Нужно добавить создание детализированной статистики для каждого значения полей фильтрации.

**Новые вспомогательные методы (добавить в конец класса):**

```java
/**
 * Получает уникальные значения указанного поля в группе данных
 */
private Set<String> getUniqueFilterValues(List<Map<String, Object>> groupRows, String filterColumnName) {
    Set<String> uniqueValues = new LinkedHashSet<>();

    for (Map<String, Object> row : groupRows) {
        Object value = row.get(filterColumnName);
        if (value != null) {
            String stringValue = value.toString().trim();
            if (!stringValue.isEmpty() && !"null".equalsIgnoreCase(stringValue)) {
                uniqueValues.add(stringValue);
            }
        }
    }

    return uniqueValues;
}

/**
 * Фильтрует данные по значению указанного поля
 */
private List<Map<String, Object>> filterDataByValue(List<Map<String, Object>> data,
                                                     String filterColumnName,
                                                     String filterValue) {
    return data.stream()
        .filter(row -> {
            Object value = row.get(filterColumnName);
            if (value == null) return false;
            return filterValue.equals(value.toString().trim());
        })
        .collect(Collectors.toList());
}
```

**Модификация основного цикла сохранения (заменить строки 86-116):**

```java
// Получаем поля фильтрации из шаблона
List<String> filterFields = parseJsonStringList(template.getStatisticsFilterFields());

// Для каждой группы подсчитываем статистику
List<ExportStatistics> statisticsToSave = new ArrayList<>();

for (Map.Entry<String, List<Map<String, Object>>> groupEntry : groupedData.entrySet()) {
    String groupValue = groupEntry.getKey();
    List<Map<String, Object>> groupRows = groupEntry.getValue();

    log.debug("Обработка группы '{}' с {} строками", groupValue, groupRows.size());

    // Для каждого поля подсчета
    for (String countField : countFields) {
        String countColumnName = getExportColumnName(countField, fieldMapping);

        // 1. Сохраняем ОБЩУЮ статистику (БЕЗ фильтра)
        long totalCount = countNonEmptyValues(groupRows, countColumnName);

        ExportStatistics generalStats = ExportStatistics.builder()
                .exportSession(session)
                .groupFieldName(groupField)
                .groupFieldValue(groupValue)
                .countFieldName(countField)
                .countValue(totalCount)
                .totalRecordsCount((long) groupRows.size())
                .dateModificationsCount(0L)
                .modificationType("STANDARD")
                .filterFieldName(null)  // NULL = общая статистика
                .filterFieldValue(null)
                .build();

        statisticsToSave.add(generalStats);

        log.debug("Группа '{}', поле '{}': общая статистика = {} значений",
                  groupValue, countField, totalCount);

        // 2. Сохраняем ДЕТАЛИЗИРОВАННУЮ статистику (по каждому полю фильтрации)
        for (String filterField : filterFields) {
            String filterColumnName = getExportColumnName(filterField, fieldMapping);

            // Получаем уникальные значения фильтра в этой группе
            Set<String> uniqueFilterValues = getUniqueFilterValues(groupRows, filterColumnName);

            log.debug("Группа '{}', фильтр '{}': найдено {} уникальных значений",
                      groupValue, filterField, uniqueFilterValues.size());

            // Для каждого уникального значения фильтра
            for (String filterValue : uniqueFilterValues) {
                // Фильтруем строки по этому значению
                List<Map<String, Object>> filteredRows = filterDataByValue(
                    groupRows, filterColumnName, filterValue
                );

                // Пересчитываем метрику для отфильтрованных данных
                long filteredCount = countNonEmptyValues(filteredRows, countColumnName);

                ExportStatistics filteredStats = ExportStatistics.builder()
                        .exportSession(session)
                        .groupFieldName(groupField)
                        .groupFieldValue(groupValue)
                        .countFieldName(countField)
                        .countValue(filteredCount)
                        .totalRecordsCount((long) filteredRows.size())
                        .dateModificationsCount(0L)
                        .modificationType("STANDARD")
                        .filterFieldName(filterField)
                        .filterFieldValue(filterValue)
                        .build();

                statisticsToSave.add(filteredStats);

                log.debug("Группа '{}', поле '{}', фильтр '{}'='{}': {} значений из {} строк",
                          groupValue, countField, filterField, filterValue,
                          filteredCount, filteredRows.size());
            }
        }
    }
}
```

### Этап 3: Расширение Repository

**Файл:** `src/main/java/com/java/repository/ExportStatisticsRepository.java`

**Добавить новые методы:**

```java
/**
 * Получить общую статистику (без фильтра) для указанных сессий
 */
@Query("SELECT es FROM ExportStatistics es WHERE " +
       "es.exportSession.id IN :sessionIds AND " +
       "es.filterFieldName IS NULL")
List<ExportStatistics> findBySessionIdsWithoutFilter(
    @Param("sessionIds") List<Long> sessionIds
);

/**
 * Получить отфильтрованную статистику для указанных сессий
 */
@Query("SELECT es FROM ExportStatistics es WHERE " +
       "es.exportSession.id IN :sessionIds AND " +
       "es.filterFieldName = :filterField AND " +
       "es.filterFieldValue = :filterValue")
List<ExportStatistics> findBySessionIdsAndFilter(
    @Param("sessionIds") List<Long> sessionIds,
    @Param("filterField") String filterFieldName,
    @Param("filterValue") String filterFieldValue
);

/**
 * Получить уникальные значения для конкретного поля фильтрации
 */
@Query("SELECT DISTINCT es.filterFieldValue FROM ExportStatistics es WHERE " +
       "es.exportSession.id IN :sessionIds AND " +
       "es.filterFieldName = :filterField AND " +
       "es.filterFieldValue IS NOT NULL " +
       "ORDER BY es.filterFieldValue")
List<String> findDistinctFilterValues(
    @Param("sessionIds") List<Long> sessionIds,
    @Param("filterField") String filterFieldName
);

/**
 * Получить все уникальные поля фильтрации для указанных сессий
 */
@Query("SELECT DISTINCT es.filterFieldName FROM ExportStatistics es WHERE " +
       "es.exportSession.id IN :sessionIds AND " +
       "es.filterFieldName IS NOT NULL " +
       "ORDER BY es.filterFieldName")
List<String> findDistinctFilterFields(
    @Param("sessionIds") List<Long> sessionIds
);
```

### Этап 4: Обновление сервиса статистики

**Файл:** `src/main/java/com/java/service/statistics/ExportStatisticsService.java`

**Модификация метода `calculateComparison` (строка 32):**

```java
/**
 * Вычисляет статистику для выбранных операций экспорта
 *
 * @param request параметры сравнения
 * @param filterFieldName название поля фильтрации (может быть null)
 * @param filterFieldValue значение фильтра (может быть null)
 */
public List<StatisticsComparisonDto> calculateComparison(
        StatisticsRequestDto request,
        String filterFieldName,
        String filterFieldValue) {

    log.info("Расчет статистики для операций: {}, фильтр: {}={}",
             request.getExportSessionIds(), filterFieldName, filterFieldValue);

    // Получаем шаблон
    ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(request.getTemplateId())
            .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

    if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
        throw new IllegalArgumentException("Статистика не включена для данного шаблона");
    }

    // Получаем сессии экспорта
    List<ExportSession> sessions = getExportSessions(request.getExportSessionIds(), template);
    if (sessions.isEmpty()) {
        log.warn("Нет сессий экспорта для анализа");
        return Collections.emptyList();
    }

    // Получаем статистику в зависимости от наличия фильтра
    List<Long> sessionIds = sessions.stream().map(ExportSession::getId).toList();
    List<ExportStatistics> allStatistics;

    if (filterFieldName != null && filterFieldValue != null) {
        // Получаем отфильтрованную статистику
        allStatistics = statisticsRepository.findBySessionIdsAndFilter(
            sessionIds, filterFieldName, filterFieldValue
        );
        log.debug("Загружено {} записей отфильтрованной статистики", allStatistics.size());
    } else {
        // Получаем общую статистику (без фильтра)
        allStatistics = statisticsRepository.findBySessionIdsWithoutFilter(sessionIds);
        log.debug("Загружено {} записей общей статистики", allStatistics.size());
    }

    if (allStatistics.isEmpty()) {
        log.warn("Нет сохранённой статистики для сессий: {}", sessionIds);
        return Collections.emptyList();
    }

    // Группируем статистику по значению группировки
    Map<String, List<ExportStatistics>> statisticsByGroup = allStatistics.stream()
            .collect(Collectors.groupingBy(ExportStatistics::getGroupFieldValue));

    // Создаем сравнительную статистику
    return createComparison(statisticsByGroup, sessions, request, template);
}
```

**Добавить перегрузку метода для обратной совместимости:**

```java
/**
 * Вычисляет статистику БЕЗ фильтра (для обратной совместимости)
 */
public List<StatisticsComparisonDto> calculateComparison(StatisticsRequestDto request) {
    return calculateComparison(request, null, null);
}
```

### Этап 5: Новый API эндпоинт

**Файл:** `src/main/java/com/java/controller/StatisticsController.java`

**Добавить новый метод:**

```java
/**
 * API для получения доступных полей и их значений для фильтрации
 *
 * @param templateId ID шаблона экспорта
 * @param sessionIds список ID сессий экспорта
 * @return Map где ключ = название поля, значение = список уникальных значений
 *
 * Пример ответа:
 * {
 *   "competitorStockStatus": ["В наличии", "Нет в наличии", "Под заказ"],
 *   "product_category": ["Виски", "Коньяк", "Водка"]
 * }
 */
@GetMapping("/statistics/filter-values")
@ResponseBody
public Map<String, List<String>> getFilterValues(
    @RequestParam Long templateId,
    @RequestParam List<Long> sessionIds
) {
    log.info("Запрос значений фильтров для шаблона {} и сессий {}", templateId, sessionIds);

    // Получаем шаблон
    ExportTemplate template = templateRepository.findById(templateId)
        .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

    // Получаем поля фильтрации из настроек шаблона
    List<String> filterFields = parseJsonStringList(template.getStatisticsFilterFields());

    if (filterFields.isEmpty()) {
        log.debug("Нет полей фильтрации в шаблоне {}", templateId);
        return Collections.emptyMap();
    }

    Map<String, List<String>> result = new HashMap<>();

    // Для каждого поля фильтрации получаем уникальные значения
    for (String filterField : filterFields) {
        List<String> values = statisticsRepository.findDistinctFilterValues(
            sessionIds, filterField
        );

        if (!values.isEmpty()) {
            result.put(filterField, values);
            log.debug("Поле '{}': найдено {} уникальных значений", filterField, values.size());
        }
    }

    log.info("Найдено {} полей фильтрации с данными", result.size());
    return result;
}

/**
 * Вспомогательный метод для парсинга JSON списка строк
 */
private List<String> parseJsonStringList(String json) {
    if (json == null || json.trim().isEmpty()) {
        return new ArrayList<>();
    }

    try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
        log.error("Ошибка парсинга JSON списка: {}", json, e);
        return new ArrayList<>();
    }
}
```

**Модификация метода `showResults` (добавить параметры фильтра):**

```java
@GetMapping("/statistics/results")
public String showResults(
    @RequestParam Long clientId,
    @RequestParam Long templateId,
    @RequestParam List<Long> exportSessionIds,
    @RequestParam Integer warningPercentage,
    @RequestParam Integer criticalPercentage,
    @RequestParam(required = false) String filterField,   // НОВЫЙ параметр
    @RequestParam(required = false) String filterValue,   // НОВЫЙ параметр
    Model model
) {
    log.info("Запрос результатов статистики: clientId={}, templateId={}, сессий={}, фильтр={}:{}",
             clientId, templateId, exportSessionIds.size(), filterField, filterValue);

    // Создаем запрос
    StatisticsRequestDto request = StatisticsRequestDto.builder()
        .templateId(templateId)
        .exportSessionIds(exportSessionIds)
        .warningPercentage(warningPercentage)
        .criticalPercentage(criticalPercentage)
        .build();

    // Вычисляем статистику с учетом фильтра
    List<StatisticsComparisonDto> comparison =
        statisticsService.calculateComparison(request, filterField, filterValue);

    model.addAttribute("comparison", comparison);
    model.addAttribute("clientId", clientId);
    model.addAttribute("templateId", templateId);
    model.addAttribute("sessionIds", exportSessionIds);
    model.addAttribute("warningPercentage", warningPercentage);
    model.addAttribute("criticalPercentage", criticalPercentage);

    return "statistics/results";
}
```

### Этап 6: Обновление UI

**Файл:** `src/main/resources/templates/statistics/results.html`

**Добавить панель фильтров после строки 385 (перед таблицей статистики):**

```html
<!-- Панель фильтрации данных -->
<div th:unless="${#lists.isEmpty(comparison)}"
     id="dynamicFilterPanel"
     class="mb-3"
     style="display: none;">
    <div class="card">
        <div class="card-header bg-light">
            <h6 class="mb-0">
                <i class="fas fa-filter me-2"></i>Фильтрация данных
            </h6>
        </div>
        <div class="card-body">
            <div class="row align-items-end">
                <div class="col-md-4">
                    <label for="filterFieldSelect" class="form-label">Поле для фильтрации</label>
                    <select id="filterFieldSelect" class="form-select">
                        <option value="">-- Выберите поле --</option>
                        <!-- Заполняется динамически через JavaScript -->
                    </select>
                    <small class="text-muted">Выберите поле по которому будет фильтрация</small>
                </div>

                <div class="col-md-4">
                    <label for="filterValueSelect" class="form-label">Значение</label>
                    <select id="filterValueSelect" class="form-select" disabled>
                        <option value="">-- Сначала выберите поле --</option>
                        <!-- Заполняется динамически при выборе поля -->
                    </select>
                    <small class="text-muted">Выберите значение для фильтрации</small>
                </div>

                <div class="col-md-4">
                    <button id="applyFilterBtn" class="btn btn-primary" disabled>
                        <i class="fas fa-check me-1"></i>Применить
                    </button>
                    <button id="resetFilterBtn" class="btn btn-secondary ms-2" style="display: none;">
                        <i class="fas fa-times me-1"></i>Сбросить
                    </button>
                </div>
            </div>

            <!-- Индикатор активного фильтра -->
            <div id="activeFilterIndicator" class="mt-3 alert alert-info" style="display: none;">
                <i class="fas fa-info-circle me-2"></i>
                Активен фильтр: <strong id="activeFilterText"></strong>
            </div>
        </div>
    </div>
</div>
```

**Добавить JavaScript в конец секции `<script>` (перед закрывающим тегом):**

```javascript
// ==================== ФИЛЬТРАЦИЯ СТАТИСТИКИ ====================

// Инициализация фильтров при загрузке страницы
document.addEventListener('DOMContentLoaded', async () => {
    await loadFilterFields();
    checkActiveFilter();
});

/**
 * Загрузка доступных полей и значений фильтрации с сервера
 */
async function loadFilterFields() {
    const templateId = /*[[${templateId}]]*/ null;
    const sessionIds = /*[[${sessionIds}]]*/ [];

    if (!templateId || !sessionIds || sessionIds.length === 0) {
        console.log('Нет данных для загрузки фильтров');
        return;
    }

    try {
        const response = await fetch(
            `/statistics/filter-values?templateId=${templateId}&sessionIds=${sessionIds.join(',')}`
        );

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const filterData = await response.json();
        console.log('Загружены данные фильтров:', filterData);

        // Если есть поля для фильтрации - показываем панель
        if (Object.keys(filterData).length > 0) {
            document.getElementById('dynamicFilterPanel').style.display = 'block';

            const fieldSelect = document.getElementById('filterFieldSelect');

            // Заполняем список полей
            Object.entries(filterData).forEach(([field, values]) => {
                const option = document.createElement('option');
                option.value = field;
                option.textContent = field;
                option.dataset.values = JSON.stringify(values);
                fieldSelect.appendChild(option);
            });

            // Обработчик выбора поля
            fieldSelect.addEventListener('change', onFilterFieldChange);
        } else {
            console.log('Нет полей для фильтрации в шаблоне');
        }
    } catch (error) {
        console.error('Ошибка загрузки фильтров:', error);
    }
}

/**
 * Обработчик выбора поля фильтрации
 */
function onFilterFieldChange(event) {
    const valueSelect = document.getElementById('filterValueSelect');
    const applyBtn = document.getElementById('applyFilterBtn');
    const selectedOption = event.target.selectedOptions[0];

    if (selectedOption && selectedOption.dataset.values) {
        const values = JSON.parse(selectedOption.dataset.values);

        // Очищаем и заполняем список значений
        valueSelect.innerHTML = '<option value="">-- Выберите значение --</option>';

        values.forEach(value => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = value;
            valueSelect.appendChild(option);
        });

        valueSelect.disabled = false;

        // Обработчик выбора значения
        valueSelect.addEventListener('change', () => {
            applyBtn.disabled = valueSelect.value === '';
        });
    } else {
        valueSelect.innerHTML = '<option value="">-- Сначала выберите поле --</option>';
        valueSelect.disabled = true;
        applyBtn.disabled = true;
    }
}

/**
 * Применение фильтра (перезагрузка страницы с параметрами)
 */
document.getElementById('applyFilterBtn').addEventListener('click', () => {
    const filterField = document.getElementById('filterFieldSelect').value;
    const filterValue = document.getElementById('filterValueSelect').value;

    if (filterField && filterValue) {
        console.log('Применение фильтра:', filterField, '=', filterValue);

        // Добавляем параметры фильтра к URL и перезагружаем страницу
        const url = new URL(window.location.href);
        url.searchParams.set('filterField', filterField);
        url.searchParams.set('filterValue', filterValue);
        window.location.href = url.toString();
    }
});

/**
 * Сброс фильтра (возврат к общей статистике)
 */
document.getElementById('resetFilterBtn').addEventListener('click', () => {
    console.log('Сброс фильтра');

    const url = new URL(window.location.href);
    url.searchParams.delete('filterField');
    url.searchParams.delete('filterValue');
    window.location.href = url.toString();
});

/**
 * Проверка и отображение активного фильтра при загрузке
 */
function checkActiveFilter() {
    const urlParams = new URLSearchParams(window.location.search);
    const filterField = urlParams.get('filterField');
    const filterValue = urlParams.get('filterValue');

    if (filterField && filterValue) {
        // Показываем индикатор активного фильтра
        const indicator = document.getElementById('activeFilterIndicator');
        const text = document.getElementById('activeFilterText');
        const resetBtn = document.getElementById('resetFilterBtn');

        text.textContent = `${filterField} = "${filterValue}"`;
        indicator.style.display = 'block';
        resetBtn.style.display = 'inline-block';

        console.log('Активен фильтр:', filterField, '=', filterValue);
    }
}
```

---

## Сценарий использования

### 1. Настройка шаблона экспорта

1. Открыть форму редактирования шаблона экспорта
2. В секции "Настройки статистики":
   - ✅ Включить статистику для этого шаблона
   - **Поля для подсчета:** `competitor_price`, `competitorPromotionalPrice`
   - **Поле для группировки:** `product_additional4`
   - **Поля для фильтрации:** `competitorStockStatus` ← ✅ ВАЖНО!
3. Сохранить шаблон

### 2. Выполнение экспорта

1. Запустить экспорт с этим шаблоном
2. Система автоматически:
   - Находит уникальные значения в `competitorStockStatus` (например: "В наличии", "Нет в наличии", "Под заказ")
   - Создает общую статистику для каждой группы (сайта)
   - Создает детализированную статистику для каждого значения фильтра
   - Сохраняет в `export_statistics` с полями `filter_field_name` и `filter_field_value`

### 3. Просмотр и фильтрация статистики

1. Открыть страницу "Сравнение экспортов"
2. Увидеть общую статистику (все товары)
3. Увидеть панель "Фильтрация данных" (если в шаблоне настроены поля фильтрации)
4. Выбрать:
   - **Поле:** `competitorStockStatus`
   - **Значение:** `В наличии`
5. Нажать "Применить"
6. Страница перезагружается → все метрики пересчитаны только для товаров "В наличии"
7. Можно изменить фильтр или нажать "Сбросить" для возврата к общей статистике

---

## Тестирование

### Тест 1: Сохранение многомерной статистики

**Подготовка:**
- Создать шаблон с настройками фильтрации
- Подготовить тестовые данные с разными статусами

**Выполнение:**
- Запустить экспорт

**Ожидаемый результат:**
```sql
SELECT
    group_field_value,
    count_field_name,
    filter_field_name,
    filter_field_value,
    count_value
FROM export_statistics
WHERE export_session_id = [ID_СЕССИИ]
ORDER BY group_field_value, count_field_name, filter_field_value;
```

Должны быть записи:
- OZON, competitor_price, NULL, NULL, 150 (общая)
- OZON, competitor_price, competitorStockStatus, "В наличии", 100
- OZON, competitor_price, competitorStockStatus, "Нет в наличии", 30
- OZON, competitor_price, competitorStockStatus, "Под заказ", 20
- и т.д.

### Тест 2: API получения значений фильтров

**Запрос:**
```
GET /statistics/filter-values?templateId=1&sessionIds=123,124,125
```

**Ожидаемый ответ:**
```json
{
  "competitorStockStatus": [
    "В наличии",
    "Нет в наличии",
    "Под заказ"
  ]
}
```

### Тест 3: Фильтрация на UI

**Действия:**
1. Открыть страницу сравнения
2. Убедиться, что панель фильтров видна
3. Выбрать поле и значение
4. Нажать "Применить"
5. Проверить, что URL изменился: `?filterField=competitorStockStatus&filterValue=В%20наличии`
6. Проверить, что данные пересчитаны корректно

### Тест 4: Сброс фильтра

**Действия:**
1. Применить фильтр
2. Нажать "Сбросить"
3. Проверить, что параметры фильтра удалены из URL
4. Проверить, что показана общая статистика

---

## Оценка трудозатрат

| Этап | Описание | Время |
|------|----------|-------|
| 1 | Создание миграции БД | 30 мин |
| 2 | Обновление сущности ExportStatistics | 15 мин |
| 3 | Расширение ExportStatisticsWriterService | 2-3 часа |
| 4 | Новые методы в Repository | 30 мин |
| 5 | Обновление ExportStatisticsService | 1 час |
| 6 | Новый API эндпоинт в контроллере | 1 час |
| 7 | UI - панель фильтров + JavaScript | 2-3 часа |
| 8 | Тестирование и отладка | 2-3 часа |
| **Итого** | | **10-13 часов** |

---

## Возможные расширения (будущие версии)

1. **Множественные фильтры:** выбор нескольких фильтров одновременно (статус И категория)
2. **Экспорт отфильтрованных данных:** кнопка "Экспорт в Excel" с учетом активного фильтра
3. **История фильтрации:** сохранение последнего использованного фильтра для пользователя
4. **Визуальная аналитика:** графики распределения по значениям фильтра
5. **Сравнение фильтров:** одновременный просмотр двух разных фильтров в двух колонках

---

## Статус
**✅ УТВЕРЖДЕНО** - готово к реализации
