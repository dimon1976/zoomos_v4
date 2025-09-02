package com.java.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер для страницы справки по нормализации
 */
@Controller
@RequestMapping("/normalization")
public class NormalizationHelpController {
    
    /**
     * Отображает страницу справки по нормализации
     */
    @GetMapping("/help")
    public String showNormalizationHelp() {
        return "normalization/help";
    }
}