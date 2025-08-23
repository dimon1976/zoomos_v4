package com.java.controller;

import com.java.service.client.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Централизованное формирование хлебных крошек для всех страниц
 */
@ControllerAdvice
@RequiredArgsConstructor
public class BreadcrumbAdvice {
    private static final Map<String, String> SEGMENT_NAMES = new HashMap<>();
    static {
        SEGMENT_NAMES.put("clients", "Клиенты");
        SEGMENT_NAMES.put("import", "Импорт");
        SEGMENT_NAMES.put("export", "Экспорт");
        SEGMENT_NAMES.put("templates", "Шаблоны");
        SEGMENT_NAMES.put("statistics", "Статистика");
        SEGMENT_NAMES.put("operations", "История операций");
        SEGMENT_NAMES.put("settings", "Настройки");
        SEGMENT_NAMES.put("create", "Создание");
        SEGMENT_NAMES.put("edit", "Редактирование");
        SEGMENT_NAMES.put("view", "Просмотр");
        SEGMENT_NAMES.put("analyze", "Анализ");
        SEGMENT_NAMES.put("start", "Запуск");
        SEGMENT_NAMES.put("status", "Статус");
        SEGMENT_NAMES.put("setup", "Настройка");
        SEGMENT_NAMES.put("results", "Результаты");
    }

    private final ClientService clientService;
    
    // Паттерны для идентификации различных типов страниц
    private static final Pattern CLIENT_PATH_PATTERN = Pattern.compile("^/clients/(\\d+)(?:/(.+))?$");
    private static final Pattern TEMPLATE_PATH_PATTERN = Pattern.compile("^/clients/(\\d+)/(import|export)/templates(?:/(.+))?$");

    @ModelAttribute("breadcrumbs")
    public List<BreadcrumbItem> breadcrumbs(HttpServletRequest request) {
        List<BreadcrumbItem> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItem("Главная", "/"));

        String uri = request.getRequestURI();
        
        // Проверяем специальные паттерны template paths
        Matcher templateMatcher = TEMPLATE_PATH_PATTERN.matcher(uri);
        if (templateMatcher.matches()) {
            generateTemplateBreadcrumbs(crumbs, templateMatcher);
            return crumbs;
        }
        
        // Проверяем обычные client paths
        Matcher clientMatcher = CLIENT_PATH_PATTERN.matcher(uri);
        if (clientMatcher.matches()) {
            generateClientBreadcrumbs(crumbs, clientMatcher);
        } else {
            // Стандартная генерация хлебных крошек
            generateStandardBreadcrumbs(crumbs, uri);
        }
        
        return crumbs;
    }

    private void generateClientBreadcrumbs(List<BreadcrumbItem> crumbs, Matcher matcher) {
        String clientId = matcher.group(1);
        String subPath = matcher.group(2);
        
        // Добавляем "Клиенты"
        crumbs.add(new BreadcrumbItem("Клиенты", "/clients"));
        
        // Получаем имя клиента и добавляем его в крошки
        try {
            Long id = Long.parseLong(clientId);
            Optional<String> clientNameOpt = clientService.getClientById(id)
                    .map(client -> client.getName());
            
            if (clientNameOpt.isPresent()) {
                crumbs.add(new BreadcrumbItem(clientNameOpt.get(), "/clients/" + clientId));
            } else {
                // Клиент не найден - используем стандартную генерацию без проверки клиента
                generateStandardBreadcrumbs(crumbs, "/clients/" + clientId + (subPath != null ? "/" + subPath : ""));
                return;
            }
            
            // Если есть подпуть, добавляем его
            if (subPath != null && !subPath.isEmpty()) {
                String[] subParts = subPath.split("/");
                StringBuilder subPathBuilder = new StringBuilder("/clients/" + clientId);
                
                for (String part : subParts) {
                    if (!part.isEmpty()) {
                        subPathBuilder.append("/").append(part);
                        String label = SEGMENT_NAMES.getOrDefault(part, part);
                        crumbs.add(new BreadcrumbItem(label, subPathBuilder.toString()));
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Если не удается распарсить ID, используем стандартную генерацию
            generateStandardBreadcrumbs(crumbs, "/clients/" + clientId + (subPath != null ? "/" + subPath : ""));
        }
    }

    private void generateStandardBreadcrumbs(List<BreadcrumbItem> crumbs, String uri) {
        String[] parts = uri.split("/");
        StringBuilder path = new StringBuilder();
        
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            path.append('/').append(part);
            String label = SEGMENT_NAMES.getOrDefault(part, part);
            crumbs.add(new BreadcrumbItem(label, path.toString()));
        }
    }

    private void generateTemplateBreadcrumbs(List<BreadcrumbItem> crumbs, Matcher matcher) {
        String clientId = matcher.group(1);
        String type = matcher.group(2); // import или export
        String templatePath = matcher.group(3); // все после /templates/
        
        try {
            Long id = Long.parseLong(clientId);
            Optional<String> clientNameOpt = clientService.getClientById(id)
                    .map(client -> client.getName());
            
            if (!clientNameOpt.isPresent()) {
                // Клиент не найден - используем стандартную генерацию
                generateStandardBreadcrumbs(crumbs, "/clients/" + clientId + "/" + type + "/templates" + 
                        (templatePath != null ? "/" + templatePath : ""));
                return;
            }
            
            // Добавляем базовые крошки
            crumbs.add(new BreadcrumbItem("Клиенты", "/clients"));
            crumbs.add(new BreadcrumbItem(clientNameOpt.get(), "/clients/" + clientId));
            
            // Добавляем тип (Импорт/Экспорт)
            String typeLabel = SEGMENT_NAMES.getOrDefault(type, type);
            crumbs.add(new BreadcrumbItem(typeLabel, "/clients/" + clientId + "/" + type));
            
            // Добавляем "Шаблоны"
            crumbs.add(new BreadcrumbItem("Шаблоны", "/clients/" + clientId + "/" + type + "/templates"));
            
            // Если есть дополнительный путь (templateId, edit, create, etc.)
            if (templatePath != null && !templatePath.isEmpty()) {
                String[] pathParts = templatePath.split("/");
                StringBuilder pathBuilder = new StringBuilder("/clients/" + clientId + "/" + type + "/templates");
                
                for (String part : pathParts) {
                    if (!part.isEmpty()) {
                        pathBuilder.append("/").append(part);
                        
                        // Если это ID шаблона (число), получаем имя шаблона
                        if (part.matches("\\d+")) {
                            try {
                                Long templateId = Long.parseLong(part);
                                String templateName = getTemplateName(templateId, type);
                                crumbs.add(new BreadcrumbItem(templateName, pathBuilder.toString()));
                            } catch (NumberFormatException e) {
                                crumbs.add(new BreadcrumbItem("Шаблон #" + part, pathBuilder.toString()));
                            }
                        } else {
                            // Это действие (create, edit, etc.)
                            String label = SEGMENT_NAMES.getOrDefault(part, part);
                            crumbs.add(new BreadcrumbItem(label, pathBuilder.toString()));
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {
            // Если не удается распарсить ID, используем стандартную генерацию
            generateStandardBreadcrumbs(crumbs, "/clients/" + clientId + "/" + type + "/templates" + 
                    (templatePath != null ? "/" + templatePath : ""));
        }
    }
    
    private String getTemplateName(Long templateId, String type) {
        // TODO: Можно добавить сервис для получения имен шаблонов
        // Пока возвращаем базовое имя
        return "Шаблон #" + templateId;
    }

    @Data
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String label;
        private String url;
    }
}
