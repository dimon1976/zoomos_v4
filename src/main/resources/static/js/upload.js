/*
 * Универсальная библиотека для обработки загрузки файлов
 * Поддерживает drag-and-drop, предпросмотр, валидацию
 * Используется во всех страницах с функциональностью upload
 */

class UploadManager {
    constructor(config) {
        this.config = {
            zoneId: null,
            inputId: null,
            previewId: null,
            buttonId: null,
            allowedTypes: [],
            maxFileSize: 600 * 1024 * 1024, // 600MB по умолчанию
            multiple: false,
            autoUpload: false,
            showFileSize: true,
            showFileType: true,
            onFileSelect: null,
            onFileRemove: null,
            onValidationError: null,
            onDragStart: null,
            onDragEnd: null,
            customValidation: null,
            ...config
        };

        this.zone = null;
        this.input = null;
        this.preview = null;
        this.button = null;
        this.selectedFiles = [];

        this.init();
    }

    init() {
        this.findElements();
        this.bindEvents();
        this.setupDragAndDrop();
    }

    findElements() {
        this.zone = document.getElementById(this.config.zoneId);
        this.input = document.getElementById(this.config.inputId);
        this.preview = document.getElementById(this.config.previewId);
        this.button = document.getElementById(this.config.buttonId);

        if (!this.zone || !this.input || !this.preview) {
            console.error('UploadManager: Не найдены обязательные элементы', {
                zone: this.config.zoneId,
                input: this.config.inputId,
                preview: this.config.previewId
            });
        }
    }

    bindEvents() {
        if (this.input) {
            this.input.addEventListener('change', (e) => this.handleFileSelect(e));
        }

        if (this.button) {
            this.button.addEventListener('click', (e) => {
                e.stopPropagation();
                this.input.click();
            });
        }

        if (this.zone) {
            this.zone.addEventListener('click', (e) => {
                // Игнорируем клики по кнопке и её дочерним элементам
                if (e.target.closest('.select-files-btn')) {
                    return;
                }
                this.input.click();
            });
        }
    }

    setupDragAndDrop() {
        if (!this.zone) return;

        this.zone.addEventListener('dragover', (e) => {
            e.preventDefault();
            this.zone.classList.add('dragover');
            if (this.config.onDragStart) {
                this.config.onDragStart(e);
            }
        });

        this.zone.addEventListener('dragleave', (e) => {
            e.preventDefault();
            // Проверяем, что мы действительно покидаем зону (не переходим к дочернему элементу)
            if (!this.zone.contains(e.relatedTarget)) {
                this.zone.classList.remove('dragover');
                if (this.config.onDragEnd) {
                    this.config.onDragEnd(e);
                }
            }
        });

        this.zone.addEventListener('drop', (e) => {
            e.preventDefault();
            this.zone.classList.remove('dragover');

            const files = e.dataTransfer.files;
            this.handleFiles(files);

            if (this.config.onDragEnd) {
                this.config.onDragEnd(e);
            }
        });
    }

    handleFileSelect(event) {
        const files = event.target.files;
        this.handleFiles(files);
    }

    handleFiles(files) {
        if (!files || files.length === 0) {
            this.clearFiles();
            return;
        }

        // Проверяем количество файлов
        if (!this.config.multiple && files.length > 1) {
            this.showError('Можно выбрать только один файл');
            return;
        }

        // Валидируем каждый файл
        const validFiles = [];
        for (let i = 0; i < files.length; i++) {
            const file = files[i];

            if (this.validateFile(file)) {
                validFiles.push(file);
            }
        }

        if (validFiles.length === 0) {
            this.clearFiles();
            return;
        }

        // Обновляем input с валидными файлами
        if (validFiles.length !== files.length) {
            this.updateInputFiles(validFiles);
        }

        this.selectedFiles = validFiles;
        this.displayPreview();

        // Вызываем callback
        if (this.config.onFileSelect) {
            this.config.onFileSelect(validFiles);
        }
    }

    validateFile(file) {
        // Проверка размера файла
        if (file.size > this.config.maxFileSize) {
            this.showError(`Файл "${file.name}" превышает максимальный размер ${this.formatFileSize(this.config.maxFileSize)}`);
            return false;
        }

        // Проверка типа файла
        if (this.config.allowedTypes.length > 0) {
            const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
            const isAllowed = this.config.allowedTypes.some(type =>
                type.toLowerCase() === fileExtension ||
                type.toLowerCase() === file.type
            );

            if (!isAllowed) {
                this.showError(`Файл "${file.name}" имеет неподдерживаемый формат. Разрешенные форматы: ${this.config.allowedTypes.join(', ')}`);
                return false;
            }
        }

        // Кастомная валидация
        if (this.config.customValidation) {
            const result = this.config.customValidation(file);
            if (result !== true) {
                this.showError(typeof result === 'string' ? result : `Файл "${file.name}" не прошел валидацию`);
                return false;
            }
        }

        return true;
    }

    updateInputFiles(files) {
        const dt = new DataTransfer();
        files.forEach(file => dt.items.add(file));
        this.input.files = dt.files;
    }

    displayPreview() {
        if (!this.preview) return;

        this.preview.innerHTML = '';

        if (this.selectedFiles.length === 0) {
            this.preview.style.display = 'none';
            return;
        }

        this.preview.style.display = 'block';

        const container = document.createElement('div');
        container.className = 'file-preview-list';

        if (this.selectedFiles.length > 1) {
            const header = document.createElement('h6');
            header.textContent = `Выбранные файлы (${this.selectedFiles.length}):`;
            header.className = 'mb-3';
            container.appendChild(header);
        }

        this.selectedFiles.forEach((file, index) => {
            const previewElement = this.createFilePreview(file, index);
            container.appendChild(previewElement);
        });

        this.preview.appendChild(container);
    }

    createFilePreview(file, index) {
        const div = document.createElement('div');
        div.className = 'file-preview';

        const fileInfo = document.createElement('div');
        fileInfo.className = 'file-info';

        const fileName = document.createElement('div');
        fileName.className = 'file-name';
        fileName.textContent = file.name;
        fileInfo.appendChild(fileName);

        if (this.config.showFileSize) {
            const fileSize = document.createElement('div');
            fileSize.className = 'file-size';
            fileSize.textContent = this.formatFileSize(file.size);
            fileInfo.appendChild(fileSize);
        }

        if (this.config.showFileType) {
            const fileType = document.createElement('div');
            fileType.className = 'file-type';
            fileType.textContent = this.getFileType(file);
            fileInfo.appendChild(fileType);
        }

        const removeBtn = document.createElement('span');
        removeBtn.className = 'remove-file';
        removeBtn.innerHTML = '<i class="fas fa-times"></i>';
        removeBtn.title = 'Удалить файл';
        removeBtn.addEventListener('click', () => this.removeFile(index));

        div.appendChild(fileInfo);
        div.appendChild(removeBtn);

        return div;
    }

    removeFile(index) {
        if (index < 0 || index >= this.selectedFiles.length) return;

        const removedFile = this.selectedFiles[index];
        this.selectedFiles.splice(index, 1);

        // Обновляем input
        this.updateInputFiles(this.selectedFiles);

        // Обновляем предпросмотр
        this.displayPreview();

        // Вызываем callback
        if (this.config.onFileRemove) {
            this.config.onFileRemove(removedFile, index);
        }
    }

    clearFiles() {
        this.selectedFiles = [];
        this.input.value = '';
        if (this.preview) {
            this.preview.innerHTML = '';
            this.preview.style.display = 'none';
        }
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    getFileType(file) {
        const extension = file.name.split('.').pop().toLowerCase();
        return extension.toUpperCase();
    }

    showError(message) {
        if (this.config.onValidationError) {
            this.config.onValidationError(message);
        } else {
            // Создаем простое уведомление
            this.showNotification(message, 'error');
        }
    }

    showNotification(message, type = 'info') {
        // Ищем контейнер для уведомлений
        let notificationContainer = document.getElementById('upload-notifications');

        if (!notificationContainer) {
            // Создаем контейнер, если его нет
            notificationContainer = document.createElement('div');
            notificationContainer.id = 'upload-notifications';
            notificationContainer.className = 'upload-notifications';

            // Вставляем контейнер перед зоной загрузки
            if (this.zone && this.zone.parentNode) {
                this.zone.parentNode.insertBefore(notificationContainer, this.zone);
            } else {
                document.body.appendChild(notificationContainer);
            }
        }

        const alertClass = this.getAlertClass(type);
        const icon = this.getAlertIcon(type);

        const alertDiv = document.createElement('div');
        alertDiv.className = `alert ${alertClass} alert-dismissible fade show`;
        alertDiv.innerHTML = `
            <i class="${icon} me-2"></i>
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        notificationContainer.appendChild(alertDiv);

        // Автоматически скрываем через 5 секунд
        setTimeout(() => {
            if (alertDiv.parentNode) {
                alertDiv.remove();
            }
        }, 5000);
    }

    getAlertClass(type) {
        switch (type) {
            case 'success': return 'alert-success';
            case 'error':
            case 'danger': return 'alert-danger';
            case 'warning': return 'alert-warning';
            default: return 'alert-info';
        }
    }

    getAlertIcon(type) {
        switch (type) {
            case 'success': return 'fas fa-check-circle';
            case 'error':
            case 'danger': return 'fas fa-exclamation-triangle';
            case 'warning': return 'fas fa-exclamation-triangle';
            default: return 'fas fa-info-circle';
        }
    }

    // Публичные методы для управления
    getSelectedFiles() {
        return this.selectedFiles;
    }

    setFiles(files) {
        this.handleFiles(files);
    }

    reset() {
        this.clearFiles();
    }

    setLoading(loading) {
        if (this.zone) {
            if (loading) {
                this.zone.classList.add('upload-loading');
            } else {
                this.zone.classList.remove('upload-loading');
            }
        }
    }

    disable() {
        if (this.zone) this.zone.classList.add('disabled');
        if (this.input) this.input.disabled = true;
        if (this.button) this.button.disabled = true;
    }

    enable() {
        if (this.zone) this.zone.classList.remove('disabled');
        if (this.input) this.input.disabled = false;
        if (this.button) this.button.disabled = false;
    }

    /**
     * Очищает event listeners и освобождает ресурсы
     * Вызывайте этот метод при уничтожении компонента для предотвращения утечек памяти
     */
    destroy() {
        // Удаляем event listeners
        if (this.input) {
            this.input.removeEventListener('change', this.handleFileSelect);
        }

        if (this.button) {
            this.button.removeEventListener('click', this.handleButtonClick);
        }

        if (this.zone) {
            this.zone.removeEventListener('click', this.handleZoneClick);
            this.zone.removeEventListener('dragover', this.handleDragOver);
            this.zone.removeEventListener('dragleave', this.handleDragLeave);
            this.zone.removeEventListener('drop', this.handleDrop);
        }

        // Очищаем файлы и preview
        this.clearFiles();

        // Удаляем контейнер уведомлений, если он был создан
        const notificationContainer = document.getElementById('upload-notifications');
        if (notificationContainer && notificationContainer.parentNode) {
            notificationContainer.remove();
        }

        // Очищаем ссылки на DOM элементы
        this.zone = null;
        this.input = null;
        this.preview = null;
        this.button = null;
        this.selectedFiles = [];
        this.config = null;
    }
}

// Утилитарные функции для создания стандартных конфигураций
const UploadConfig = {
    // Конфигурация для импорта файлов
    importFiles: (zoneId, inputId, previewId, buttonId) => ({
        zoneId,
        inputId,
        previewId,
        buttonId,
        allowedTypes: ['.csv', '.xlsx', '.xls', '.txt'],
        maxFileSize: 600 * 1024 * 1024, // 600MB
        multiple: true,
        showFileSize: true,
        showFileType: true
    }),

    // Конфигурация для Data Merger - исходный файл
    dataMergerSource: (zoneId, inputId, previewId, buttonId) => ({
        zoneId,
        inputId,
        previewId,
        buttonId,
        allowedTypes: ['.csv', '.xlsx', '.xls'],
        maxFileSize: 100 * 1024 * 1024, // 100MB
        multiple: false,
        showFileSize: true,
        showFileType: true
    }),

    // Конфигурация для Data Merger - файл ссылок
    dataMergerLinks: (zoneId, inputId, previewId, buttonId) => ({
        zoneId,
        inputId,
        previewId,
        buttonId,
        allowedTypes: ['.csv', '.xlsx', '.xls'],
        maxFileSize: 100 * 1024 * 1024, // 100MB
        multiple: false,
        showFileSize: true,
        showFileType: true
    }),

    // Конфигурация для редиректов
    redirectFiles: (zoneId, inputId, previewId, buttonId) => ({
        zoneId,
        inputId,
        previewId,
        buttonId,
        allowedTypes: ['.csv', '.xlsx', '.xls'],
        maxFileSize: 50 * 1024 * 1024, // 50MB
        multiple: false,
        showFileSize: true,
        showFileType: true
    })
};

// Глобальные функции для обратной совместимости
window.UploadManager = UploadManager;
window.UploadConfig = UploadConfig;

// Функция для инициализации upload зон на странице
function initializeUploadZones(configs) {
    const managers = {};

    configs.forEach(config => {
        if (config.id && document.getElementById(config.zoneId)) {
            managers[config.id] = new UploadManager(config);
        }
    });

    return managers;
}

window.initializeUploadZones = initializeUploadZones;