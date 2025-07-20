package com.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер для страницы статистики
 */
@Controller
@RequestMapping("/stats")
@RequiredArgsConstructor
@Slf4j
public class StatsController {

    @GetMapping
    public String index(Model model) {
        log.debug("GET request to stats page");
        model.addAttribute("pageTitle", "Статистика");
        return "statistics/index";
    }
}
