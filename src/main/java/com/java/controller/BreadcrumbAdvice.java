// src/main/java/com/java/controller/BreadcrumbAdvice.java
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
 * Поддерживает контекстную информацию (имена клиентов, шаблонов и т.д.)
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
            processBreadcrumbSegments(crumbs, segments, path);
        } catch (Exception e) {
            log.warn("Ошибка при формировании хлебных крошек для пути {}: {}", path, e.getMessage());
            // В случае ошибки возвращаем базовые крошки
        }

        return crumbs;
    }

    private void processBreadcrumbSegments(List<BreadcrumbItem> crumbs, String[] segments, String fullPath) {
        StringBuilder currentPath = new StringBuilder();

        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            currentPath.append("/").append(segment);

            switch (segment) {
                case UrlConstants.SEGMENT_CLIENTS -> {
                    crumbs.add(new BreadcrumbItem("Клиенты", UrlConstants.CLIENTS));
                }
                case UrlConstants.SEGMENT_SETTINGS -> {
                    crumbs.add(new BreadcrumbItem("Настройки", UrlConstants.SETTINGS));
                }
                case UrlConstants.SEGMENT_STATISTICS -> {
                    if (isPreviousSegment(segments, i, UrlConstants.SEGMENT_SETTINGS)) {
                        crumbs.add(new BreadcrumbItem("Статистика", UrlConstants.SETTINGS_STATISTICS));
                    } else {
                        crumbs.add(new BreadcrumbItem("Статистика", currentPath.toString()));
                    }
                }
                case UrlConstants.SEGMENT_CREATE -> {
                    crumbs.add(new BreadcrumbItem("Создание", currentPath.toString()));
                }
                case UrlConstants.SEGMENT_EDIT -> {
                    crumbs.add(new BreadcrumbItem("Редактирование", currentPath.toString()));
                }
                default -> {
                    // Проверяем, является ли сегмент ID
                    if (isNumeric(segment)) {
                        handleNumericSegment(crumbs, segments, i, segment, currentPath.toString());
                    } else {
                        handleNamedSegment(crumbs, segments, i, segment, currentPath.toString());
                    }
                }
            }
        }
    }

    private void handleNumericSegment(List<BreadcrumbItem> crumbs, String[] segments, int index,
                                      String segment, String currentPath) {
        Long id = Long.parseLong(segment);

        if (index > 1) {
            String prevSegment = segments[index - 1];

            switch (prevSegment) {
                case UrlConstants.SEGMENT_CLIENTS -> {
                    // ID клиента
                    String clientName = getClientName(id);
                    crumbs.add(new BreadcrumbItem(clientName, currentPath));
                }
                case UrlConstants.SEGMENT_TEMPLATES -> {
                    // ID шаблона - определяем тип по контексту
                    String templateName = getTemplateName(id, segments);
                    crumbs.add(new BreadcrumbItem(templateName, currentPath));
                }
                case UrlConstants.SEGMENT_OPERATIONS -> {
                    // ID операции
                    crumbs.add(new BreadcrumbItem("Операция #" + id, currentPath));
                }
            }
        }
    }

    private void handleNamedSegment(List<BreadcrumbItem> crumbs, String[] segments, int index,
                                    String segment, String currentPath) {
        switch (segment) {
            case UrlConstants.SEGMENT_IMPORT -> {
                crumbs.add(new BreadcrumbItem("Импорт", currentPath));
            }
            case UrlConstants.SEGMENT_EXPORT -> {
                crumbs.add(new BreadcrumbItem("Экспорт", currentPath));
            }
            case UrlConstants.SEGMENT_TEMPLATES -> {
                crumbs.add(new BreadcrumbItem("Шаблоны", currentPath));
            }
            case UrlConstants.SEGMENT_OPERATIONS -> {
                crumbs.add(new BreadcrumbItem("Операции", currentPath));
            }
            case "utils" -> {
                crumbs.add(new BreadcrumbItem("Утилиты", UrlConstants.UTILS));
            }
            case "stats" -> {
                crumbs.add(new BreadcrumbItem("Статистика", UrlConstants.STATS));
            }
            default -> {
                // Неизвестный сегмент - добавляем как есть с заглавной буквы
                String displayName = segment.substring(0, 1).toUpperCase() + segment.substring(1);
                crumbs.add(new BreadcrumbItem(displayName, currentPath));
            }
        }
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

    private String getTemplateName(Long templateId, String[] segments) {
        try {
            // Определяем тип шаблона по контексту пути
            boolean isImportTemplate = containsSegment(segments, UrlConstants.SEGMENT_IMPORT);

            if (isImportTemplate) {
                return importTemplateService.getTemplate(templateId)
                        .map(template -> template.getName())
                        .orElse("Шаблон импорта #" + templateId);
            } else {
                return exportTemplateService.getTemplate(templateId)
                        .map(template -> template.getName())
                        .orElse("Шаблон экспорта #" + templateId);
            }
        } catch (Exception e) {
            log.warn("Не удалось получить имя шаблона с ID {}: {}", templateId, e.getMessage());
            return "Шаблон #" + templateId;
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

    private boolean isPreviousSegment(String[] segments, int currentIndex, String expectedSegment) {
        return currentIndex > 0 && expectedSegment.equals(segments[currentIndex - 1]);
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