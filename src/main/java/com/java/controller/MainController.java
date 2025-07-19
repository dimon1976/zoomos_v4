package com.java.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Контроллер для главной страницы приложения
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {

    /**
     * Отображение главной страницы приложения
     */
    @GetMapping("/")
    public String displayHomePage(Model model, HttpServletRequest request) {
        log.debug("Запрос на отображение главной страницы");
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("pageTitle", "Главная страница");
        return "index";
    }
}