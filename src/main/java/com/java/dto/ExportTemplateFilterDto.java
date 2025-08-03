package com.java.dto;

import com.java.model.enums.FilterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Тип фильтра обязателен")
    private FilterType filterType; // EQUALS, CONTAINS, BETWEEN, IN

    @NotBlank(message = "Значение фильтра обязательно")
    private String filterValue;

    @Builder.Default
    private Boolean isActive = true;
}