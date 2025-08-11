package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Короткая информация о шаблоне.
 */
@Data
@AllArgsConstructor
public class TemplateSummaryDto {
    private Long id;
    private String name;
    private String entityType;
}
