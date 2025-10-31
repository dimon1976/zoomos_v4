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
            animation: false, // Отключаем анимацию для предотвращения проблем со скрытыми вкладками
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                title: {
                    display: false  // Заголовок теперь в HTML (можно выделить и скопировать)
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
                        top: 5,
                        bottom: 10
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

        // Очищаем метрику от спецсимволов для использования в ID
        const safeMetricName = metricName.replace(/[^a-zA-Z0-9]/g, '-');

        console.log(`Loading charts for metric: ${metricName} (safe: ${safeMetricName}), groups: ${allGroupsData.length}`);
        console.log('First group data sample:', allGroupsData[0]);

        // Уничтожаем только графики текущей метрики (если они уже были созданы)
        Object.keys(chartInstances).forEach(chartId => {
            if (chartInstances[chartId] && chartId.startsWith(`chart-${safeMetricName}-`)) {
                console.log(`Destroying existing chart: ${chartId}`);
                chartInstances[chartId].destroy();
                delete chartInstances[chartId];
            }
        });

        // Очищаем контейнер (графики для этой метрики будут созданы заново)
        container.innerHTML = '';

        // Создаем графики для каждой группы
        allGroupsData.forEach((historyData, index) => {
            // Создаем wrapper для графика
            const chartWrapper = document.createElement('div');
            chartWrapper.className = 'col-md-6 col-lg-4 mb-4';

            const chartCard = document.createElement('div');
            chartCard.className = 'card shadow-sm';

            // Используем безопасное имя метрики для ID
            const canvasId = `chart-${safeMetricName}-${index}`;

            // Добавляем HTML-заголовок с названием сайта конкурента (можно выделить и скопировать)
            chartCard.innerHTML = `
                <div class="card-header bg-light border-bottom">
                    <h6 class="mb-0 text-primary fw-bold">${historyData.groupValue}</h6>
                </div>
                <div class="card-body" style="height: 380px;">
                    <canvas id="${canvasId}"></canvas>
                </div>
            `;

            chartWrapper.appendChild(chartCard);
            container.appendChild(chartWrapper);
        });

        // Создаем графики после добавления всех canvas в DOM
        // Увеличенная задержка чтобы вкладка успела стать видимой и canvas отрендериться
        setTimeout(() => {
            allGroupsData.forEach((historyData, index) => {
                const canvasId = `chart-${safeMetricName}-${index}`;
                const canvas = document.getElementById(canvasId);

                if (canvas) {
                    // Проверяем, что canvas имеет ненулевую ширину (вкладка видима)
                    const canvasWidth = canvas.offsetWidth;
                    console.log(`Creating chart: ${canvasId} for ${historyData.metricName}, canvas width: ${canvasWidth}px`);

                    if (canvasWidth > 0) {
                        createTrendChart(canvasId, historyData);
                    } else {
                        console.warn(`Canvas ${canvasId} has zero width - tab might be hidden`);
                        // Все равно создаем график, он будет обновлен позже через resize
                        createTrendChart(canvasId, historyData);
                    }
                } else {
                    console.error(`Canvas element not found: ${canvasId}`);
                }
            });
        }, 200);

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

