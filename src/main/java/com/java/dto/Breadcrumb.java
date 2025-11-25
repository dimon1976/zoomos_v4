package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для хлебных крошек навигации
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Breadcrumb {
    /**
     * Отображаемый текст крошки
     */
    private String label;

    /**
     * URL для перехода
     * null для текущей (активной) страницы
     */
    private String url;
}
