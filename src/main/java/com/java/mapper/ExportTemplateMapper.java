// src/main/java/com/java/mapper/ExportTemplateMapper.java
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

    private static ObjectMapper objectMapper;

    public static void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }

    private static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper;
    }

    public static ExportTemplateDto toDto(ExportTemplate entity) {
        if (entity == null) return null;

        return ExportTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .clientId(entity.getClient().getId())
                .clientName(entity.getClient().getName())
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
                .fields(entity.getFields().stream()
                        .map(ExportTemplateMapper::toFieldDto)
                        .collect(Collectors.toList()))
                .filters(entity.getFilters().stream()
                        .map(ExportTemplateMapper::toFilterDto)
                        .collect(Collectors.toList()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                // Настройки статистики
                .enableStatistics(entity.getEnableStatistics())
                .statisticsCountFields(parseJsonStringList(entity.getStatisticsCountFields()))
                .statisticsGroupField(entity.getStatisticsGroupField())
                .statisticsFilterFields(parseJsonStringList(entity.getStatisticsFilterFields()))
                // Настройки именования файлов
                .filenameTemplate(entity.getFilenameTemplate())
                .includeClientName(entity.getIncludeClientName())
                .includeExportType(entity.getIncludeExportType())
                .includeTaskNumber(entity.getIncludeTaskNumber())
                .exportTypeLabel(entity.getExportTypeLabel())
                .operationNameSource(entity.getOperationNameSource())
                .build();
    }

    public static ExportTemplate toEntity(ExportTemplateDto dto, Client client) {
        if (dto == null) return null;

        ExportTemplate entity = ExportTemplate.builder()
                .id(dto.getId())
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
                // Настройки статистики
                .enableStatistics(dto.getEnableStatistics())
                .statisticsCountFields(toJsonString(dto.getStatisticsCountFields()))
                .statisticsGroupField(dto.getStatisticsGroupField())
                .statisticsFilterFields(toJsonString(dto.getStatisticsFilterFields()))
                // Настройки именования файлов
                .filenameTemplate(dto.getFilenameTemplate())
                .includeClientName(dto.getIncludeClientName())
                .includeExportType(dto.getIncludeExportType())
                .includeTaskNumber(dto.getIncludeTaskNumber())
                .exportTypeLabel(dto.getExportTypeLabel())
                .operationNameSource(dto.getOperationNameSource())
                .build();

        // Добавляем поля
        if (dto.getFields() != null) {
            for (ExportTemplateFieldDto fieldDto : dto.getFields()) {
                ExportTemplateField field = toFieldEntity(fieldDto);
                entity.addField(field);
            }
        }

        // Добавляем фильтры
        if (dto.getFilters() != null) {
            for (ExportTemplateFilterDto filterDto : dto.getFilters()) {
                ExportTemplateFilter filter = toFilterEntity(filterDto);
                entity.addFilter(filter);
            }
        }

        return entity;
    }

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

        // Обновляем настройки статистики
        entity.setEnableStatistics(dto.getEnableStatistics());
        entity.setStatisticsCountFields(toJsonString(dto.getStatisticsCountFields()));
        entity.setStatisticsGroupField(dto.getStatisticsGroupField());
        entity.setStatisticsFilterFields(toJsonString(dto.getStatisticsFilterFields()));

        // Обновляем настройки именования файлов
        entity.setFilenameTemplate(dto.getFilenameTemplate());
        entity.setIncludeClientName(dto.getIncludeClientName());
        entity.setIncludeExportType(dto.getIncludeExportType());
        entity.setIncludeTaskNumber(dto.getIncludeTaskNumber());
        entity.setExportTypeLabel(dto.getExportTypeLabel());
        entity.setOperationNameSource(dto.getOperationNameSource());


        // Обновляем поля
        entity.getFields().clear();
        if (dto.getFields() != null) {
            for (ExportTemplateFieldDto fieldDto : dto.getFields()) {
                ExportTemplateField field = toFieldEntity(fieldDto);
                entity.addField(field);
            }
        }

        // Обновляем фильтры
        entity.getFilters().clear();
        if (dto.getFilters() != null) {
            for (ExportTemplateFilterDto filterDto : dto.getFilters()) {
                ExportTemplateFilter filter = toFilterEntity(filterDto);
                entity.addFilter(filter);
            }
        }
    }

    public static List<ExportTemplateDto> toDtoList(List<ExportTemplate> entities) {
        if (entities == null) return new ArrayList<>();
        return entities.stream()
                .map(ExportTemplateMapper::toDto)
                .collect(Collectors.toList());
    }

    // Вспомогательные методы для полей
    private static ExportTemplateFieldDto toFieldDto(ExportTemplateField entity) {
        return ExportTemplateFieldDto.builder()
                .id(entity.getId())
                .entityFieldName(entity.getEntityFieldName())
                .exportColumnName(entity.getExportColumnName())
                .fieldOrder(entity.getFieldOrder())
                .isIncluded(entity.getIsIncluded())
                .dataFormat(entity.getDataFormat())
                .transformationRule(entity.getTransformationRule())
                .normalizationType(entity.getNormalizationType())
                .normalizationRule(entity.getNormalizationRule())
                .build();
    }

    private static ExportTemplateField toFieldEntity(ExportTemplateFieldDto dto) {
        return ExportTemplateField.builder()
                .id(dto.getId())
                .entityFieldName(dto.getEntityFieldName())
                .exportColumnName(dto.getExportColumnName())
                .fieldOrder(dto.getFieldOrder())
                .isIncluded(dto.getIsIncluded())
                .dataFormat(dto.getDataFormat())
                .transformationRule(dto.getTransformationRule())
                .normalizationType(dto.getNormalizationType())
                .normalizationRule(dto.getNormalizationRule())
                .build();
    }

    // Вспомогательные методы для фильтров
    private static ExportTemplateFilterDto toFilterDto(ExportTemplateFilter entity) {
        return ExportTemplateFilterDto.builder()
                .id(entity.getId())
                .fieldName(entity.getFieldName())
                .filterType(entity.getFilterType())
                .filterValue(entity.getFilterValue())
                .isActive(entity.getIsActive())
                .build();
    }

    private static ExportTemplateFilter toFilterEntity(ExportTemplateFilterDto dto) {
        return ExportTemplateFilter.builder()
                .id(dto.getId())
                .fieldName(dto.getFieldName())
                .filterType(dto.getFilterType())
                .filterValue(dto.getFilterValue())
                .isActive(dto.getIsActive())
                .build();
    }

    // Вспомогательные методы для JSON
    private static List<String> parseJsonStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return getObjectMapper().readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON списка: {}", json, e);
            return new ArrayList<>();
        }
    }

    private static String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.error("Ошибка преобразования списка в JSON: {}", list, e);
            return null;
        }
    }
}