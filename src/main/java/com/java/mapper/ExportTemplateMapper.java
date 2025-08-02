package com.java.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.ExportTemplateDto;
import com.java.dto.ExportTemplateFieldDto;
import com.java.dto.ExportTemplateFilterDto;
import com.java.model.Client;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.ExportTemplateFilter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ExportTemplateMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ExportTemplateMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> DTO
     */
    public static ExportTemplateDto toDto(ExportTemplate entity) {
        if (entity == null) return null;

        return ExportTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .clientId(entity.getClient() != null ? entity.getClient().getId() : null)
                .clientName(entity.getClient() != null ? entity.getClient().getName() : null)
                .entityType(entity.getEntityType())
                .exportStrategy(entity.getExportStrategy())
                .fileFormat(entity.getFileFormat())
                .csvDelimiter(entity.getCsvDelimiter())
                .csvEncoding(entity.getCsvEncoding())
                .csvQuoteChar(entity.getCsvQuoteChar())
                .csvIncludeHeader(entity.getCsvIncludeHeader())
                .xlsxSheetName(entity.getXlsxSheetName())
                .xlsxAutoSizeColumns(entity.getXlsxAutoSizeColumns())
                .maxRowsPerFile(entity.getMaxRowsPerFile())
                .isActive(entity.getIsActive())
                .fields(entity.getFields() != null ?
                        entity.getFields().stream()
                                .map(ExportTemplateMapper::fieldToDto)
                                .collect(Collectors.toList()) : new ArrayList<>())
                .filters(entity.getFilters() != null ?
                        entity.getFilters().stream()
                                .map(ExportTemplateMapper::filterToDto)
                                .collect(Collectors.toList()) : new ArrayList<>())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * DTO -> Entity (для создания)
     */
    public static ExportTemplate toEntity(ExportTemplateDto dto, Client client) {
        if (dto == null) return null;

        ExportTemplate entity = ExportTemplate.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .client(client)
                .entityType(dto.getEntityType())
                .exportStrategy(dto.getExportStrategy())
                .fileFormat(dto.getFileFormat())
                .csvDelimiter(dto.getCsvDelimiter())
                .csvEncoding(dto.getCsvEncoding())
                .csvQuoteChar(dto.getCsvQuoteChar())
                .csvIncludeHeader(dto.getCsvIncludeHeader())
                .xlsxSheetName(dto.getXlsxSheetName())
                .xlsxAutoSizeColumns(dto.getXlsxAutoSizeColumns())
                .maxRowsPerFile(dto.getMaxRowsPerFile())
                .isActive(dto.getIsActive())
                .build();

        // Добавляем поля
        if (dto.getFields() != null) {
            for (ExportTemplateFieldDto fieldDto : dto.getFields()) {
                ExportTemplateField field = fieldToEntity(fieldDto);
                entity.addField(field);
            }
        }

        // Добавляем фильтры
        if (dto.getFilters() != null) {
            for (ExportTemplateFilterDto filterDto : dto.getFilters()) {
                ExportTemplateFilter filter = filterToEntity(filterDto);
                entity.addFilter(filter);
            }
        }

        return entity;
    }

    /**
     * Обновление Entity из DTO
     */
    public static void updateEntity(ExportTemplate entity, ExportTemplateDto dto) {
        if (entity == null || dto == null) return;

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setEntityType(dto.getEntityType());
        entity.setExportStrategy(dto.getExportStrategy());
        entity.setFileFormat(dto.getFileFormat());
        entity.setCsvDelimiter(dto.getCsvDelimiter());
        entity.setCsvEncoding(dto.getCsvEncoding());
        entity.setCsvQuoteChar(dto.getCsvQuoteChar());
        entity.setCsvIncludeHeader(dto.getCsvIncludeHeader());
        entity.setXlsxSheetName(dto.getXlsxSheetName());
        entity.setXlsxAutoSizeColumns(dto.getXlsxAutoSizeColumns());
        entity.setMaxRowsPerFile(dto.getMaxRowsPerFile());
        entity.setIsActive(dto.getIsActive());

        // Обновляем поля
        entity.getFields().clear();
        if (dto.getFields() != null) {
            for (ExportTemplateFieldDto fieldDto : dto.getFields()) {
                ExportTemplateField field = fieldToEntity(fieldDto);
                entity.addField(field);
            }
        }

        // Обновляем фильтры
        entity.getFilters().clear();
        if (dto.getFilters() != null) {
            for (ExportTemplateFilterDto filterDto : dto.getFilters()) {
                ExportTemplateFilter filter = filterToEntity(filterDto);
                entity.addFilter(filter);
            }
        }
    }

    /**
     * Field Entity -> DTO
     */
    public static ExportTemplateFieldDto fieldToDto(ExportTemplateField entity) {
        if (entity == null) return null;

        return ExportTemplateFieldDto.builder()
                .id(entity.getId())
                .entityFieldName(entity.getEntityFieldName())
                .exportColumnName(entity.getExportColumnName())
                .fieldOrder(entity.getFieldOrder())
                .isIncluded(entity.getIsIncluded())
                .dataFormat(entity.getDataFormat())
                .transformationRule(entity.getTransformationRule())
                .build();
    }

    /**
     * Field DTO -> Entity
     */
    public static ExportTemplateField fieldToEntity(ExportTemplateFieldDto dto) {
        if (dto == null) return null;

        return ExportTemplateField.builder()
                .entityFieldName(dto.getEntityFieldName())
                .exportColumnName(dto.getExportColumnName())
                .fieldOrder(dto.getFieldOrder())
                .isIncluded(dto.getIsIncluded())
                .dataFormat(dto.getDataFormat())
                .transformationRule(dto.getTransformationRule())
                .build();
    }

    /**
     * Filter Entity -> DTO
     */
    public static ExportTemplateFilterDto filterToDto(ExportTemplateFilter entity) {
        if (entity == null) return null;

        return ExportTemplateFilterDto.builder()
                .id(entity.getId())
                .fieldName(entity.getFieldName())
                .filterType(entity.getFilterType())
                .filterValue(entity.getFilterValue())
                .isActive(entity.getIsActive())
                .build();
    }

    /**
     * Filter DTO -> Entity
     */
    public static ExportTemplateFilter filterToEntity(ExportTemplateFilterDto dto) {
        if (dto == null) return null;

        return ExportTemplateFilter.builder()
                .fieldName(dto.getFieldName())
                .filterType(dto.getFilterType())
                .filterValue(dto.getFilterValue())
                .isActive(dto.getIsActive())
                .build();
    }

    /**
     * Список Entity -> список DTO
     */
    public static List<ExportTemplateDto> toDtoList(List<ExportTemplate> entities) {
        return entities.stream()
                .map(ExportTemplateMapper::toDto)
                .collect(Collectors.toList());
    }
}