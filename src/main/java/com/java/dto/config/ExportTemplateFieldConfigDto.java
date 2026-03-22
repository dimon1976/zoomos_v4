package com.java.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportTemplateFieldConfigDto {

    private String entityFieldName;
    private String exportColumnName;
    private Integer fieldOrder;
    @Builder.Default
    private Boolean isIncluded = true;
    private String dataFormat;
    private String transformationRule;
    private String normalizationType;
    private String normalizationRule;
}
