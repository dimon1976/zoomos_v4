/**
 * JavaScript для страницы результатов статистики
 */

// Глобальные переменные (будут инициализированы из шаблона)
let statisticsData = [];
let currentWarningThreshold = 10;
let currentCriticalThreshold = 20;

document.addEventListener('DOMContentLoaded', () => {
    addGroupStartClass();
    highlightMetrics();
    updateSummaryCounters();
    // displayDateModifications(); // Перенесено в initializeStatisticsData()
    initializeQuickFilters();
});

function toggleSettings() {
    const panel = document.getElementById('settingsPanel');
    panel.classList.toggle('d-none');
}

function validateThresholdValue(val, fallback) {
    const n = Number.parseInt(String(val), 10);
    return Number.isFinite(n) ? Math.min(100, Math.max(1, n)) : fallback;
}

function updateThresholds() {
    const warningEl = document.getElementById('warningThreshold');
    const criticalEl = document.getElementById('criticalThreshold');
    const warning = validateThresholdValue(warningEl.value, currentWarningThreshold);
    const critical = validateThresholdValue(criticalEl.value, currentCriticalThreshold);

    if (warning >= critical) {
        alert('Процент предупреждения должен быть меньше критического');
        warningEl.value = currentWarningThreshold;
        criticalEl.value = currentCriticalThreshold;
    }
}

function applyThresholds() {
    const warning = validateThresholdValue(document.getElementById('warningThreshold').value, currentWarningThreshold);
    const critical = validateThresholdValue(document.getElementById('criticalThreshold').value, currentCriticalThreshold);

    if (warning >= critical) {
        alert('Процент предупреждения должен быть меньше критического');
        return;
    }

    currentWarningThreshold = warning;
    currentCriticalThreshold = critical;

    recalculateAlertLevels();
    updateSummaryCounters();
    showNotification('Пороги отклонений обновлены', 'success');
}

function recalculateAlertLevels() {
    const cells = document.querySelectorAll('.statistics-table tbody td');
    cells.forEach(cell => {
        cell.classList.remove(
            'metric-increase-warning','metric-increase-critical',
            'metric-decrease-warning','metric-decrease-critical',
            'metric-stable'
        );

        const changeWrapper = cell.querySelector('.metric-change span');
        if (!changeWrapper) { cell.classList.add('metric-stable'); return; }

        // Берём числовое значение из data-change, чтобы не зависеть от локали
        const raw = changeWrapper.getAttribute('data-change');
        const val = raw === null ? NaN : Number(raw);
        if (!Number.isFinite(val) || val === 0) { cell.classList.add('metric-stable'); return; }

        const abs = Math.abs(val);
        const isUp = val > 0;

        if (abs >= currentCriticalThreshold) {
            cell.classList.add(isUp ? 'metric-increase-critical' : 'metric-decrease-critical');
        } else if (abs >= currentWarningThreshold) {
            cell.classList.add(isUp ? 'metric-increase-warning' : 'metric-decrease-warning');
        } else {
            cell.classList.add('metric-stable');
        }
    });

    // Восстанавливаем выделение метрик после пересчета
    highlightMetrics();

    // Пересчитываем счетчики фильтров после изменения порогов
    calculateFilterCounts();
}

function updateSummaryCounters() {
    // При подсчете счетчиков учитываем только видимые строки
    const visibleRows = document.querySelectorAll('.statistics-table tbody tr:not(.group-hidden)');
    const warningCells = Array.from(visibleRows).reduce((count, row) => {
        return count + row.querySelectorAll('.metric-increase-warning, .metric-decrease-warning').length;
    }, 0);
    const criticalCells = Array.from(visibleRows).reduce((count, row) => {
        return count + row.querySelectorAll('.metric-increase-critical, .metric-decrease-critical').length;
    }, 0);

    const fromData = statisticsData && statisticsData[0] && Array.isArray(statisticsData[0].operations)
        ? statisticsData[0].operations.length
        : null;
    const fromDom = document.querySelectorAll('thead .operation-header').length;
    const totalOperations = fromData ?? fromDom;

    const totalEl = document.getElementById('totalOperationsCount');
    if (totalEl) totalEl.textContent = String(totalOperations);
    const warnEl = document.getElementById('warningChangesCount');
    if (warnEl) warnEl.textContent = String(warningCells);
    const critEl = document.getElementById('criticalChangesCount');
    if (critEl) critEl.textContent = String(criticalCells);
}

function exportToExcel() {
    try {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/statistics/export/excel';
        form.style.display = 'none';

        const dataInput = document.createElement('input');
        dataInput.name = 'statisticsData';
        try {
            dataInput.value = JSON.stringify(statisticsData);
        } catch (err) {
            console.error('Ошибка сериализации данных:', err);
            dataInput.value = '[]';
        }
        form.appendChild(dataInput);

        const settingsInput = document.createElement('input');
        settingsInput.name = 'settings';
        try {
            settingsInput.value = JSON.stringify({
                warningThreshold: currentWarningThreshold,
                criticalThreshold: currentCriticalThreshold
            });
        } catch (err) {
            console.error('Ошибка сериализации настроек:', err);
            settingsInput.value = '{}';
        }
        form.appendChild(settingsInput);

        document.body.appendChild(form);
        form.submit();
        form.remove();

        showNotification('Экспорт в Excel запущен', 'info');
    } catch (err) {
        console.error('Ошибка экспорта в Excel:', err);
        showNotification('Ошибка при экспорте в Excel', 'danger');
    }
}

function showNotification(message, type) {
    const alert = document.createElement('div');
    alert.className = `alert alert-${type} alert-dismissible fade show position-fixed`;
    alert.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
    alert.setAttribute('role', 'alert');
    alert.innerHTML = `
        ${message}
        <button type="button" class="btn-close" aria-label="Закрыть" onclick="this.parentElement.remove()"></button>
    `;
    document.body.appendChild(alert);
    setTimeout(() => { if (alert.parentElement) alert.remove(); }, 5000);
}

function addGroupStartClass() {
    document.querySelectorAll('.statistics-table tbody tr').forEach(tr => {
        if (tr.querySelector('td[rowspan]')) tr.classList.add('group-start');
    });
}

function highlightMetrics() {
    // Применяем цветовое выделение для всех ячеек с метриками
    document.querySelectorAll('.statistics-table tbody td').forEach(cell => {
        // Проверяем, является ли ячейка метрикой (не группой и не значением)
        const cellText = cell.textContent.trim();

        // Пропускаем ячейки с rowspan (группы) и ячейки с числовыми значениями
        if (cell.hasAttribute('rowspan') || cell.querySelector('.metric-value')) {
            return;
        }

        // Проверяем текст ячейки на соответствие названиям метрик
        const metricName = cellText.toUpperCase();

        // Удаляем все предыдущие классы выделения
        cell.classList.remove('metric-highlight-date', 'metric-highlight-price', 'metric-highlight-promo');

        // Применяем соответствующий класс выделения
        if (metricName === 'DATE_MODIFICATIONS' || metricName === 'DATE MODIFICATIONS') {
            cell.classList.add('metric-highlight-date');
        } else if (metricName === 'COMPETITORPRICE' || metricName === 'COMPETITOR PRICE') {
            cell.classList.add('metric-highlight-price');
        } else if (metricName === 'COMPETITORPROMOTIONALPRICE' || metricName === 'COMPETITOR PROMOTIONAL PRICE') {
            cell.classList.add('metric-highlight-promo');
        }
    });
}

// ===== Фильтры быстрого доступа =====
function initializeQuickFilters() {
    calculateFilterCounts();
    bindFilterEvents();
    updateFilterStatus();
}

function calculateFilterCounts() {
    const groups = document.querySelectorAll('.statistics-table tbody tr.group-start');
    let warningCount = 0, criticalCount = 0, bothCount = 0;
    const totalCount = groups.length;

    groups.forEach(groupRow => {
        const groupMetrics = getGroupMetrics(groupRow);
        const hasOnlyWarning = hasOnlyWarningDeviations(groupMetrics);
        const hasCritical = hasCriticalDeviations(groupMetrics);
        const hasAny = hasAnyDeviations(groupMetrics);

        if (hasOnlyWarning) warningCount++;
        if (hasCritical) criticalCount++;
        if (hasAny) bothCount++;
    });

    // Обновляем счетчики кнопок
    const warningCountEl = document.getElementById('warningCount');
    const criticalCountEl = document.getElementById('criticalCount');
    const bothCountEl = document.getElementById('bothCount');
    const totalCountEl = document.getElementById('totalCount');

    if (warningCountEl) warningCountEl.textContent = warningCount;
    if (criticalCountEl) criticalCountEl.textContent = criticalCount;
    if (bothCountEl) bothCountEl.textContent = bothCount;
    if (totalCountEl) totalCountEl.textContent = totalCount;
}

function getGroupMetrics(groupStartRow) {
    const metrics = [];
    let currentRow = groupStartRow;

    // Собираем все строки метрик для этой группы
    // Группа включает строку с rowspan и все следующие строки до следующей группы
    do {
        const metricCells = currentRow.querySelectorAll('.metric-decrease-warning, .metric-decrease-critical, .metric-increase-warning, .metric-increase-critical');
        metrics.push(...metricCells);
        currentRow = currentRow.nextElementSibling;
    } while (currentRow && !currentRow.querySelector('td[rowspan]'));

    return metrics;
}

function hasWarningDeviations(metrics) {
    // Проверяем есть ли хотя бы одно ПЛОХОЕ предупреждение в группе
    // Для DATE_MODIFICATIONS плохо когда растет (increase-warning отображается как decrease-warning)
    // Для остальных метрик плохо когда падает (decrease-warning)
    return metrics.some(cell =>
        cell.classList.contains('metric-decrease-warning')
    );
}

function hasCriticalDeviations(metrics) {
    // Проверяем есть ли хотя бы одно ПЛОХОЕ критическое отклонение в группе
    // Для DATE_MODIFICATIONS плохо когда растет (increase-critical отображается как decrease-critical)
    // Для остальных метрик плохо когда падает (decrease-critical)
    return metrics.some(cell =>
        cell.classList.contains('metric-decrease-critical')
    );
}

function hasOnlyWarningDeviations(metrics) {
    // Проверяем есть ли предупреждения И нет критических отклонений
    const hasWarning = hasWarningDeviations(metrics);
    const hasCritical = hasCriticalDeviations(metrics);
    return hasWarning && !hasCritical;
}

function hasAnyDeviations(metrics) {
    // Проверяем есть ли любые ПЛОХИЕ отклонения в группе
    // Учитываем только плохие отклонения (красные и желтые)
    return metrics.some(cell =>
        cell.classList.contains('metric-decrease-warning') ||
        cell.classList.contains('metric-decrease-critical')
    );
}

function shouldShowGroup(groupMetrics, filterType) {
    switch (filterType) {
        case 'warning':
            // Показываем только группы с предупреждениями БЕЗ критических отклонений
            return hasOnlyWarningDeviations(groupMetrics);
        case 'critical':
            // Показываем группы, где есть хотя бы одно критическое отклонение
            return hasCriticalDeviations(groupMetrics);
        case 'both':
            // Показываем группы с любыми отклонениями
            return hasAnyDeviations(groupMetrics);
        case 'reset':
        default:
            return true;
    }
}

function filterGroups(filterType) {
    try {
        const groups = document.querySelectorAll('.statistics-table tbody tr.group-start');
        let visibleCount = 0;

        // Добавляем класс анимации
        groups.forEach(row => row.classList.add('filtering'));

        setTimeout(() => {
            try {
                groups.forEach(groupRow => {
                    try {
                        const groupMetrics = getGroupMetrics(groupRow);
                        const shouldShow = shouldShowGroup(groupMetrics, filterType);

                        toggleGroupVisibility(groupRow, shouldShow);
                        if (shouldShow) visibleCount++;

                        // Убираем класс анимации
                        groupRow.classList.remove('filtering');
                    } catch (err) {
                        console.error('Ошибка при обработке группы:', err);
                        // В случае ошибки показываем группу
                        groupRow.classList.remove('group-hidden', 'filtering');
                    }
                });

                updateFilterStatus(visibleCount, groups.length);
                setActiveFilter(filterType);
                updateSummaryCounters();
            } catch (err) {
                console.error('Ошибка при применении фильтра:', err);
                showNotification('Ошибка при применении фильтра', 'danger');
            }
        }, 150);
    } catch (err) {
        console.error('Ошибка инициализации фильтра:', err);
        showNotification('Ошибка фильтрации данных', 'danger');
    }
}

function toggleGroupVisibility(groupStartRow, shouldShow) {
    let currentRow = groupStartRow;

    // Скрываем/показываем все строки, принадлежащие этой группе
    // Группа включает строку с rowspan и все следующие строки до следующей группы
    do {
        currentRow.classList.toggle('group-hidden', !shouldShow);
        currentRow = currentRow.nextElementSibling;
    } while (currentRow && !currentRow.querySelector('td[rowspan]'));
}

function updateFilterStatus(visibleCount, totalCount) {
    const visibleEl = document.getElementById('visibleGroups');
    const totalEl = document.getElementById('totalGroups');

    if (visibleEl) visibleEl.textContent = visibleCount || 0;
    if (totalEl) totalEl.textContent = totalCount || 0;
}

function setActiveFilter(filterType) {
    // Убираем активное состояние со всех кнопок
    document.querySelectorAll('.filter-controls .btn').forEach(btn =>
        btn.classList.remove('active')
    );

    // Устанавливаем активное состояние на текущий фильтр
    let activeButtonId;
    switch (filterType) {
        case 'warning': activeButtonId = 'filterWarning'; break;
        case 'critical': activeButtonId = 'filterCritical'; break;
        case 'both': activeButtonId = 'filterBoth'; break;
        case 'reset': activeButtonId = 'filterReset'; break;
    }

    const activeButton = document.getElementById(activeButtonId);
    if (activeButton) activeButton.classList.add('active');
}

function bindFilterEvents() {
    const filterWarning = document.getElementById('filterWarning');
    const filterCritical = document.getElementById('filterCritical');
    const filterBoth = document.getElementById('filterBoth');
    const filterReset = document.getElementById('filterReset');

    if (filterWarning) {
        filterWarning.addEventListener('click', () => filterGroups('warning'));
    }
    if (filterCritical) {
        filterCritical.addEventListener('click', () => filterGroups('critical'));
    }
    if (filterBoth) {
        filterBoth.addEventListener('click', () => filterGroups('both'));
    }
    if (filterReset) {
        filterReset.addEventListener('click', () => filterGroups('reset'));
    }
}
// ===== Конец фильтров быстрого доступа =====

function displayDateModifications() {
    if (!statisticsData || statisticsData.length === 0) {
        console.debug('displayDateModifications: Нет данных статистики');
        return;
    }

    const panel = document.getElementById('dateModificationsPanel');
    const summary = document.getElementById('dateModificationsSummary');
    const content = document.getElementById('dateModificationsContent');

    if (!panel || !summary || !content) {
        console.warn('displayDateModifications: Не найдены элементы DOM для панели DATE_MODIFICATIONS');
        return;
    }

    // Собираем статистику изменений дат из всех групп и всех операций
    const allDateMods = [];

    statisticsData.forEach(group => {
        if (group.operations && group.operations.length > 0) {
            group.operations.forEach(operation => {
                // Ищем статистику изменений дат в операции
                if (operation.dateModificationStats) {
                    console.debug('Найдена статистика изменений дат для группы:', group.groupFieldValue, operation.dateModificationStats);
                    allDateMods.push({
                        group: group.groupFieldValue,
                        operation: operation.operationName,
                        exportDate: operation.exportDate,
                        operationId: operation.operationId,
                        ...operation.dateModificationStats
                    });
                }
            });
        }
    });

    console.debug('displayDateModifications: Найдено записей с изменениями дат:', allDateMods.length);

    if (allDateMods.length === 0) {
        console.debug('displayDateModifications: Нет данных об изменениях дат - показываем панель для отладки');
        // Временно показываем панель даже если нет данных (для отладки)
        panel.classList.remove('d-none');
        // Показываем пустое сообщение для отладки
        summary.innerHTML = 'Нет данных об изменениях дат (режим отладки)';
        content.innerHTML = '<div class="col-12 text-muted text-center p-3 bg-light border rounded">⚠️ Данные об изменениях дат не найдены в statisticsData</div>';
        return;
    }

    // Отображаем панель
    panel.classList.remove('d-none');
    console.debug('displayDateModifications: Панель DATE_MODIFICATIONS показана с данными:', allDateMods);

    // Создаем компактное резюме
    const totalModified = allDateMods.reduce((sum, mod) => sum + (mod.modifiedCount || 0), 0);
    const totalRecords = allDateMods.reduce((sum, mod) => sum + (mod.totalCount || 0), 0);
    const overallPercentage = totalRecords > 0 ? (totalModified / totalRecords * 100).toFixed(1) : '0.0';

    const criticalGroups = allDateMods.filter(mod => mod.alertLevel && mod.alertLevel.toLowerCase() === 'critical').length;
    const warningGroups = allDateMods.filter(mod => mod.alertLevel && mod.alertLevel.toLowerCase() === 'warning').length;

    let summaryText = `${totalModified} из ${totalRecords} записей (${overallPercentage}%)`;
    if (criticalGroups > 0) {
        summaryText += ` | <span class="text-danger"><i class="fas fa-exclamation-triangle"></i> ${criticalGroups} критических</span>`;
    }
    if (warningGroups > 0) {
        summaryText += ` | <span class="text-warning"><i class="fas fa-exclamation-triangle"></i> ${warningGroups} предупреждений</span>`;
    }

    summary.innerHTML = summaryText;

    // Создаем детализированные карточки для каждой группы с изменениями дат
    content.innerHTML = allDateMods.map(dateMod => {
        const alertLevel = (dateMod.alertLevel || 'normal').toLowerCase();
        const alertClass = alertLevel === 'critical' ? 'border-danger' :
                          alertLevel === 'warning' ? 'border-warning' :
                          'border-info';

        const alertBadge = alertLevel === 'critical' ?
                          '<span class="badge bg-danger ms-2">Критическое</span>' :
                          alertLevel === 'warning' ?
                          '<span class="badge bg-warning text-dark ms-2">Предупреждение</span>' :
                          '<span class="badge bg-success ms-2">Нормально</span>';

        const modificationPercentage = dateMod.modificationPercentage || 0;
        const modifiedCount = dateMod.modifiedCount || 0;
        const totalCount = dateMod.totalCount || 0;
        const operationName = dateMod.operation || `Операция #${dateMod.operationId || 'N/A'}`;

        return `
            <div class="col-md-6 col-lg-4 mb-2">
                <div class="card ${alertClass} h-100" style="font-size: 0.9rem;">
                    <div class="card-body p-2">
                        <div class="d-flex justify-content-between align-items-start mb-1">
                            <h6 class="card-title mb-0" style="font-size: 0.95rem;">${dateMod.group || 'Неизвестная группа'}</h6>
                            ${alertBadge}
                        </div>
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <div class="fw-bold text-primary">${modificationPercentage.toFixed(1)}%</div>
                                <div class="small text-muted">
                                    ${modifiedCount}/${totalCount} записей
                                </div>
                            </div>
                            <div class="text-end">
                                <div class="small text-muted">${operationName}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

// Функция для инициализации данных из шаблона (вызывается из HTML)
function initializeStatisticsData(data, warningThreshold, criticalThreshold) {
    statisticsData = data || [];
    currentWarningThreshold = warningThreshold || 10;
    currentCriticalThreshold = criticalThreshold || 20;

    // Детальное логирование данных для отладки DATE_MODIFICATIONS
    console.log('=== ИНИЦИАЛИЗАЦИЯ ДАННЫХ СТАТИСТИКИ ===');
    console.log('Всего групп:', statisticsData.length);
    statisticsData.forEach((group, groupIndex) => {
        console.log(`\nГруппа ${groupIndex + 1}: ${group.groupFieldValue}`);
        console.log('Операций в группе:', group.operations ? group.operations.length : 0);

        if (group.operations) {
            group.operations.forEach((operation, opIndex) => {
                console.log(`  Операция ${opIndex + 1} (ID: ${operation.operationId}):`);
                console.log('    dateModificationStats:', operation.dateModificationStats);
                if (operation.dateModificationStats) {
                    console.log('    - modifiedCount:', operation.dateModificationStats.modifiedCount);
                    console.log('    - totalCount:', operation.dateModificationStats.totalCount);
                    console.log('    - modificationPercentage:', operation.dateModificationStats.modificationPercentage);
                    console.log('    - alertLevel:', operation.dateModificationStats.alertLevel);
                }
            });
        }
    });
    console.log('=== КОНЕЦ ЛОГИРОВАНИЯ ===');

    // Теперь, когда данные инициализированы, обрабатываем отображение DATE_MODIFICATIONS
    displayDateModifications();
}