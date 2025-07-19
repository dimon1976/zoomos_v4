package com.java.dto;

import com.java.model.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTemplateFieldDto {

    private Long id;

    private String columnName;

    private Integer columnIndex;

    @NotBlank(message = "Имя поля сущности обязательно")
    private String entityFieldName;

    private FieldType fieldType = FieldType.STRING;

    private Boolean isRequired = false;

    private Boolean isUnique = false;

    private String defaultValue;

    private String dateFormat;

    private String transformationRule;

    private String validationRegex;

    private String validationMessage;
}
