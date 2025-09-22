package com.java.dto.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для строки файла ссылок с аналогами и их ссылками
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLinksDto {

    /**
     * Модель аналога (ключ для сопоставления)
     */
    private String analogModel;

    /**
     * Ссылка на карточку товара
     */
    private String link;
}