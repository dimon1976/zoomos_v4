package com.java.dto;

import com.java.model.enums.ErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorDto {

    private Long id;
    private Long rowNumber;
    private String columnName;
    private String fieldValue;
    private ErrorType errorType;
    private String errorMessage;
    private ZonedDateTime occurredAt;

    // Для группировки ошибок
    private Long count;
    private Map<String, Object> context;
}