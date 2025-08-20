package com.java.controller;

import com.java.constants.UrlConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    @GetMapping(UrlConstants.SETTINGS)
    public String settings(Model model) {
        log.debug("GET request to settings page");
        model.addAttribute("pageTitle", "Настройки");
        return "settings/index";
    }
}