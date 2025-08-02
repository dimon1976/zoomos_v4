package com.java.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTemplateFilterDto {

    private Long id;

    @NotBlank(message = "Имя поля обязательно")
    private String fieldName;

    @NotBlank(message = "Тип фильтра обязателен")
    private String filterType; // EQUALS, CONTAINS, BETWEEN, IN

    @NotBlank(message = "Значение фильтра обязательно")
    private String filterValue;

    @Builder.Default
    private Boolean isActive = true;
}