package com.java.dto;

import com.java.model.enums.NormalizationType;
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
public class ExportTemplateFieldDto {

    private Long id;

    @NotBlank(message = "Имя поля сущности обязательно")
    private String entityFieldName;

    @NotBlank(message = "Название колонки для экспорта обязательно")
    private String exportColumnName;

    @NotNull(message = "Порядок поля обязателен")
    private Integer fieldOrder;

    @Builder.Default
    private Boolean isIncluded = true;

    private String dataFormat; // Формат для дат/чисел

    private String transformationRule; // JSON правила трансформации
    
    // Поля нормализации
    private NormalizationType normalizationType;
    
    private String normalizationRule; // JSON с правилами нормализации
}