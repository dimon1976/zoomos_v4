package com.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Централизованное формирование хлебных крошек для всех страниц
 */
@ControllerAdvice
public class BreadcrumbAdvice {
    private static final Map<String, String> SEGMENT_NAMES = new HashMap<>();
    static {
        SEGMENT_NAMES.put("clients", "Клиенты");
        SEGMENT_NAMES.put("import", "Импорт");
        SEGMENT_NAMES.put("export", "Экспорт");
        SEGMENT_NAMES.put("templates", "Шаблоны");
        SEGMENT_NAMES.put("statistics", "Статистика");
        SEGMENT_NAMES.put("settings", "Настройки");
    }

    @ModelAttribute("breadcrumbs")
    public List<BreadcrumbItem> breadcrumbs(HttpServletRequest request) {
        List<BreadcrumbItem> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItem("Главная", "/"));

        String[] parts = request.getRequestURI().split("/");
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            path.append('/').append(part);
            crumbs.add(new BreadcrumbItem(SEGMENT_NAMES.getOrDefault(part, part), path.toString()));
        }
        return crumbs;
    }

    @Data
    @AllArgsConstructor
    public static class BreadcrumbItem {
        private String label;
        private String url;
    }
}
