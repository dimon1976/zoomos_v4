package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для результата объединения данных
 * Каждая строка содержит связку: оригинал → аналог → коэффициент → ссылка
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedProductDto {

    /**
     * ID товара-оригинала
     */
    private String id;

    /**
     * Модель товара-оригинала
     */
    private String originalModel;

    /**
     * Модель аналога
     */
    private String analogModel;

    /**
     * Коэффициент аналога
     */
    private Double coefficient;

    /**
     * Ссылка на карточку товара
     */
    private String link;
}