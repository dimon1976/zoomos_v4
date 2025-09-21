package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для строки исходного файла с товарами-оригиналами и их аналогами
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceProductDto {

    /**
     * Уникальный ID товара-оригинала
     */
    private String id;

    /**
     * Модель товара-оригинала
     */
    private String originalModel;

    /**
     * Модель аналога (ключ для поиска ссылок)
     */
    private String analogModel;

    /**
     * Коэффициент для данного аналога
     */
    private Double coefficient;
}