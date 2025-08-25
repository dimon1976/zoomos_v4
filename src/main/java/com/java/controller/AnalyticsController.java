package com.java.controller;

import com.java.service.client.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final ClientService clientService;

    /**
     * Страница расширенной аналитики
     */
    @GetMapping
    public String analyticsPage(Model model) {
        log.debug("Отображение страницы расширенной аналитики");
        
        // Добавляем список клиентов для фильтрации
        model.addAttribute("clients", clientService.getAllClients());
        
        return "analytics/index";
    }
}