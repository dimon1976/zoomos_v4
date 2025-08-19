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

/**
 * Централизованное формирование хлебных крошек для всех страниц
 */
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
        StringBuilder currentPath = new StringBuilder();

        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            currentPath.append("/").append(segment);

            switch (segment) {
                case UrlConstants.SEGMENT_CLIENTS -> handleClientsSegment(crumbs, segments, i, currentPath);
                case UrlConstants.SEGMENT_SETTINGS -> handleSettingsSegment(crumbs, segments, i, currentPath);
                default -> {
                    if (isNumeric(segment)) {
                        handleNumericSegment(crumbs, segments, i, segment, currentPath.toString());
                    } else {
                        handleNamedSegment(crumbs, segments, i, segment, currentPath.toString());
                    }
                }
            }
        }
    }

    private void handleClientsSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, StringBuilder currentPath) {
        crumbs.add(new BreadcrumbItem("Клиенты", UrlConstants.CLIENTS));

        // Проверяем следующий сегмент для обработки /clients/create
        if (index + 1 < segments.length && "create".equals(segments[index + 1])) {
            crumbs.add(new BreadcrumbItem("Создание", UrlConstants.CLIENTS_CREATE));
        }
    }

    private void handleSettingsSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, StringBuilder currentPath) {
        crumbs.add(new BreadcrumbItem("Настройки", UrlConstants.SETTINGS));

        // Проверяем подразделы настроек
        if (index + 1 < segments.length) {
            String nextSegment = segments[index + 1];
            if (UrlConstants.SEGMENT_STATISTICS.equals(nextSegment)) {
                crumbs.add(new BreadcrumbItem("Статистика", UrlConstants.SETTINGS_STATISTICS));
            }
        }
    }

    private void handleNumericSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, String segment, String currentPath) {
        Long id = Long.parseLong(segment);

        if (index > 1) {
            String prevSegment = segments[index - 1];

            switch (prevSegment) {
                case UrlConstants.SEGMENT_CLIENTS -> handleClientId(crumbs, segments, index, id, currentPath);
                case UrlConstants.SEGMENT_TEMPLATES -> handleTemplateId(crumbs, segments, index, id, currentPath);
                case UrlConstants.SEGMENT_OPERATIONS -> crumbs.add(new BreadcrumbItem("Операция #" + id, currentPath));
            }
        }
    }

    private void handleClientId(List<BreadcrumbItem> crumbs, String[] segments, int index, Long clientId, String currentPath) {
        String clientName = getClientName(clientId);
        crumbs.add(new BreadcrumbItem(clientName, currentPath));

        // Обрабатываем дальнейшие сегменты для клиента
        if (index + 1 < segments.length) {
            String nextSegment = segments[index + 1];
            String clientBasePath = "/clients/" + clientId;

            switch (nextSegment) {
                case UrlConstants.SEGMENT_EDIT -> crumbs.add(new BreadcrumbItem("Редактирование", currentPath + "/edit"));
                case UrlConstants.SEGMENT_IMPORT -> handleImportSegment(crumbs, segments, index + 1, clientBasePath);
                case UrlConstants.SEGMENT_EXPORT -> handleExportSegment(crumbs, segments, index + 1, clientBasePath);
                case UrlConstants.SEGMENT_TEMPLATES -> crumbs.add(new BreadcrumbItem("Шаблоны", clientBasePath + "/templates"));
                case UrlConstants.SEGMENT_STATISTICS -> crumbs.add(new BreadcrumbItem("Статистика", clientBasePath + "/statistics"));
                case UrlConstants.SEGMENT_OPERATIONS -> crumbs.add(new BreadcrumbItem("Операции", clientBasePath + "/operations"));
            }
        }
    }

    private void handleImportSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, String clientBasePath) {
        crumbs.add(new BreadcrumbItem("Импорт", clientBasePath + "/import"));

        if (index + 1 < segments.length && UrlConstants.SEGMENT_TEMPLATES.equals(segments[index + 1])) {
            crumbs.add(new BreadcrumbItem("Шаблоны импорта", clientBasePath + "/import/templates"));

            // Обрабатываем дальнейшие сегменты шаблонов
            if (index + 2 < segments.length) {
                String action = segments[index + 2];
                if (UrlConstants.SEGMENT_CREATE.equals(action)) {
                    crumbs.add(new BreadcrumbItem("Создание", clientBasePath + "/import/templates/create"));
                } else if (isNumeric(action)) {
                    Long templateId = Long.parseLong(action);
                    String templateName = getImportTemplateName(templateId);
                    crumbs.add(new BreadcrumbItem(templateName, clientBasePath + "/import/templates/" + templateId));

                    if (index + 3 < segments.length && UrlConstants.SEGMENT_EDIT.equals(segments[index + 3])) {
                        crumbs.add(new BreadcrumbItem("Редактирование", clientBasePath + "/import/templates/" + templateId + "/edit"));
                    }
                }
            }
        }
    }

    private void handleExportSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, String clientBasePath) {
        crumbs.add(new BreadcrumbItem("Экспорт", clientBasePath + "/export"));

        if (index + 1 < segments.length && UrlConstants.SEGMENT_TEMPLATES.equals(segments[index + 1])) {
            crumbs.add(new BreadcrumbItem("Шаблоны экспорта", clientBasePath + "/export/templates"));

            // Обрабатываем дальнейшие сегменты шаблонов
            if (index + 2 < segments.length) {
                String action = segments[index + 2];
                if (UrlConstants.SEGMENT_CREATE.equals(action)) {
                    crumbs.add(new BreadcrumbItem("Создание", clientBasePath + "/export/templates/create"));
                } else if (isNumeric(action)) {
                    Long templateId = Long.parseLong(action);
                    String templateName = getExportTemplateName(templateId);
                    crumbs.add(new BreadcrumbItem(templateName, clientBasePath + "/export/templates/" + templateId));

                    if (index + 3 < segments.length && UrlConstants.SEGMENT_EDIT.equals(segments[index + 3])) {
                        crumbs.add(new BreadcrumbItem("Редактирование", clientBasePath + "/export/templates/" + templateId + "/edit"));
                    }
                }
            }
        }
    }

    private void handleTemplateId(List<BreadcrumbItem> crumbs, String[] segments, int index, Long templateId, String currentPath) {
        // Определяем тип шаблона по контексту пути
        boolean isImportTemplate = containsSegment(segments, UrlConstants.SEGMENT_IMPORT);

        String templateName = isImportTemplate
                ? getImportTemplateName(templateId)
                : getExportTemplateName(templateId);
        crumbs.add(new BreadcrumbItem(templateName, currentPath));
    }

    private void handleNamedSegment(List<BreadcrumbItem> crumbs, String[] segments, int index, String segment, String currentPath) {
        // Пропускаем сегменты, которые уже обработаны в контексте
        if (isProcessedInContext(segments, index, segment)) {
            return;
        }

        switch (segment) {
            case "utils" -> crumbs.add(new BreadcrumbItem("Утилиты", "/utils"));
            case "stats" -> crumbs.add(new BreadcrumbItem("Статистика", "/stats"));
            default -> {
                // Неизвестный сегмент - добавляем как есть с заглавной буквы
                String displayName = segment.substring(0, 1).toUpperCase() + segment.substring(1);
                crumbs.add(new BreadcrumbItem(displayName, currentPath));
            }
        }
    }

    private boolean isProcessedInContext(String[] segments, int index, String segment) {
        // Проверяем, был ли сегмент уже обработан в контексте предыдущих сегментов
        if (index > 0) {
            String prevSegment = segments[index - 1];
            return (isNumeric(prevSegment) &&
                    (UrlConstants.SEGMENT_IMPORT.equals(segment) ||
                            UrlConstants.SEGMENT_EXPORT.equals(segment) ||
                            UrlConstants.SEGMENT_TEMPLATES.equals(segment) ||
                            UrlConstants.SEGMENT_STATISTICS.equals(segment) ||
                            UrlConstants.SEGMENT_OPERATIONS.equals(segment) ||
                            UrlConstants.SEGMENT_EDIT.equals(segment) ||
                            UrlConstants.SEGMENT_CREATE.equals(segment)));
        }
        return false;
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

    private boolean containsSegment(String[] segments, String segment) {
        for (String s : segments) {
            if (segment.equals(s)) {
                return true;
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String label;
        private String url;
    }
}