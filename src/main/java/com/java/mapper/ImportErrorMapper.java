package com.java.mapper;

import com.java.dto.ImportErrorDto;
import com.java.model.entity.ImportError;
import com.java.model.enums.ErrorType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Маппер для преобразования ImportError между Entity и DTO
 */
public class ImportErrorMapper {

    private ImportErrorMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> DTO
     */
    public static ImportErrorDto toDto(ImportError entity) {
        if (entity == null) return null;

        return ImportErrorDto.builder()
                .id(entity.getId())
                .rowNumber(entity.getRowNumber())
                .columnName(entity.getColumnName())
                .fieldValue(entity.getFieldValue())
                .errorType(entity.getErrorType())
                .errorMessage(entity.getErrorMessage())
                .occurredAt(entity.getOccurredAt())
                .build();
    }

    /**
     * Список Entity -> список DTO
     */
    public static List<ImportErrorDto> toDtoList(List<ImportError> entities) {
        return entities.stream()
                .map(ImportErrorMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Создает Entity из параметров
     */
    public static ImportError createError(Long rowNumber, String columnName,
                                          String fieldValue, ErrorType errorType,
                                          String errorMessage) {
        return ImportError.builder()
                .rowNumber(rowNumber)
                .columnName(columnName)
                .fieldValue(fieldValue)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();
    }
}