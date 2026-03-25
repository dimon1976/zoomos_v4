package com.java.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Добавляет атрибут _layout в Model для всех MVC контроллеров.
 * Позволяет переключать UI-тему между Tabler и Bootstrap через cookie.
 *
 * Переключение: добавить ?theme=tabler или ?theme=bootstrap к любому URL.
 * Cookie "ui-theme" сохраняется на 1 год.
 */
@ControllerAdvice(annotations = Controller.class)
public class ThemeControllerAdvice {

    private static final String COOKIE_NAME = "ui-theme";
    private static final String TABLER_LAYOUT = "layout/tabler-main";
    private static final String BOOTSTRAP_LAYOUT = "layout/main";
    private static final int COOKIE_MAX_AGE = 365 * 24 * 3600;

    @ModelAttribute("_layout")
    public String resolveLayout(HttpServletRequest request, HttpServletResponse response) {
        String theme = getThemeParam(request);

        if (theme != null) {
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, theme)
                    .path("/")
                    .maxAge(COOKIE_MAX_AGE)
                    .httpOnly(true)
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return "tabler".equals(theme) ? TABLER_LAYOUT : BOOTSTRAP_LAYOUT;
        }

        return readCookie(request);
    }

    private String getThemeParam(HttpServletRequest request) {
        String param = request.getParameter("theme");
        if ("tabler".equals(param) || "bootstrap".equals(param)) {
            return param;
        }
        return null;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    return "bootstrap".equals(c.getValue()) ? BOOTSTRAP_LAYOUT : TABLER_LAYOUT;
                }
            }
        }
        return TABLER_LAYOUT; // Tabler — новый дефолт
    }
}
