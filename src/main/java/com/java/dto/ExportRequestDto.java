package com.java.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequestDto {

    @NotNull(message = "ID шаблона обязателен")
    private Long templateId;

    @NotEmpty(message = "Необходимо выбрать хотя бы одну операцию")
    private List<Long> operationIds;

    // Фильтры по датам
    private ZonedDateTime dateFrom;
    private ZonedDateTime dateTo;

    // Дополнительные фильтры (переопределяют шаблонные)
    @Builder.Default
    private List<ExportTemplateFilterDto> additionalFilters = new ArrayList<>();

    // Параметры файла (переопределяют шаблонные)
    private String fileFormat;
    private String csvDelimiter;
    private String csvEncoding;

    @Builder.Default
    private Boolean asyncMode = true;
}