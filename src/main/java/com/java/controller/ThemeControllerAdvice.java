package com.java.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Устанавливает layout/tabler-main как единственный UI-лэйаут.
 */
@ControllerAdvice(annotations = Controller.class)
public class ThemeControllerAdvice {

    @ModelAttribute("_layout")
    public String resolveLayout() {
        return "layout/tabler-main";
    }
}
