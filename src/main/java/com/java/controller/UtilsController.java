package com.java.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер для страницы утилит
 */
@Controller
@RequestMapping("/utils")
@RequiredArgsConstructor
@Slf4j
public class UtilsController {

    @GetMapping
    public String index(Model model) {
        log.debug("GET request to utils page");
        model.addAttribute("pageTitle", "Утилиты");
        return "utilities/index";
    }
}