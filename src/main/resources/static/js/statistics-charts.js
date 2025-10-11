/**
 * Модуль для отрисовки исторических графиков статистики с трендами
 * Использует Chart.js 4.x
 */

// Глобальные переменные для хранения экземпляров графиков
const chartInstances = {};

/**
 * Создает линейный график с трендом для конкретной метрики и группы
 *
 * @param {string} canvasId - ID canvas элемента
 * @param {Object} historyData - Данные истории от API
 * @returns {Chart} - Экземпляр графика Chart.js
 */
function createTrendChart(canvasId, historyData) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error('Canvas element not found:', canvasId);
        return null;
    }

    const ctx = canvas.getContext('2d');

    // Уничтожаем существующий график если есть
    if (chartInstances[canvasId]) {
        chartInstances[canvasId].destroy();
    }

    // Подготовка данных
    const labels = historyData.dataPoints.map(point => {
        // Парсим ZonedDateTime из Java (формат: 2025-01-21T15:30:00+03:00)
        const date = new Date(point.date);
        return date.toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    });

    const values = historyData.dataPoints.map(point => point.value);

    console.log('Chart data:', {
        groupValue: historyData.groupValue,
        metricName: historyData.metricName,
        dataPoints: historyData.dataPoints.length,
        firstDate: historyData.dataPoints[0]?.date,
        parsedFirstDate: labels[0]
    });

    // Вычисляем линию тренда
    const trendLine = calculateTrendLine(values);

    // Определяем цвет на основе тренда
    const trendColor = getTrendColor(historyData.trendInfo.direction);
    const backgroundGradient = createGradient(ctx, canvas.height, trendColor);

    // Конфигурация графика
    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: `${historyData.metricName} (${historyData.groupValue})`,
                    data: values,
                    borderColor: trendColor.border,
                    backgroundColor: backgroundGradient,
                    borderWidth: 3,
                    fill: true,
                    tension: 0.3, // Сглаживание линии
                    pointRadius: 6,
                    pointHoverRadius: 10,
                    pointBackgroundColor: trendColor.point,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 3,
                    pointHoverBorderWidth: 3
                },
                {
                    label: 'Линия тренда',
                    data: trendLine,
                    borderColor: trendColor.trend,
                    borderWidth: 3,
                    borderDash: [8, 4], // Пунктирная линия (увеличена)
                    fill: false,
                    pointRadius: 0,
                    tension: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                title: {
                    display: true,
                    text: historyData.groupValue,
                    font: {
                        size: 16,
                        weight: 'bold'
                    },
                    padding: {
                        top: 10,
                        bottom: 5
                    }
                },
                subtitle: {
                    display: true,
                    text: historyData.trendInfo.description,
                    font: {
                        size: 13,
                        style: 'italic'
                    },
                    color: trendColor.text,
                    padding: {
                        bottom: 15
                    }
                },
                legend: {
                    display: true,
                    position: 'bottom',
                    labels: {
                        font: {
                            size: 12
                        },
                        padding: 15
                    }
                },
                tooltip: {
                    enabled: true,
                    backgroundColor: 'rgba(0, 0, 0, 0.8)',
                    titleFont: {
                        size: 14,
                        weight: 'bold'
                    },
                    bodyFont: {
                        size: 13
                    },
                    footerFont: {
                        size: 12
                    },
                    padding: 12,
                    displayColors: true,
                    callbacks: {
                        title: function(context) {
                            const index = context[0].dataIndex;
                            return historyData.dataPoints[index].operationName;
                        },
                        label: function(context) {
                            let label = context.dataset.label || '';
                            if (label) {
                                label += ': ';
                            }
                            if (context.parsed.y !== null) {
                                label += context.parsed.y.toLocaleString('ru-RU');
                            }
                            return label;
                        },
                        footer: function(context) {
                            const index = context[0].dataIndex;
                            const point = historyData.dataPoints[index];
                            const date = new Date(point.date);
                            const formattedDate = date.toLocaleString('ru-RU', {
                                day: '2-digit',
                                month: 'long',
                                year: 'numeric',
                                hour: '2-digit',
                                minute: '2-digit'
                            });
                            return `Дата: ${formattedDate}\nЗначение: ${point.value.toLocaleString('ru-RU')}`;
                        }
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        font: {
                            size: 12
                        },
                        maxRotation: 45,
                        minRotation: 0
                    },
                    grid: {
                        display: false
                    }
                },
                y: {
                    beginAtZero: true,
                    ticks: {
                        font: {
                            size: 12
                        },
                        callback: function(value) {
                            return value.toLocaleString('ru-RU');
                        }
                    },
                    grid: {
                        color: 'rgba(0, 0, 0, 0.05)'
                    }
                }
            }
        }
    };

    // Создаем график
    const chart = new Chart(ctx, config);
    chartInstances[canvasId] = chart;

    return chart;
}

/**
 * Вычисляет линию тренда (линейная регрессия)
 */
function calculateTrendLine(values) {
    const n = values.length;
    if (n < 2) return values;

    let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

    for (let i = 0; i < n; i++) {
        sumX += i;
        sumY += values[i];
        sumXY += i * values[i];
        sumX2 += i * i;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    return values.map((_, i) => slope * i + intercept);
}

/**
 * Возвращает цвета на основе направления тренда
 */
function getTrendColor(direction) {
    switch (direction) {
        case 'STRONG_GROWTH':
            return {
                border: '#28a745',
                point: '#28a745',
                trend: '#20c997',
                text: '#155724',
                fill: 'rgba(40, 167, 69, 0.1)'
            };
        case 'GROWTH':
            return {
                border: '#20c997',
                point: '#20c997',
                trend: '#17a2b8',
                text: '#0c5460',
                fill: 'rgba(32, 201, 151, 0.1)'
            };
        case 'DECLINE':
            return {
                border: '#ffc107',
                point: '#ffc107',
                trend: '#fd7e14',
                text: '#856404',
                fill: 'rgba(255, 193, 7, 0.1)'
            };
        case 'STRONG_DECLINE':
            return {
                border: '#dc3545',
                point: '#dc3545',
                trend: '#c82333',
                text: '#721c24',
                fill: 'rgba(220, 53, 69, 0.1)'
            };
        default: // STABLE
            return {
                border: '#6c757d',
                point: '#6c757d',
                trend: '#495057',
                text: '#383d41',
                fill: 'rgba(108, 117, 125, 0.1)'
            };
    }
}

/**
 * Возвращает Unicode иконку для направления тренда
 */
function getTrendIcon(direction) {
    switch (direction) {
        case 'STRONG_GROWTH':
            return '⬆️'; // Двойная стрелка вверх
        case 'GROWTH':
            return '↗️'; // Стрелка вверх-вправо
        case 'DECLINE':
            return '↘️'; // Стрелка вниз-вправо
        case 'STRONG_DECLINE':
            return '⬇️'; // Двойная стрелка вниз
        default: // STABLE
            return '➡️'; // Стрелка вправо
    }
}

/**
 * Создает градиентную заливку для графика
 */
function createGradient(ctx, height, colors) {
    const gradient = ctx.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, colors.fill);
    gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
    return gradient;
}

/**
 * Загружает и отображает график для конкретной группы и метрики
 *
 * @param {string} canvasId - ID canvas элемента
 * @param {number} templateId - ID шаблона
 * @param {string} groupValue - Значение группы
 * @param {string} metricName - Название метрики
 * @param {string|null} filterFieldName - Поле фильтрации (опционально)
 * @param {string|null} filterFieldValue - Значение фильтра (опционально)
 * @param {number} limit - Максимальное количество точек
 */
async function loadAndDisplayChart(canvasId, templateId, groupValue, metricName, filterFieldName = null, filterFieldValue = null, limit = 50) {
    try {
        // Показываем индикатор загрузки
        const container = document.getElementById(canvasId).parentElement;
        const loader = document.createElement('div');
        loader.className = 'text-center mb-3 chart-loader';
        loader.innerHTML = '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Загрузка...</span></div>';
        container.prepend(loader);

        // Формируем URL для API запроса
        let url = `/statistics/history?templateId=${templateId}&groupValue=${encodeURIComponent(groupValue)}&metricName=${encodeURIComponent(metricName)}&limit=${limit}`;

        if (filterFieldName && filterFieldValue) {
            url += `&filterFieldName=${encodeURIComponent(filterFieldName)}&filterFieldValue=${encodeURIComponent(filterFieldValue)}`;
        }

        // Загружаем данные
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const historyData = await response.json();

        // Убираем индикатор загрузки
        loader.remove();

        // Проверяем наличие данных
        if (!historyData.dataPoints || historyData.dataPoints.length === 0) {
            showNoDataMessage(container, 'Нет исторических данных для отображения');
            return null;
        }

        // Создаем график
        return createTrendChart(canvasId, historyData);

    } catch (error) {
        console.error('Ошибка загрузки данных графика:', error);

        // Убираем индикатор загрузки если есть
        const loader = document.querySelector('.chart-loader');
        if (loader) loader.remove();

        // Показываем сообщение об ошибке
        const container = document.getElementById(canvasId).parentElement;
        showNoDataMessage(container, 'Ошибка загрузки данных: ' + error.message);

        return null;
    }
}

/**
 * Показывает сообщение вместо графика
 */
function showNoDataMessage(container, message) {
    const alert = document.createElement('div');
    alert.className = 'alert alert-info';
    alert.innerHTML = `<i class="fas fa-info-circle me-2"></i>${message}`;
    container.prepend(alert);
}

/**
 * Загружает графики для всех групп по одной метрике
 *
 * @param {string} containerSelector - CSS селектор контейнера для графиков
 * @param {number} templateId - ID шаблона
 * @param {string} metricName - Название метрики
 * @param {string|null} filterFieldName - Поле фильтрации (опционально)
 * @param {string|null} filterFieldValue - Значение фильтра (опционально)
 * @param {number} limit - Максимальное количество точек на график
 */
async function loadAllGroupsCharts(containerSelector, templateId, metricName, filterFieldName = null, filterFieldValue = null, limit = 50) {
    try {
        // Формируем URL для API запроса
        let url = `/statistics/history/all-groups?templateId=${templateId}&metricName=${encodeURIComponent(metricName)}&limit=${limit}`;

        if (filterFieldName && filterFieldValue) {
            url += `&filterFieldName=${encodeURIComponent(filterFieldName)}&filterFieldValue=${encodeURIComponent(filterFieldValue)}`;
        }

        // Загружаем данные
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const allGroupsData = await response.json();

        if (!allGroupsData || allGroupsData.length === 0) {
            console.warn('Нет данных для отображения графиков');
            return;
        }

        // Получаем контейнер
        const container = document.querySelector(containerSelector);
        if (!container) {
            console.error('Container not found:', containerSelector);
            return;
        }

        // Очищаем контейнер
        container.innerHTML = '';

        // Создаем графики для каждой группы
        allGroupsData.forEach((historyData, index) => {
            // Создаем wrapper для графика
            const chartWrapper = document.createElement('div');
            chartWrapper.className = 'col-md-6 col-lg-4 mb-4';

            const chartCard = document.createElement('div');
            chartCard.className = 'card shadow-sm';
            chartCard.innerHTML = `
                <div class="card-body" style="height: 400px;">
                    <canvas id="chart-${metricName}-${index}"></canvas>
                </div>
            `;

            chartWrapper.appendChild(chartCard);
            container.appendChild(chartWrapper);

            // Создаем график
            const canvasId = `chart-${metricName}-${index}`;
            createTrendChart(canvasId, historyData);
        });

    } catch (error) {
        console.error('Ошибка загрузки графиков для всех групп:', error);
    }
}

/**
 * Уничтожает все созданные графики (для очистки памяти)
 */
function destroyAllCharts() {
    Object.values(chartInstances).forEach(chart => {
        if (chart) chart.destroy();
    });
    Object.keys(chartInstances).forEach(key => delete chartInstances[key]);
}

/**
 * Создаёт комбинированный график на всю ширину со всеми группами
 *
 * @param {string} canvasId - ID canvas элемента
 * @param {Array} allGroupsData - Массив данных всех групп от API
 * @returns {Chart} - Экземпляр графика Chart.js
 */
function createCombinedChart(canvasId, allGroupsData) {
    console.log(`createCombinedChart вызвана с canvasId: ${canvasId}`);
    console.log(`Данные для ${allGroupsData.length} групп:`, allGroupsData);

    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error('Canvas element not found:', canvasId);
        return null;
    }

    console.log('Canvas элемент найден:', canvas);

    const ctx = canvas.getContext('2d');

    // Уничтожаем существующий график если есть
    if (chartInstances[canvasId]) {
        console.log('Уничтожаем существующий график');
        chartInstances[canvasId].destroy();
    }

    // Яркая палитра цветов для различия групп
    const colorPalette = [
        { border: '#667eea', bg: 'rgba(102, 126, 234, 0.1)' },  // Фиолетовый
        { border: '#f093fb', bg: 'rgba(240, 147, 251, 0.1)' },  // Розовый
        { border: '#4facfe', bg: 'rgba(79, 172, 254, 0.1)' },   // Голубой
        { border: '#43e97b', bg: 'rgba(67, 233, 123, 0.1)' },   // Зелёный
        { border: '#fa709a', bg: 'rgba(250, 112, 154, 0.1)' },  // Малиновый
        { border: '#fee140', bg: 'rgba(254, 225, 64, 0.1)' },   // Жёлтый
        { border: '#30cfd0', bg: 'rgba(48, 207, 208, 0.1)' },   // Бирюзовый
        { border: '#ff6b6b', bg: 'rgba(255, 107, 107, 0.1)' },  // Коралловый
        { border: '#5f27cd', bg: 'rgba(95, 39, 205, 0.1)' },    // Индиго
        { border: '#00d2d3', bg: 'rgba(0, 210, 211, 0.1)' },    // Циан
        { border: '#ff9ff3', bg: 'rgba(255, 159, 243, 0.1)' },  // Светло-розовый
        { border: '#54a0ff', bg: 'rgba(84, 160, 255, 0.1)' },   // Синий
        { border: '#48dbfb', bg: 'rgba(72, 219, 251, 0.1)' },   // Светло-голубой
        { border: '#1dd1a1', bg: 'rgba(29, 209, 161, 0.1)' },   // Мятный
        { border: '#feca57', bg: 'rgba(254, 202, 87, 0.1)' },   // Золотой
        { border: '#ff6348', bg: 'rgba(255, 99, 72, 0.1)' },    // Томатный
        { border: '#ff4757', bg: 'rgba(255, 71, 87, 0.1)' },    // Красный
        { border: '#747d8c', bg: 'rgba(116, 125, 140, 0.1)' },  // Серо-синий
        { border: '#2f3542', bg: 'rgba(47, 53, 66, 0.1)' },     // Тёмно-серый
        { border: '#a29bfe', bg: 'rgba(162, 155, 254, 0.1)' }   // Лавандовый
    ];

    // Создаём datasets для каждой группы
    const datasets = allGroupsData.map((groupData, index) => {
        // Цвет из палитры по индексу (циклично)
        const color = colorPalette[index % colorPalette.length];
        const trendInfo = groupData.trendInfo;

        // Парсим даты и значения
        const data = groupData.dataPoints.map(point => ({
            x: new Date(point.date),
            y: point.value
        }));

        // Формируем label без тренда (тренд теперь показывается через цвет)
        const trendLabel = groupData.groupValue;

        // Толщина линии зависит от силы тренда
        const borderWidth = (trendInfo.direction.includes('STRONG') ? 3 : 2);

        return {
            label: trendLabel,
            data: data,
            borderColor: color.border,
            backgroundColor: color.bg,
            borderWidth: borderWidth,  // Жирнее для сильных трендов
            fill: false,
            tension: 0.3,
            pointRadius: 5,
            pointHoverRadius: 9,
            pointBackgroundColor: color.border,
            pointBorderColor: '#fff',
            pointBorderWidth: 2,
            // Сохраняем доп. данные для таблицы
            trendInfo: trendInfo,
            groupValue: groupData.groupValue
        };
    });

    // Конфигурация графика
    const config = {
        type: 'line',
        data: {
            datasets: datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'nearest',  // Показываем только ближайшую точку
                intersect: true,  // Только при наведении на точку
                axis: 'x'
            },
            plugins: {
                title: {
                    display: true,
                    text: allGroupsData[0]?.metricName || 'Сравнение групп',
                    font: {
                        size: 20,
                        weight: 'bold'
                    },
                    padding: {
                        top: 15,
                        bottom: 20
                    }
                },
                subtitle: {
                    display: true,
                    text: 'Используйте колесо мыши для масштабирования • Drag для перемещения • Двойной клик для сброса',
                    font: {
                        size: 12,
                        style: 'italic'
                    },
                    color: '#6c757d',
                    padding: {
                        bottom: 15
                    }
                },
                legend: {
                    display: false  // Отключаем легенду - используем таблицу вместо неё
                },
                tooltip: {
                    enabled: true,
                    backgroundColor: 'rgba(0, 0, 0, 0.9)',
                    titleFont: {
                        size: 13,
                        weight: 'bold'
                    },
                    bodyFont: {
                        size: 12
                    },
                    padding: 10,
                    displayColors: false,  // Убираем цветной квадратик
                    callbacks: {
                        title: function(context) {
                            // Название группы БЕЗ тренда
                            return context[0].dataset.groupValue || '';
                        },
                        label: function(context) {
                            // Только значение
                            return `Значение: ${context.parsed.y.toLocaleString('ru-RU')}`;
                        },
                        afterLabel: function(context) {
                            // Дата операции
                            const date = new Date(context.parsed.x);
                            return date.toLocaleDateString('ru-RU', {
                                day: '2-digit',
                                month: 'short',
                                year: 'numeric'
                            });
                        }
                    }
                },
                zoom: {
                    pan: {
                        enabled: true,
                        mode: 'x',
                        modifierKey: null
                    },
                    zoom: {
                        wheel: {
                            enabled: true,
                            speed: 0.1
                        },
                        pinch: {
                            enabled: true
                        },
                        mode: 'x'
                    },
                    limits: {
                        x: {min: 'original', max: 'original'},
                    }
                }
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'day',
                        displayFormats: {
                            day: 'dd MMM yyyy',
                            hour: 'HH:mm'
                        },
                        tooltipFormat: 'dd MMM yyyy HH:mm'
                    },
                    title: {
                        display: true,
                        text: 'Дата операции',
                        font: {
                            size: 14,
                            weight: 'bold'
                        }
                    },
                    ticks: {
                        font: {
                            size: 12
                        },
                        maxRotation: 45,
                        minRotation: 0
                    },
                    grid: {
                        display: true,
                        color: 'rgba(0, 0, 0, 0.05)'
                    }
                },
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Значение',
                        font: {
                            size: 14,
                            weight: 'bold'
                        }
                    },
                    ticks: {
                        font: {
                            size: 12
                        },
                        callback: function(value) {
                            return value.toLocaleString('ru-RU');
                        }
                    },
                    grid: {
                        color: 'rgba(0, 0, 0, 0.08)'
                    }
                }
            }
        }
    };

    // Создаем график
    console.log('Создание графика Chart.js с конфигурацией:', config);
    const chart = new Chart(ctx, config);
    chartInstances[canvasId] = chart;

    console.log('График успешно создан:', chart);
    return chart;
}

/**
 * Загружает и отображает комбинированный график для метрики
 *
 * @param {string} containerSelector - CSS селектор контейнера для графика
 * @param {number} templateId - ID шаблона
 * @param {string} metricName - Название метрики
 * @param {string|null} filterFieldName - Поле фильтрации (опционально)
 * @param {string|null} filterFieldValue - Значение фильтра (опционально)
 * @param {number} limit - Максимальное количество точек
 */
async function loadCombinedChart(containerSelector, templateId, metricName, filterFieldName = null, filterFieldValue = null, limit = 50) {
    try {
        console.log(`Загрузка комбинированного графика для метрики: ${metricName}`);
        console.log(`Container selector: ${containerSelector}`);

        // Формируем URL для API запроса
        let url = `/statistics/history/all-groups?templateId=${templateId}&metricName=${encodeURIComponent(metricName)}&limit=${limit}`;

        if (filterFieldName && filterFieldValue) {
            url += `&filterFieldName=${encodeURIComponent(filterFieldName)}&filterFieldValue=${encodeURIComponent(filterFieldValue)}`;
        }

        console.log(`Fetching data from: ${url}`);

        // Загружаем данные
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const allGroupsData = await response.json();
        console.log(`Получено данных для ${allGroupsData.length} групп`);

        if (!allGroupsData || allGroupsData.length === 0) {
            console.warn('Нет данных для отображения комбинированного графика');
            const container = document.querySelector(containerSelector);
            if (container) {
                container.innerHTML = `
                    <div class="alert alert-info">
                        <i class="fas fa-info-circle me-2"></i>
                        Нет исторических данных для отображения комбинированного графика
                    </div>
                `;
            }
            return;
        }

        // Получаем контейнер
        const container = document.querySelector(containerSelector);
        if (!container) {
            console.error('Container not found:', containerSelector);
            return;
        }

        // Создаём HTML для графика
        const canvasId = `combined-chart-${metricName.replace(/[^a-zA-Z0-9]/g, '-')}`;
        const tableId = `trend-table-${canvasId}`;

        container.innerHTML = `
            <div class="col-12">
                <div class="card border-0 shadow-sm" id="chart-container-${canvasId}">
                    <div class="card-body" style="height: 600px; position: relative; display: flex; flex-direction: column;">
                        <!-- Панель управления сверху -->
                        <div class="chart-controls mb-3 d-flex justify-content-between align-items-center" style="flex-shrink: 0;">
                            <div class="btn-group btn-group-sm" role="group">
                                <button type="button" class="btn btn-outline-primary" onclick="resetChartZoom('${canvasId}')" title="Сбросить масштаб">
                                    <i class="fas fa-search-minus"></i>
                                </button>
                                <button type="button" class="btn btn-outline-secondary" onclick="toggleAllLines('${canvasId}')" title="Скрыть все линии">
                                    <i class="fas fa-eye-slash"></i>
                                </button>
                                <button type="button" class="btn btn-outline-success" onclick="downloadChartImage('${canvasId}', 'png')" title="Скачать PNG">
                                    <i class="fas fa-download"></i>
                                </button>
                                <button type="button" class="btn btn-outline-info" onclick="toggleFullscreen('${canvasId}')" title="Полный экран">
                                    <i class="fas fa-expand"></i>
                                </button>
                            </div>
                        </div>
                        <!-- Canvas занимает оставшееся пространство -->
                        <div style="flex-grow: 1; position: relative;">
                            <canvas id="${canvasId}"></canvas>
                        </div>
                    </div>
                </div>
                <!-- Таблица статистики трендов -->
                <div class="card border-0 shadow-sm mt-3">
                    <div class="card-header bg-light">
                        <h6 class="mb-0"><i class="fas fa-chart-bar me-2"></i>Детальная статистика трендов</h6>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-sm table-hover mb-0" id="${tableId}">
                                <thead class="table-light">
                                    <tr>
                                        <th style="width: 40px;"></th>
                                        <th>Группа</th>
                                        <th>Тренд</th>
                                        <th class="text-end">Min</th>
                                        <th class="text-end">Max</th>
                                        <th class="text-end">Среднее</th>
                                        <th class="text-end">Изменение</th>
                                        <th class="text-center">Действия</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <!-- Заполняется через JavaScript -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Создаём график после небольшой задержки, чтобы DOM успел обновиться
        setTimeout(() => {
            console.log(`Создание графика с ID: ${canvasId}`);
            const chart = createCombinedChart(canvasId, allGroupsData);

            // Заполняем таблицу статистики
            if (chart) {
                populateTrendTable(tableId, canvasId, allGroupsData);
            }
        }, 100);

    } catch (error) {
        console.error('Ошибка загрузки комбинированного графика:', error);
        const container = document.querySelector(containerSelector);
        if (container) {
            container.innerHTML = `
                <div class="alert alert-danger">
                    <i class="fas fa-exclamation-circle me-2"></i>
                    Ошибка загрузки графика: ${error.message}
                </div>
            `;
        }
    }
}

/**
 * Сбрасывает масштаб графика
 */
function resetChartZoom(canvasId) {
    const chart = chartInstances[canvasId];
    if (chart && chart.resetZoom) {
        chart.resetZoom();
    }
}

/**
 * Переключает видимость всех линий на графике
 */
function toggleAllLines(canvasId) {
    const chart = chartInstances[canvasId];
    if (!chart) {
        console.error('График не найден:', canvasId);
        return;
    }

    // Проверяем текущее состояние - все ли линии видимы
    const allVisible = chart.data.datasets.every((dataset, index) => {
        const meta = chart.getDatasetMeta(index);
        return !meta.hidden;
    });

    // Переключаем состояние всех линий
    chart.data.datasets.forEach((dataset, index) => {
        const meta = chart.getDatasetMeta(index);
        meta.hidden = allVisible; // Если все видимы - скрываем, иначе показываем
    });

    chart.update();

    // Обновляем текст кнопки
    const button = event.target.closest('button');
    if (button) {
        const icon = button.querySelector('i');
        const textNode = Array.from(button.childNodes).find(node => node.nodeType === Node.TEXT_NODE);

        if (allVisible) {
            icon.className = 'fas fa-eye me-1';
            if (textNode) textNode.textContent = 'Показать все';
        } else {
            icon.className = 'fas fa-eye-slash me-1';
            if (textNode) textNode.textContent = 'Скрыть все';
        }
    }
}

/**
 * Скачивает график как изображение
 */
function downloadChartImage(canvasId, format = 'png') {
    const chart = chartInstances[canvasId];
    if (!chart) {
        console.error('График не найден:', canvasId);
        return;
    }

    try {
        // Получаем изображение в base64
        const image = chart.toBase64Image();

        // Создаём ссылку для скачивания
        const link = document.createElement('a');
        link.href = image;
        link.download = `chart-${canvasId}-${new Date().getTime()}.${format}`;

        // Триггерим скачивание
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // Показываем уведомление
        showNotification('Графики сохранен как ' + format.toUpperCase(), 'success');
    } catch (error) {
        console.error('Ошибка при экспорте графика:', error);
        showNotification('Ошибка при экспорте графика', 'error');
    }
}

/**
 * Переключает полноэкранный режим для графика
 */
function toggleFullscreen(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error('Canvas не найден:', canvasId);
        return;
    }

    // Находим родительский card элемент
    const cardElement = canvas.closest('.card');
    if (!cardElement) {
        console.error('Card контейнер не найден');
        return;
    }

    // Добавляем обработчик события fullscreenchange для обновления иконки
    const updateFullscreenButton = () => {
        const button = cardElement.querySelector('.btn-outline-info');
        if (button) {
            const icon = button.querySelector('i');
            if (document.fullscreenElement === cardElement) {
                if (icon) icon.className = 'fas fa-compress';
                button.title = 'Выход из полноэкранного режима';
            } else {
                if (icon) icon.className = 'fas fa-expand';
                button.title = 'Полный экран';
            }
        }
    };

    // Добавляем слушатель один раз
    if (!cardElement.hasAttribute('data-fullscreen-listener')) {
        cardElement.setAttribute('data-fullscreen-listener', 'true');
        document.addEventListener('fullscreenchange', updateFullscreenButton);
        document.addEventListener('webkitfullscreenchange', updateFullscreenButton);
        document.addEventListener('mozfullscreenchange', updateFullscreenButton);
        document.addEventListener('MSFullscreenChange', updateFullscreenButton);
    }

    if (!document.fullscreenElement) {
        // Входим в полноэкранный режим
        if (cardElement.requestFullscreen) {
            cardElement.requestFullscreen().catch(err => {
                console.error('Ошибка при входе в fullscreen:', err);
                showNotification('Не удалось войти в полноэкранный режим', 'error');
            });
        } else if (cardElement.webkitRequestFullscreen) {
            cardElement.webkitRequestFullscreen();
        } else if (cardElement.msRequestFullscreen) {
            cardElement.msRequestFullscreen();
        }
    } else {
        // Выходим из полноэкранного режима
        if (document.exitFullscreen) {
            document.exitFullscreen();
        } else if (document.webkitExitFullscreen) {
            document.webkitExitFullscreen();
        } else if (document.msExitFullscreen) {
            document.msExitFullscreen();
        }
    }
}

/**
 * Показывает всплывающее уведомление
 */
function showNotification(message, type = 'info') {
    // Создаём уведомление
    const notification = document.createElement('div');
    notification.className = `alert alert-${type === 'success' ? 'success' : 'danger'} position-fixed`;
    notification.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px; animation: slideIn 0.3s ease-out;';
    notification.innerHTML = `
        <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'} me-2"></i>
        ${message}
    `;

    // Добавляем на страницу
    document.body.appendChild(notification);

    // Удаляем через 3 секунды
    setTimeout(() => {
        notification.style.animation = 'slideOut 0.3s ease-in';
        setTimeout(() => {
            document.body.removeChild(notification);
        }, 300);
    }, 3000);
}

/**
 * Заполняет таблицу статистики трендов
 */
function populateTrendTable(tableId, canvasId, allGroupsData) {
    const table = document.getElementById(tableId);
    if (!table) {
        console.error('Таблица не найдена:', tableId);
        return;
    }

    const tbody = table.querySelector('tbody');
    if (!tbody) {
        console.error('tbody не найден в таблице');
        return;
    }

    // Сохраняем оригинальные данные для сортировки
    table.dataset.originalData = JSON.stringify(allGroupsData);
    table.dataset.canvasId = canvasId;

    // Делаем заголовки кликабельными для сортировки
    const headers = table.querySelectorAll('thead th');
    headers.forEach((header, index) => {
        // Пропускаем первую (цвет) и последнюю (действия) колонки
        if (index > 0 && index < headers.length - 1) {
            header.style.cursor = 'pointer';
            header.style.userSelect = 'none';
            header.classList.add('sortable-header');

            // Добавляем обработчик клика
            header.addEventListener('click', () => {
                sortTable(tableId, index);
            });
        }
    });

    // Рендерим строки таблицы
    renderTableRows(tableId, canvasId, allGroupsData);
}

/**
 * Рендерит строки таблицы
 */
function renderTableRows(tableId, canvasId, allGroupsData) {
    const table = document.getElementById(tableId);
    const tbody = table.querySelector('tbody');

    // Очищаем tbody
    tbody.innerHTML = '';

    // Заполняем строки для каждой группы
    allGroupsData.forEach((groupData, index) => {
        const trendInfo = groupData.trendInfo;
        const dataPoints = groupData.dataPoints;

        // Вычисляем статистику
        const values = dataPoints.map(p => p.value);
        const min = Math.min(...values);
        const max = Math.max(...values);
        const avg = values.reduce((a, b) => a + b, 0) / values.length;

        // Получаем цвет линии из datasets
        const chart = chartInstances[canvasId];
        const dataset = chart?.data.datasets.find(ds => ds.groupValue === groupData.groupValue);
        const lineColor = dataset?.borderColor || '#6c757d';

        // Находим индекс dataset'а для правильной работы toggleLineVisibility
        const datasetIndex = chart?.data.datasets.findIndex(ds => ds.groupValue === groupData.groupValue) || 0;

        // Определяем класс для тренда
        const trendClass = getTrendClass(trendInfo.direction);
        const trendIcon = getTrendIcon(trendInfo.direction);

        // Создаём строку
        const row = document.createElement('tr');
        row.innerHTML = `
            <td style="background-color: ${lineColor}; border-left: 4px solid ${lineColor};"></td>
            <td><strong>${groupData.groupValue}</strong></td>
            <td><span class="badge ${trendClass}">${trendIcon} ${trendInfo.description}</span></td>
            <td class="text-end" data-value="${min}">${min.toLocaleString('ru-RU')}</td>
            <td class="text-end" data-value="${max}">${max.toLocaleString('ru-RU')}</td>
            <td class="text-end" data-value="${avg}">${avg.toFixed(0).toLocaleString('ru-RU')}</td>
            <td class="text-end" data-value="${trendInfo.changePercentage}">
                <span class="${trendInfo.direction.includes('GROWTH') ? 'text-success' : trendInfo.direction.includes('DECLINE') ? 'text-danger' : 'text-secondary'}">
                    ${trendInfo.changePercentage > 0 ? '+' : ''}${trendInfo.changePercentage.toFixed(1)}%
                </span>
            </td>
            <td class="text-center">
                <button class="btn btn-sm btn-outline-primary" onclick="toggleLineVisibility('${canvasId}', ${datasetIndex})" title="Показать/скрыть линию">
                    <i class="fas fa-eye"></i>
                </button>
            </td>
        `;

        tbody.appendChild(row);
    });
}

/**
 * Сортирует таблицу по указанной колонке
 */
function sortTable(tableId, columnIndex) {
    const table = document.getElementById(tableId);
    const canvasId = table.dataset.canvasId;
    const allGroupsData = JSON.parse(table.dataset.originalData);

    // Получаем текущее направление сортировки
    const headers = table.querySelectorAll('thead th');
    const currentHeader = headers[columnIndex];
    const currentDirection = currentHeader.dataset.sortDirection || 'asc';
    const newDirection = currentDirection === 'asc' ? 'desc' : 'asc';

    // Очищаем индикаторы сортировки у всех заголовков
    headers.forEach(h => {
        h.dataset.sortDirection = '';
        const icon = h.querySelector('.sort-icon');
        if (icon) icon.remove();
    });

    // Устанавливаем новое направление
    currentHeader.dataset.sortDirection = newDirection;

    // Добавляем иконку сортировки
    const icon = document.createElement('i');
    icon.className = `fas fa-sort-${newDirection === 'asc' ? 'up' : 'down'} ms-1 sort-icon`;
    currentHeader.appendChild(icon);

    // Сортируем данные
    const sortedData = [...allGroupsData].sort((a, b) => {
        let valueA, valueB;

        switch (columnIndex) {
            case 1: // Группа
                valueA = a.groupValue.toLowerCase();
                valueB = b.groupValue.toLowerCase();
                break;
            case 2: // Тренд
                // Сортируем по направлению тренда
                const trendOrder = { 'STRONG_GROWTH': 5, 'GROWTH': 4, 'STABLE': 3, 'DECLINE': 2, 'STRONG_DECLINE': 1 };
                valueA = trendOrder[a.trendInfo.direction] || 0;
                valueB = trendOrder[b.trendInfo.direction] || 0;
                break;
            case 3: // Min
                valueA = Math.min(...a.dataPoints.map(p => p.value));
                valueB = Math.min(...b.dataPoints.map(p => p.value));
                break;
            case 4: // Max
                valueA = Math.max(...a.dataPoints.map(p => p.value));
                valueB = Math.max(...b.dataPoints.map(p => p.value));
                break;
            case 5: // Среднее
                valueA = a.dataPoints.reduce((sum, p) => sum + p.value, 0) / a.dataPoints.length;
                valueB = b.dataPoints.reduce((sum, p) => sum + p.value, 0) / b.dataPoints.length;
                break;
            case 6: // Изменение
                valueA = a.trendInfo.changePercentage;
                valueB = b.trendInfo.changePercentage;
                break;
            default:
                return 0;
        }

        // Сравниваем значения
        if (typeof valueA === 'string') {
            return newDirection === 'asc'
                ? valueA.localeCompare(valueB, 'ru')
                : valueB.localeCompare(valueA, 'ru');
        } else {
            return newDirection === 'asc' ? valueA - valueB : valueB - valueA;
        }
    });

    // Перерендериваем таблицу с отсортированными данными
    renderTableRows(tableId, canvasId, sortedData);
}

/**
 * Возвращает CSS класс для badge тренда
 */
function getTrendClass(direction) {
    switch (direction) {
        case 'STRONG_GROWTH':
            return 'bg-success';
        case 'GROWTH':
            return 'bg-info';
        case 'DECLINE':
            return 'bg-warning text-dark';
        case 'STRONG_DECLINE':
            return 'bg-danger';
        default:
            return 'bg-secondary';
    }
}

/**
 * Переключает видимость конкретной линии
 */
function toggleLineVisibility(canvasId, datasetIndex) {
    const chart = chartInstances[canvasId];
    if (!chart) {
        console.error('График не найден:', canvasId);
        return;
    }

    const meta = chart.getDatasetMeta(datasetIndex);
    meta.hidden = !meta.hidden;
    chart.update();

    // Обновляем иконку кнопки
    const button = event.target.closest('button');
    if (button) {
        const icon = button.querySelector('i');
        if (meta.hidden) {
            icon.className = 'fas fa-eye-slash';
            button.classList.remove('btn-outline-primary');
            button.classList.add('btn-outline-secondary');
        } else {
            icon.className = 'fas fa-eye';
            button.classList.remove('btn-outline-secondary');
            button.classList.add('btn-outline-primary');
        }
    }
}
