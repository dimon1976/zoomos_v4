package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.service.client.ClientService;
import com.java.service.exports.ExportTemplateService;
import com.java.service.imports.ImportTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class BreadcrumbAdvice {

    private final ClientService clientService;
    private final ImportTemplateService importTemplateService;
    private final ExportTemplateService exportTemplateService;

    @ModelAttribute("breadcrumbs")
    public List<BreadcrumbItem> breadcrumbs(HttpServletRequest request) {
        List<BreadcrumbItem> crumbs = new ArrayList<>();
        String path = request.getRequestURI();

        log.debug("Формируем хлебные крошки для пути: {}", path);

        // Игнорируем API пути
        if (path.startsWith(UrlConstants.API_BASE)) {
            return crumbs;
        }

        // Всегда добавляем главную страницу
        crumbs.add(new BreadcrumbItem("Главная", UrlConstants.HOME));

        // Разбираем путь на сегменты
        String[] segments = path.split("/");
        if (segments.length <= 1) {
            return crumbs; // Только главная страница
        }

        try {
            processBreadcrumbSegments(crumbs, segments);
        } catch (Exception e) {
            log.warn("Ошибка при формировании хлебных крошек для пути {}: {}", path, e.getMessage());
        }

        return crumbs;
    }

    private void processBreadcrumbSegments(List<BreadcrumbItem> crumbs, String[] segments) {
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];

            switch (segment) {
                case "clients" -> handleClientsSegment(crumbs, segments, i);
                case "settings" -> handleSettingsSegment(crumbs, segments, i);
                case "utils" -> crumbs.add(new BreadcrumbItem("Утилиты", UrlConstants.UTILS));
                case "stats" -> crumbs.add(new BreadcrumbItem("Статистика", UrlConstants.STATS));
                default -> {
                    if (isNumeric(segment)) {
                        handleNumericSegment(crumbs, segments, i);
                    }
                }
            }
        }
    }

    private void handleClientsSegment(List<BreadcrumbItem> crumbs, String[] segments, int index) {
        crumbs.add(new BreadcrumbItem("Клиенты", UrlConstants.CLIENTS));

        // Проверяем следующий сегмент
        if (index + 1 < segments.length) {
            String nextSegment = segments[index + 1];

            if ("create".equals(nextSegment)) {
                crumbs.add(new BreadcrumbItem("Создание", UrlConstants.CLIENTS_CREATE));
            } else if (isNumeric(nextSegment)) {
                // Обрабатываем /clients/{clientId}/...
                Long clientId = Long.parseLong(nextSegment);
                handleClientSegment(crumbs, segments, index + 1, clientId);
            }
        }
    }

    private void handleClientSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, Long clientId) {
        String clientName = getClientName(clientId);
        String clientPath = "/clients/" + clientId;
        crumbs.add(new BreadcrumbItem(clientName, clientPath));

        // Проверяем следующий сегмент после clientId
        if (index + 1 < segments.length) {
            String nextSegment = segments[index + 1];

            switch (nextSegment) {
                case "edit" -> crumbs.add(new BreadcrumbItem("Редактирование", clientPath + "/edit"));
                case "import" -> handleImportSegment(crumbs, segments, index + 1, clientId, clientPath);
                case "export" -> handleExportSegment(crumbs, segments, index + 1, clientId, clientPath);
                case "templates" -> crumbs.add(new BreadcrumbItem("Шаблоны", clientPath + "/templates"));
                case "statistics" -> crumbs.add(new BreadcrumbItem("Статистика", clientPath + "/statistics"));
                case "operations" -> handleOperationsSegment(crumbs, segments, index + 1, clientId, clientPath);
            }
        }
    }

    private void handleImportSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, Long clientId, String clientPath) {
        crumbs.add(new BreadcrumbItem("Импорт", clientPath + "/import"));

        // Проверяем следующий сегмент после import
        if (index + 1 < segments.length && "templates".equals(segments[index + 1])) {
            crumbs.add(new BreadcrumbItem("Шаблоны импорта", clientPath + "/import/templates"));

            // Проверяем дальнейшие сегменты
            if (index + 2 < segments.length) {
                String action = segments[index + 2];
                if ("create".equals(action)) {
                    crumbs.add(new BreadcrumbItem("Создание", clientPath + "/import/templates/create"));
                } else if (isNumeric(action)) {
                    Long templateId = Long.parseLong(action);
                    String templateName = getImportTemplateName(templateId);
                    crumbs.add(new BreadcrumbItem(templateName, clientPath + "/import/templates/" + templateId));

                    if (index + 3 < segments.length && "edit".equals(segments[index + 3])) {
                        crumbs.add(new BreadcrumbItem("Редактирование", clientPath + "/import/templates/" + templateId + "/edit"));
                    }
                }
            }
        }
    }

    private void handleExportSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, Long clientId, String clientPath) {
        crumbs.add(new BreadcrumbItem("Экспорт", clientPath + "/export"));

        // Проверяем следующий сегмент после export
        if (index + 1 < segments.length && "templates".equals(segments[index + 1])) {
            crumbs.add(new BreadcrumbItem("Шаблоны экспорта", clientPath + "/export/templates"));

            // Проверяем дальнейшие сегменты
            if (index + 2 < segments.length) {
                String action = segments[index + 2];
                if ("create".equals(action)) {
                    crumbs.add(new BreadcrumbItem("Создание", clientPath + "/export/templates/create"));
                } else if (isNumeric(action)) {
                    Long templateId = Long.parseLong(action);
                    String templateName = getExportTemplateName(templateId);
                    crumbs.add(new BreadcrumbItem(templateName, clientPath + "/export/templates/" + templateId));

                    if (index + 3 < segments.length && "edit".equals(segments[index + 3])) {
                        crumbs.add(new BreadcrumbItem("Редактирование", clientPath + "/export/templates/" + templateId + "/edit"));
                    }
                }
            }
        }
    }

    private void handleOperationsSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, Long clientId, String clientPath) {
        crumbs.add(new BreadcrumbItem("Операции", clientPath + "/operations"));

        // Проверяем следующий сегмент после operations
        if (index + 1 < segments.length && isNumeric(segments[index + 1])) {
            Long operationId = Long.parseLong(segments[index + 1]);
            crumbs.add(new BreadcrumbItem("Операция #" + operationId, clientPath + "/operations/" + operationId));
        }
    }

    private void handleSettingsSegment(List<BreadcrumbItem> crumbs, String[] segments, int index) {
        crumbs.add(new BreadcrumbItem("Настройки", UrlConstants.SETTINGS));

        // Проверяем подразделы настроек
        if (index + 1 < segments.length) {
            String nextSegment = segments[index + 1];
            if ("statistics".equals(nextSegment)) {
                crumbs.add(new BreadcrumbItem("Статистика", UrlConstants.SETTINGS_STATISTICS));
            }
        }
    }

    private void handleNumericSegment(List<BreadcrumbItem> crumbs, String[] segments, int index) {
        // Обработка числовых сегментов уже включена в соответствующие методы
        // Этот метод остается для совместимости
    }

    private String getClientName(Long clientId) {
        try {
            return clientService.getClientById(clientId)
                    .map(client -> client.getName())
                    .orElse("Клиент #" + clientId);
        } catch (Exception e) {
            log.warn("Не удалось получить имя клиента с ID {}: {}", clientId, e.getMessage());
            return "Клиент #" + clientId;
        }
    }

    private String getImportTemplateName(Long templateId) {
        try {
            return importTemplateService.getTemplate(templateId)
                    .map(template -> template.getName())
                    .orElse("Шаблон импорта #" + templateId);
        } catch (Exception e) {
            log.warn("Не удалось получить имя шаблона импорта с ID {}: {}", templateId, e.getMessage());
            return "Шаблон импорта #" + templateId;
        }
    }

    private String getExportTemplateName(Long templateId) {
        try {
            return exportTemplateService.getTemplate(templateId)
                    .map(template -> template.getName())
                    .orElse("Шаблон экспорта #" + templateId);
        } catch (Exception e) {
            log.warn("Не удалось получить имя шаблона экспорта с ID {}: {}", templateId, e.getMessage());
            return "Шаблон экспорта #" + templateId;
        }
    }

    private boolean isNumeric(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Data
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String label;
        private String url;
    }
}