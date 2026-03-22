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
public class ImportTemplateFieldConfigDto {

    private String columnName;
    private Integer columnIndex;
    private String entityFieldName;
    private String fieldType;
    @Builder.Default
    private Boolean isRequired = false;
    @Builder.Default
    private Boolean isUnique = false;
    private String defaultValue;
    private String dateFormat;
    private String transformationRule;
    private String validationRegex;
    private String validationMessage;
}
