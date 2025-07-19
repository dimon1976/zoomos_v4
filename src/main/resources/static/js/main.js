/**
 * Основные функции JavaScript для приложения обработки файлов
 */

// Инициализация при загрузке DOM
document.addEventListener('DOMContentLoaded', function() {
    console.log('Приложение инициализировано');

    // Инициализация всплывающих подсказок Bootstrap
    initTooltips();

    // Инициализация валидации форм
    initFormValidation();

    // Инициализация автоматического скрытия уведомлений
    initAlertDismissal();
});

/**
 * Инициализирует всплывающие подсказки Bootstrap
 */
function initTooltips() {
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
}

/**
 * Инициализирует валидацию форм на стороне клиента
 */
function initFormValidation() {
    const forms = document.querySelectorAll('.needs-validation');

    Array.from(forms).forEach(form => {
        form.addEventListener('submit', event => {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }

            form.classList.add('was-validated');
        }, false);
    });
}

/**
 * Инициализирует автоматическое скрытие уведомлений через некоторое время
 */
function initAlertDismissal() {
    const successAlerts = document.querySelectorAll('.alert-success');

    successAlerts.forEach(alert => {
        // Автоматически скрываем уведомление об успехе через 5 секунд
        setTimeout(() => {
            const bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        }, 5000);
    });
}

/**
 * Форматирует размер файла в читаемый вид
 * @param {number} size Размер файла в байтах
 * @returns {string} Форматированный размер файла
 */
function formatFileSize(size) {
    const KB = 1024;
    const MB = KB * 1024;
    const GB = MB * 1024;

    if (size < KB) {
        return size + ' байт';
    } else if (size < MB) {
        return (size / KB).toFixed(2) + ' КБ';
    } else if (size < GB) {
        return (size / MB).toFixed(2) + ' МБ';
    } else {
        return (size / GB).toFixed(2) + ' ГБ';
    }
}

/**
 * Переключает видимость элемента
 * @param {string} elementId ID элемента
 */
function toggleElement(elementId) {
    const element = document.getElementById(elementId);
    if (element) {
        element.classList.toggle('d-none');
    }
}

/**
 * Активирует указанную вкладку
 * @param {string} tabId ID вкладки
 */
function activateTab(tabId) {
    const tabElement = document.querySelector(`#${tabId}`);
    if (tabElement) {
        const tab = new bootstrap.Tab(tabElement);
        tab.show();
    }
}

/**
 * Отображает модальное окно подтверждения с выполнением callback
 * @param {string} message Сообщение для отображения
 * @param {Function} confirmCallback Функция, выполняемая при подтверждении
 * @param {Function} cancelCallback Функция, выполняемая при отмене
 */
function showConfirmation(message, confirmCallback, cancelCallback) {
    if (confirm(message)) {
        if (typeof confirmCallback === 'function') {
            confirmCallback();
        }
    } else {
        if (typeof cancelCallback === 'function') {
            cancelCallback();
        }
    }
}