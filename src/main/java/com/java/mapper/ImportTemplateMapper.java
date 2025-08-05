package com.java.mapper;

import com.java.dto.ImportTemplateDto;
import com.java.dto.ImportTemplateFieldDto;
import com.java.model.entity.ImportTemplate;
import com.java.model.entity.ImportTemplateField;
import com.java.model.Client;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Маппер для преобразования ImportTemplate между Entity и DTO
 */
public class ImportTemplateMapper {

    private ImportTemplateMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> DTO
     */
    public static ImportTemplateDto toDto(ImportTemplate entity) {
        if (entity == null) return null;

        return ImportTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .clientId(entity.getClient() != null ? entity.getClient().getId() : null)
                .clientName(entity.getClient() != null ? entity.getClient().getName() : null)
                .entityType(entity.getEntityType())
                .dataSourceType(entity.getDataSourceType())
                .duplicateStrategy(entity.getDuplicateStrategy())
                .errorStrategy(entity.getErrorStrategy())
                .fileType(entity.getFileType())
                .delimiter(entity.getDelimiter())
                .encoding(entity.getEncoding())
                .skipHeaderRows(entity.getSkipHeaderRows())
                .isActive(entity.getIsActive())
                .fields(entity.getFields() != null ?
                        entity.getFields().stream()
                                .map(ImportTemplateMapper::fieldToDto)
                                .collect(Collectors.toList()) : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * DTO -> Entity (для создания)
     */
    public static ImportTemplate toEntity(ImportTemplateDto dto, Client client) {
        if (dto == null) return null;

        ImportTemplate entity = ImportTemplate.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .client(client)
                .entityType(dto.getEntityType())
                .dataSourceType(dto.getDataSourceType())
                .duplicateStrategy(dto.getDuplicateStrategy())
                .errorStrategy(dto.getErrorStrategy())
                .fileType(dto.getFileType())
                .delimiter(dto.getDelimiter())
                .encoding(dto.getEncoding())
                .skipHeaderRows(dto.getSkipHeaderRows())
                .isActive(dto.getIsActive())
                .build();

        // Добавляем поля
        if (dto.getFields() != null) {
            for (ImportTemplateFieldDto fieldDto : dto.getFields()) {
                ImportTemplateField field = fieldToEntity(fieldDto);
                entity.addField(field);
            }
        }

        return entity;
    }

    /**
     * Обновление Entity из DTO
     */
    public static void updateEntity(ImportTemplate entity, ImportTemplateDto dto) {
        if (entity == null || dto == null) return;

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setEntityType(dto.getEntityType());
        entity.setDataSourceType(dto.getDataSourceType());
        entity.setDuplicateStrategy(dto.getDuplicateStrategy());
        entity.setErrorStrategy(dto.getErrorStrategy());
        entity.setFileType(dto.getFileType());
        entity.setDelimiter(dto.getDelimiter());
        entity.setEncoding(dto.getEncoding());
        entity.setSkipHeaderRows(dto.getSkipHeaderRows());
        entity.setIsActive(dto.getIsActive());

        // Обновляем поля
        entity.getFields().clear();
        if (dto.getFields() != null) {
            for (ImportTemplateFieldDto fieldDto : dto.getFields()) {
                ImportTemplateField field = fieldToEntity(fieldDto);
                entity.addField(field);
            }
        }
    }

    /**
     * Field Entity -> DTO
     */
    public static ImportTemplateFieldDto fieldToDto(ImportTemplateField entity) {
        if (entity == null) return null;

        return ImportTemplateFieldDto.builder()
                .id(entity.getId())
                .columnName(entity.getColumnName())
                .columnIndex(entity.getColumnIndex())
                .entityFieldName(entity.getEntityFieldName())
                .fieldType(entity.getFieldType())
                .isRequired(entity.getIsRequired())
                .isUnique(entity.getIsUnique())
                .defaultValue(entity.getDefaultValue())
                .dateFormat(entity.getDateFormat())
                .transformationRule(entity.getTransformationRule())
                .validationRegex(entity.getValidationRegex())
                .validationMessage(entity.getValidationMessage())
                .build();
    }

    /**
     * Field DTO -> Entity
     */
    public static ImportTemplateField fieldToEntity(ImportTemplateFieldDto dto) {
        if (dto == null) return null;

        return ImportTemplateField.builder()
                .columnName(dto.getColumnName())
                .columnIndex(dto.getColumnIndex())
                .entityFieldName(dto.getEntityFieldName())
                .fieldType(dto.getFieldType())
                .isRequired(dto.getIsRequired())
                .isUnique(dto.getIsUnique())
                .defaultValue(dto.getDefaultValue())
                .dateFormat(dto.getDateFormat())
                .transformationRule(dto.getTransformationRule())
                .validationRegex(dto.getValidationRegex())
                .validationMessage(dto.getValidationMessage())
                .build();
    }

    /**
     * Список Entity -> список DTO
     */
    public static List<ImportTemplateDto> toDtoList(List<ImportTemplate> entities) {
        return entities.stream()
                .map(ImportTemplateMapper::toDto)
                .collect(Collectors.toList());
    }
}