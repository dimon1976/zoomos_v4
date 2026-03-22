package com.java.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportTemplateConfigDto {

    private String name;
    private String description;
    private String entityType;
    private String dataSourceType;
    private String duplicateStrategy;
    private String errorStrategy;
    private String fileType;
    private String delimiter;
    private String encoding;
    private Integer skipHeaderRows;
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private List<ImportTemplateFieldConfigDto> fields = new ArrayList<>();
}
