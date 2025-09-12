# Исправление поведения фильтров на странице результатов статистики

## Обзор

Страница результатов статистики (`results.html`) имеет функциональность фильтрации, которая в настоящее время содержит критическую проблему: при применении быстрых фильтров все данные скрываются вместо правильной фильтрации контента. Данный документ описывает анализ проблемы и решение для исправления поведения фильтров.

## Анализ проблемы

### Текущая проблема
- **Быстрые фильтры не работают**: Когда пользователи применяют быстрые фильтры (такие как "Только предупреждения", "Скрыть без изменений", "Только проблемы" и т.д.), все строки таблицы скрываются вместо отображения отфильтрованных результатов
- **Причина**: JavaScript логика фильтрации неправильно идентифицирует и оценивает ячейки метрик и их значения изменений
- **Влияние**: Пользователи не могут эффективно фильтровать данные статистики, делая страницу результатов непригодной для анализа

### Затронутые типы фильтров
1. **Панель быстрых фильтров**:
   - "Только предупреждения" - показывает записи с изменениями в диапазоне [currentWarningThreshold, currentCriticalThreshold)
   - "Скрыть без изменений" - скрывает записи с изменением = 0%
   - "Только проблемы" - показывает записи с изменениями >= currentWarningThreshold%
   - "Большие изменения" - показывает записи с изменениями >= currentCriticalThreshold%

2. **Панель продвинутых фильтров**:
   - Фильтрация по уровням предупреждений (NORMAL/WARNING/CRITICAL)
   - Фильтрация по диапазону процента изменений
   - Фильтрация по полям группировки
   - Дополнительные условия

### Уровни отклонений и пороговые значения

**Определение уровней**:
- **NORMAL**: изменения < currentWarningThreshold%
- **WARNING**: currentWarningThreshold% <= изменения < currentCriticalThreshold%  
- **CRITICAL**: изменения >= currentCriticalThreshold%

**Источник пороговых значений**:
Пороговые значения **обязательно** берутся из глобальных JavaScript переменных:
- `currentWarningThreshold` - порог предупреждения (инициализируется из серверных данных)
- `currentCriticalThreshold` - критический порог (инициализируется из серверных данных)

**Важно**: Эти переменные:
1. Инициализируются при загрузке страницы из Thymeleaf модели: `/*[[${warningPercentage}]]*/` и `/*[[${criticalPercentage}]]*/`
2. Могут быть изменены пользователем через панель настроек
3. Должны использоваться во всех функциях фильтрации для обеспечения консистентности
4. Обновляются функцией `applyThresholds()` при изменении пользователем

## Анализ архитектуры

### Текущая реализация фильтров

#### Логика клиентской фильтрации
```javascript
// Основная функция фильтрации в results.html
function applyFilters() {
    const rows = document.querySelectorAll('.statistics-table tbody tr');
    // Логика управления группами
    groups.forEach(group => {
        group.metricRows.forEach(row => {
            // Логика оценки фильтров - ПРОБЛЕМНАЯ ОБЛАСТЬ
            const metricCells = Array.from(row.querySelectorAll('td')).filter(cell => 
                !cell.classList.contains('group-header') && 
                !cell.hasAttribute('rowspan') &&
                cell.querySelector('.metric-value')
            );
            
            // Берет ячейку последнего столбца для оценки
            const lastColumnCell = metricCells[metricCells.length - 1];
            
            // Получает процент изменения из атрибута data-change
            const changeSpan = lastColumnCell.querySelector('.metric-change span[data-change]');
            const changeValue = changeSpan ? Math.abs(parseFloat(changeSpan.getAttribute('data-change')) || 0) : 0;
        });
    });
}
```

#### Серверная структура данных
```html
<!-- Структура таблицы статистики в results.html -->
<td th:each="colOperation : ${comparison[0].operations}">
    <div th:class="классы стилизации метрик">
        <div class="metric-value" th:text="${metricValue.currentValue}">100</div>
        <div class="metric-change">
            <span th:attr="data-change=${metricValue.changePercentage}"
                  th:classappend="${'change-' + metricValue.changeType.name().toLowerCase()}">
                <!-- Отображение процента изменения -->
            </span>
        </div>
    </div>
</td>
```

### Поддержка фильтров на бэкенде
Контроллер `ExportStatisticsController.java` предоставляет:
- `/statistics/api/filtered-analyze` - API серверной фильтрации
- `StatisticsFilterDto` - DTO критериев фильтрации
- `calculateFilteredComparison()` - Сервисный метод для фильтрации

## Анализ первопричин

### Проблемы JavaScript фильтрации

1. **Неправильная логика выбора ячеек**:
   ```javascript
   // ПРОБЛЕМА: Этот селектор может не найти правильные ячейки метрик
   const metricCells = Array.from(row.querySelectorAll('td')).filter(cell => 
       !cell.classList.contains('group-header') && 
       !cell.hasAttribute('rowspan') &&
       cell.querySelector('.metric-value')  // Этот запрос может не сработать
   );

   // ДОПОЛНИТЕЛЬНАЯ ПРОБЛЕМА: Использование неактуальных пороговых значений
   // Фильтры должны использовать currentWarningThreshold и currentCriticalThreshold
   ```

2. **Проблемы доступа к атрибутам данных**:
   ```javascript
   // ПРОБЛЕМА: атрибут data-change может не существовать или быть null
   const changeSpan = lastColumnCell.querySelector('.metric-change span[data-change]');
   const changeValue = changeSpan ? Math.abs(parseFloat(changeSpan.getAttribute('data-change')) || 0) : 0;
   ```

3. **Проблемы обнаружения CSS классов**:
   ```javascript
   // ПРОБЛЕМА: CSS классы для уровней предупреждений могут быть неправильно применены
   // Фильтрация должна использовать пороги из currentWarningThreshold и currentCriticalThreshold
   if (filterState.onlyAlerts && shouldShow) {
       const isWarning = changeValue >= currentWarningThreshold && changeValue < currentCriticalThreshold;
       if (!isWarning) {
           shouldShow = false; // Это может всегда быть false из-за неправильной логики
       }
   }
   ```

4. **Управление строками групп**:
   ```javascript
   // ПРОБЛЕМА: Логика видимости заголовков групп может скрыть все группы
   groups.forEach(group => {
       group.headerRow.style.display = groupHasMatchingRows ? '' : 'none';
       if (!groupHasMatchingRows) {
           hiddenCount++; // Может привести к скрытию всех групп
       }
   });
   ```

## Дизайн решения

### 1. Исправление JavaScript логики фильтров

#### A. Инициализация и использование пороговых значений
```javascript
// Глобальные переменные для пороговых значений (инициализируются из Thymeleaf)
let currentWarningThreshold = /*[[${warningPercentage}]]*/ 10;
let currentCriticalThreshold = /*[[${criticalPercentage}]]*/ 20;

// Функция обновления порогов при изменении пользователем
function updateThresholdsFromSettings() {
    const warningInput = document.getElementById('warningThreshold');
    const criticalInput = document.getElementById('criticalThreshold');
    
    if (warningInput && criticalInput) {
        const newWarning = parseInt(warningInput.value);
        const newCritical = parseInt(criticalInput.value);
        
        if (newWarning < newCritical && newWarning > 0 && newCritical > 0) {
            currentWarningThreshold = newWarning;
            currentCriticalThreshold = newCritical;
            
            // Пересчитать CSS классы с новыми порогами
            recalculateAlertLevels();
            
            // Обновить подсказки фильтров
            updateFilterTooltips();
        }
    }
}
```

#### B. Улучшение выбора ячеек
```javascript
function findMetricCellsInRow(row) {
    // Более надежный выбор ячеек
    const allCells = Array.from(row.querySelectorAll('td'));
    
    // Пропускаем ячейку заголовка группы (имеет атрибут rowspan)
    // Пропускаем ячейку названия метрики (обычно 2-я ячейка)
    const metricCells = allCells.filter((cell, index) => {
        return !cell.hasAttribute('rowspan') && 
               !cell.classList.contains('group-header') &&
               index > 1 && // Пропускаем первые два столбца
               cell.querySelector('.metric-value') !== null;
    });
    
    return metricCells;
}
```

#### C. Надежное извлечение данных
```javascript
function extractChangeData(cell) {
    const metricChangeDiv = cell.querySelector('.metric-change');
    if (!metricChangeDiv) {
        return { hasChange: false, changeValue: 0, changeType: 'STABLE' };
    }
    
    const changeSpan = metricChangeDiv.querySelector('span[data-change]');
    if (!changeSpan) {
        return { hasChange: false, changeValue: 0, changeType: 'STABLE' };
    }
    
    const dataChange = changeSpan.getAttribute('data-change');
    const changeValue = dataChange ? parseFloat(dataChange) : 0;
    
    return {
        hasChange: changeValue !== 0,
        changeValue: Math.abs(changeValue),
        changeType: changeValue > 0 ? 'UP' : changeValue < 0 ? 'DOWN' : 'STABLE'
    };
}
```

#### D. Улучшенное обнаружение уровней предупреждений с использованием актуальных порогов
```javascript
function detectAlertLevel(cell, changeValue) {
    // Сначала проверяем по CSS классам
    if (cell.classList.contains('metric-increase-critical') || 
        cell.classList.contains('metric-decrease-critical')) {
        return 'CRITICAL';
    }
    
    if (cell.classList.contains('metric-increase-warning') || 
        cell.classList.contains('metric-decrease-warning')) {
        return 'WARNING';
    }
    
    if (cell.classList.contains('metric-stable')) {
        return 'NORMAL';
    }
    
    // Резервный вариант: вычисляем по порогам из глобальных переменных
    // currentWarningThreshold и currentCriticalThreshold инициализируются 
    // из серверных данных при загрузке страницы
    if (changeValue >= currentCriticalThreshold) {
        return 'CRITICAL';
    } else if (changeValue >= currentWarningThreshold) {
        return 'WARNING';
    } else {
        return 'NORMAL';
    }
}
```

### 2. Переработанная логика применения фильтров

#### A. Улучшенная оценка фильтров
```javascript
function evaluateRowAgainstFilters(row, filterState) {
    const metricCells = findMetricCellsInRow(row);
    
    if (metricCells.length === 0) {
        return false; // Нет валидных ячеек метрик
    }
    
    // Оцениваем по последней операции (последний столбец)
    const latestCell = metricCells[metricCells.length - 1];
    const changeData = extractChangeData(latestCell);
    const alertLevel = detectAlertLevel(latestCell, changeData.changeValue);
    
    return applyFilterCriteria(changeData, alertLevel, filterState);
}

function applyFilterCriteria(changeData, alertLevel, filterState) {
    // Фильтр "Только предупреждения" (только уровень WARNING)
    // WARNING соответствует диапазону currentWarningThreshold <= изменение < currentCriticalThreshold
    if (filterState.onlyAlerts) {
        return alertLevel === 'WARNING';
    }
    
    // Фильтр "Скрыть без изменений"
    if (filterState.hideNoChanges && !changeData.hasChange) {
        return false;
    }
    
    // Фильтр "Только проблемы" (WARNING + CRITICAL)
    // WARNING: currentWarningThreshold <= изменение < currentCriticalThreshold
    // CRITICAL: изменение >= currentCriticalThreshold
    if (filterState.onlyProblems) {
        return alertLevel === 'WARNING' || alertLevel === 'CRITICAL';
    }
    
    // Фильтр "Большие изменения" (уровень CRITICAL)
    // CRITICAL: изменение >= currentCriticalThreshold
    if (filterState.bigChanges) {
        return alertLevel === 'CRITICAL';
    }
    
    return true; // Показывать по умолчанию
}
```

#### B. Исправленное управление группами
```javascript
function applyFiltersWithGroupManagement() {
    const groups = buildGroupStructure();
    let totalVisible = 0;
    let totalHidden = 0;
    
    groups.forEach(group => {
        let groupHasVisibleRows = false;
        
        // Обрабатываем каждую строку метрики в группе
        group.metricRows.forEach(row => {
            const shouldShow = evaluateRowAgainstFilters(row, filterState);
            
            row.style.display = shouldShow ? '' : 'none';
            
            if (shouldShow) {
                groupHasVisibleRows = true;
                totalVisible++;
            } else {
                totalHidden++;
            }
        });
        
        // Показываем/скрываем заголовок группы в зависимости от наличия видимых строк
        group.headerRow.style.display = groupHasVisibleRows ? '' : 'none';
        
        if (!groupHasVisibleRows) {
            totalHidden++; // Считаем скрытый заголовок группы
        }
    });
    
    updateRowCounters(totalVisible, totalHidden);
    updateSummaryCounters();
}
```

### 3. Улучшенная отладка и обработка ошибок

#### A. Отладочное логирование
```javascript
function debugFilterApplication(row, result, reason) {
    if (window.statisticsDebug) {
        console.log('Отладка фильтров:', {
            row: row,
            shouldShow: result,
            reason: reason,
            groupValue: getGroupValue(row),
            metricName: getMetricName(row),
            filterState: filterState
        });
    }
}

// Включение режима отладки
window.statisticsDebug = false; // Установить в true для отладки
```

#### B. Восстановление после ошибок
```javascript
function safeApplyFilters() {
    try {
        applyFiltersWithGroupManagement();
    } catch (error) {
        console.error('Применение фильтров не удалось:', error);
        
        // Восстановление: показать все строки
        document.querySelectorAll('.statistics-table tbody tr').forEach(row => {
            row.style.display = '';
        });
        
        showNotification('Ошибка фильтрации. Показаны все данные.', 'warning');
        
        // Сброс состояния фильтров
        resetAllFilters();
    }
}
```

### 4. Интеграция с серверной фильтрацией

#### A. Гибридная стратегия фильтрации
```javascript
// Для больших наборов данных используем серверную фильтрацию
const LARGE_DATASET_THRESHOLD = 500;

function shouldUseServerSideFiltering() {
    const totalRows = document.querySelectorAll('.statistics-table tbody tr').length;
    return totalRows > LARGE_DATASET_THRESHOLD;
}

async function applyFiltersHybrid() {
    if (shouldUseServerSideFiltering()) {
        await applyServerSideFilters();
    } else {
        safeApplyFilters(); // Клиентская фильтрация
    }
}
```

#### B. Интеграция серверной фильтрации
```javascript
async function applyServerSideFilters() {
    const filterDto = buildFilterDto();
    
    try {
        const response = await fetch('/statistics/api/filtered-analyze', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(filterDto)
        });
        
        if (response.ok) {
            const result = await response.json();
            updateTableWithFilteredData(result);
        } else {
            throw new Error('Серверная фильтрация не удалась');
        }
    } catch (error) {
        console.error('Ошибка серверной фильтрации:', error);
        // Откат к клиентской фильтрации
        safeApplyFilters();
    }
}
```

## План реализации

### Фаза 1: Исправление основной логики фильтров
1. **Обновление JavaScript функций**:
   - Заменить `applyFilters()` на `safeApplyFilters()`
   - Реализовать `findMetricCellsInRow()` и `extractChangeData()`
   - Добавить `detectAlertLevel()` и `applyFilterCriteria()`

2. **Тестирование с существующими данными**:
   - Проверить работу фильтров с текущими данными статистики
   - Протестировать все комбинации фильтров
   - Валидировать логику видимости групп

### Фаза 2: Улучшенная обработка ошибок
1. **Добавление режима отладки**:
   - Реализовать отладочное логирование для операций фильтрации
   - Добавить механизмы восстановления после ошибок
   - Обеспечить обратную связь с пользователем при проблемах с фильтрами

2. **Валидация и обратная связь**:
   - Добавить валидацию состояния фильтров
   - Улучшить уведомления пользователя
   - Обработать крайние случаи (пустые данные, неправильный DOM)

### Фаза 3: Интеграция с серверной стороной
1. **Гибридная фильтрация**:
   - Реализовать стратегию фильтрации на основе порогов
   - Интегрировать с существующим endpoint `/statistics/api/filtered-analyze`
   - Добавить мониторинг производительности

2. **Оптимизация**:
   - Кэшировать результаты фильтрации
   - Реализовать отложенную фильтрацию
   - Добавить индикаторы загрузки для серверных операций

## Стратегия тестирования

### Модульное тестирование
- **JavaScript функции фильтров**: Тестирование отдельных функций оценки фильтров
- **Управление группами**: Тестирование логики видимости групп
- **Извлечение данных**: Тестирование парсинга данных изменений из DOM

### Интеграционное тестирование  
- **Полный рабочий процесс фильтров**: Тестирование полного процесса применения фильтров
- **Интеграция с серверной стороной**: Тестирование поведения гибридной фильтрации
- **Обработка ошибок**: Тестирование восстановления от различных ошибочных состояний

### Пользовательское приемочное тестирование
- **Функциональность фильтров**: Проверка корректной работы всех типов фильтров
- **Производительность**: Обеспечение приемлемого времени отклика
- **Пользовательский опыт**: Валидация интуитивного поведения фильтров

## Снижение рисков

### Предотвращение потери данных
- Реализовать безопасные практики манипулирования DOM
- Добавить механизмы отката для неудачных операций фильтрации
- Поддерживать исходное состояние данных для восстановления

### Вопросы производительности
- Оптимизировать DOM запросы и манипуляции
- Реализовать эффективное обнаружение изменений
- Добавить мониторинг производительности и оповещения

### Совместимость с браузерами
- Тестировать в поддерживаемых браузерах
- Реализовать резервные варианты для неподдерживаемых функций
- Валидировать надежность обнаружения CSS классов

## Критерии успеха

### Функциональные требования
1. **Точность фильтрации**: Все типы фильтров правильно показывают/скрывают соответствующие строки
2. **Управление группами**: Заголовки групп правильно управляются при фильтрации строк
3. **Производительность**: Операции фильтрации завершаются в течение 2 секунд для типичных наборов данных
4. **Обработка ошибок**: Корректная деградация при сбоях операций фильтрации

### Требования к пользовательскому опыту
1. **Интуитивное поведение**: Фильтры работают так, как ожидают пользователи
2. **Визуальная обратная связь**: Четкое указание примененных фильтров и их эффектов
3. **Сохранение состояния**: Настройки фильтров запоминаются при взаимодействии со страницей
4. **Доступность**: Элементы управления фильтрами доступны через навигацию с клавиатуры