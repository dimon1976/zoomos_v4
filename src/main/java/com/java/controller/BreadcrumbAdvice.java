package com.java.controller;

import com.java.service.client.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    }

    private final ClientService clientService;
    
    // Паттерн для идентификации клиентских страниц: /clients/{id}/...
    private static final Pattern CLIENT_PATH_PATTERN = Pattern.compile("^/clients/(\\d+)(?:/(.+))?$");

    @ModelAttribute("breadcrumbs")
    public List<BreadcrumbItem> breadcrumbs(HttpServletRequest request) {
        List<BreadcrumbItem> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItem("Главная", "/"));

        String uri = request.getRequestURI();
        Matcher matcher = CLIENT_PATH_PATTERN.matcher(uri);
        
        if (matcher.matches()) {
            // Это страница клиента
            generateClientBreadcrumbs(crumbs, matcher);
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
            String clientName = clientService.getClientById(id)
                    .map(client -> client.getName())
                    .orElse("Клиент #" + clientId);
            crumbs.add(new BreadcrumbItem(clientName, "/clients/" + clientId));
            
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

    @Data
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String label;
        private String url;
    }
}
