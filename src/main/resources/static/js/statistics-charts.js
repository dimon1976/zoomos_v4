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
        const date = new Date(point.date);
        return date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
    });

    const values = historyData.dataPoints.map(point => point.value);

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
                    borderWidth: 2,
                    fill: true,
                    tension: 0.3, // Сглаживание линии
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: trendColor.point,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2
                },
                {
                    label: 'Линия тренда',
                    data: trendLine,
                    borderColor: trendColor.trend,
                    borderWidth: 2,
                    borderDash: [5, 5], // Пунктирная линия
                    fill: false,
                    pointRadius: 0,
                    tension: 0
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 2,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                title: {
                    display: true,
                    text: historyData.groupValue,
                    font: {
                        size: 14,
                        weight: 'bold'
                    }
                },
                subtitle: {
                    display: true,
                    text: historyData.trendInfo.description,
                    font: {
                        size: 11,
                        style: 'italic'
                    },
                    color: trendColor.text
                },
                legend: {
                    display: true,
                    position: 'bottom'
                },
                tooltip: {
                    enabled: true,
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
                            return 'Дата: ' + date.toLocaleDateString('ru-RU', {
                                day: '2-digit',
                                month: 'long',
                                year: 'numeric',
                                hour: '2-digit',
                                minute: '2-digit'
                            });
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: function(value) {
                            return value.toLocaleString('ru-RU');
                        }
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
                <div class="card-body">
                    <canvas id="chart-${metricName}-${index}" height="200"></canvas>
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
