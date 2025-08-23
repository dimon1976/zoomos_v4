// src/main/java/com/java/constants/UrlConstants.java
package com.java.constants;

public final class UrlConstants {

    private UrlConstants() {
        // Утилитный класс
    }

    // ===== MVC ROUTES (HTML страницы) =====

    // Главная страница
    public static final String HOME = "/";

    // Клиенты
    public static final String CLIENTS = "/clients";
    public static final String CLIENTS_CREATE = "/clients/create";
    public static final String CLIENT_DETAIL = "/clients/{clientId}";
    public static final String CLIENT_EDIT = "/clients/{clientId}/edit";
    public static final String CLIENT_DELETE = "/clients/{clientId}/delete";

    // Страницы клиента (вместо вкладок)
    public static final String CLIENT_IMPORT = "/clients/{clientId}/import";
    public static final String CLIENT_EXPORT = "/clients/{clientId}/export";
    public static final String CLIENT_TEMPLATES = "/clients/{clientId}/templates";
    public static final String CLIENT_STATISTICS = "/clients/{clientId}/statistics";
    public static final String CLIENT_OPERATIONS = "/clients/{clientId}/operations";

    // Шаблоны импорта
    public static final String CLIENT_IMPORT_TEMPLATES = "/clients/{clientId}/import/templates";
    public static final String CLIENT_IMPORT_TEMPLATES_CREATE = "/clients/{clientId}/import/templates/create";
    public static final String CLIENT_IMPORT_TEMPLATE_DETAIL = "/clients/{clientId}/import/templates/{templateId}";
    public static final String CLIENT_IMPORT_TEMPLATE_EDIT = "/clients/{clientId}/import/templates/{templateId}/edit";
    public static final String CLIENT_IMPORT_TEMPLATE_DELETE = "/clients/{clientId}/import/templates/{templateId}/delete";
    public static final String CLIENT_IMPORT_TEMPLATE_CLONE = "/clients/{clientId}/import/templates/{templateId}/clone";

    // Шаблоны экспорта
    public static final String CLIENT_EXPORT_TEMPLATES = "/clients/{clientId}/export/templates";
    public static final String CLIENT_EXPORT_TEMPLATES_CREATE = "/clients/{clientId}/export/templates/create";
    public static final String CLIENT_EXPORT_TEMPLATE_DETAIL = "/clients/{clientId}/export/templates/{templateId}";
    public static final String CLIENT_EXPORT_TEMPLATE_EDIT = "/clients/{clientId}/export/templates/{templateId}/edit";
    public static final String CLIENT_EXPORT_TEMPLATE_DELETE = "/clients/{clientId}/export/templates/{templateId}/delete";
    public static final String CLIENT_EXPORT_TEMPLATE_CLONE = "/clients/{clientId}/export/templates/{templateId}/clone";

    // Операции
    public static final String CLIENT_OPERATION_DETAIL = "/clients/{clientId}/operations/{operationId}";

    // Импорт файлов
    public static final String IMPORT_UPLOAD = "/clients/{clientId}/import/upload";
    public static final String IMPORT_ANALYZE = "/clients/{clientId}/import/analyze";
    public static final String IMPORT_START = "/clients/{clientId}/import/start";
    public static final String IMPORT_CANCEL = "/clients/{clientId}/import/cancel";

    // Экспорт
    public static final String EXPORT_START = "/clients/{clientId}/export/start";

    // Настройки
    public static final String SETTINGS = "/settings";
    public static final String SETTINGS_STATISTICS = "/settings/statistics";

    // Утилиты и статистика
    public static final String UTILS = "/utils";
    public static final String STATS = "/stats";

    // ===== REST API ROUTES (JSON ответы) =====

    public static final String API_BASE = "/api";

    // API клиентов
    public static final String API_CLIENTS = "/api/clients";
    public static final String API_CLIENT_OPERATIONS = "/api/clients/{clientId}/operations";
    public static final String API_CLIENT_STATS = "/api/clients/{clientId}/stats";

    // API операций
    public static final String API_OPERATIONS = "/api/operations";
    public static final String API_OPERATION_STATUS = "/api/operations/{operationId}/status";
    public static final String API_OPERATION_DELETE = "/api/operations/{operationId}";

    // API дашборда
    public static final String API_DASHBOARD_STATS = "/api/dashboard/stats";
    public static final String API_DASHBOARD_OPERATIONS = "/api/dashboard/operations";
    public static final String API_DASHBOARD_REFRESH = "/api/dashboard/refresh";
    public static final String API_DASHBOARD_CLIENTS = "/api/dashboard/clients";
    public static final String API_DASHBOARD_FILE_TYPES = "/api/dashboard/file-types";

    // API импорта
    public static final String API_IMPORT_PROGRESS = "/api/import/progress/{sessionId}";
    public static final String API_IMPORT_PROGRESS_BY_OPERATION = "/api/import/progress/operation/{operationId}";
    public static final String API_IMPORT_CANCEL = "/api/import/cancel/{operationId}";
    public static final String API_IMPORT_TEMPLATES_SUMMARY = "/api/import/templates/client/{clientId}/summary";

    // API экспорта
    public static final String API_EXPORT_PROGRESS = "/api/export/progress/{sessionId}";
    public static final String API_EXPORT_PROGRESS_BY_OPERATION = "/api/export/progress/operation/{operationId}";
    public static final String API_EXPORT_TEMPLATES_SUMMARY = "/api/export/templates/client/{clientId}/summary";

    // API шаблонов
    public static final String API_TEMPLATE_FIELDS = "/api/templates/{templateId}/fields";
    public static final String API_AVAILABLE_FIELDS = "/api/templates/available-fields";

    // API статистики
    public static final String API_STATISTICS_ANALYZE = "/api/statistics/analyze";
    public static final String API_STATISTICS_PREVIEW = "/api/statistics/preview";
    public static final String API_STATISTICS_SAVED = "/api/statistics/session/{sessionId}/saved";

    // ===== URL PARAMETERS =====

    public static final String PARAM_CLIENT_ID = "clientId";
    public static final String PARAM_TEMPLATE_ID = "templateId";
    public static final String PARAM_OPERATION_ID = "operationId";
    public static final String PARAM_SESSION_ID = "sessionId";
}