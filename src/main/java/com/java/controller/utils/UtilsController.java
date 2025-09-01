package com.java.controller.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Главный контроллер для системы утилит
 */
@Controller
@RequestMapping("/utils")
@RequiredArgsConstructor
@Slf4j
public class UtilsController {

    /**
     * Главная страница утилит со списком доступных инструментов
     */
    @GetMapping
    public String index(Model model) {
        log.debug("GET request to utils main page");
        
        model.addAttribute("pageTitle", "Утилиты");
        model.addAttribute("utilities", getAvailableUtilities());
        
        return "utils/utils-main";
    }

    /**
     * Получить список доступных утилит
     */
    private List<Map<String, Object>> getAvailableUtilities() {
        List<Map<String, Object>> utilities = new ArrayList<>();
        
        utilities.add(Map.of(
            "id", "barcode-match",
            "title", "Сопоставление штрихкодов",
            "description", "Сопоставление данных по штрихкодам в одном файле",
            "icon", "fas fa-barcode",
            "url", "/utils/barcode-match",
            "status", "ready" // development, ready, disabled
        ));
        
        utilities.add(Map.of(
            "id", "url-cleaner", 
            "title", "Очистка URL",
            "description", "Удаление UTM-меток и реферальных параметров из ссылок",
            "icon", "fas fa-link",
            "url", "/utils/url-cleaner",
            "status", "ready"
        ));
        
        utilities.add(Map.of(
            "id", "link-extractor",
            "title", "Сбор ссылок с ID",
            "description", "Поиск и сбор всех ссылок из файла с привязкой к ID",
            "icon", "fas fa-link",
            "url", "/utils/link-extractor", 
            "status", "ready"
        ));
        
        utilities.add(Map.of(
            "id", "redirect-collector",
            "title", "Сбор финальных ссылок",
            "description", "Получение финальных URL после всех редиректов",
            "icon", "fas fa-external-link-alt",
            "url", "/utils/redirect-collector",
            "status", "ready"
        ));
        
        return utilities;
    }
}