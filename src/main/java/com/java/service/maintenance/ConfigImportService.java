package com.java.service.maintenance;

import com.java.dto.config.*;
import com.java.model.Client;
import com.java.model.entity.*;
import com.java.model.enums.*;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigImportService {

    private final ClientRepository clientRepository;
    private final ImportTemplateRepository importTemplateRepository;
    private final ExportTemplateRepository exportTemplateRepository;

    @Transactional(readOnly = true)
    public ConfigImportPreviewDto preview(ConfigExportDto config, ConfigExportOptionsDto options) {
        ConfigImportPreviewDto preview = ConfigImportPreviewDto.builder()
                .fileVersion(config.getVersion())
                .exportedAt(config.getExportedAt())
                .generatedBy(config.getGeneratedBy())
                .sections(config.getSections())
                .build();

        if (options.isIncludeClients() && config.getClients() != null) {
            for (ClientConfigDto clientDto : config.getClients()) {
                Optional<Client> existingClientOpt = clientRepository.findByNameIgnoreCase(clientDto.getName());
                if (existingClientOpt.isPresent()) {
                    preview.setUpdatedClients(preview.getUpdatedClients() + 1);
                } else {
                    preview.setNewClients(preview.getNewClients() + 1);
                }

                Client existingClient = existingClientOpt.orElse(null);

                if (options.isIncludeImportTemplates() && clientDto.getImportTemplates() != null) {
                    for (ImportTemplateConfigDto tDto : clientDto.getImportTemplates()) {
                        boolean exists = existingClient != null &&
                                importTemplateRepository.existsByNameAndClient(tDto.getName(), existingClient);
                        if (exists) {
                            preview.setUpdatedImportTemplates(preview.getUpdatedImportTemplates() + 1);
                        } else {
                            preview.setNewImportTemplates(preview.getNewImportTemplates() + 1);
                        }
                    }
                }

                if (options.isIncludeExportTemplates() && clientDto.getExportTemplates() != null) {
                    for (ExportTemplateConfigDto tDto : clientDto.getExportTemplates()) {
                        boolean exists = existingClient != null &&
                                exportTemplateRepository.existsByNameAndClient(tDto.getName(), existingClient);
                        if (exists) {
                            preview.setUpdatedExportTemplates(preview.getUpdatedExportTemplates() + 1);
                        } else {
                            preview.setNewExportTemplates(preview.getNewExportTemplates() + 1);
                        }
                    }
                }
            }
        }

        return preview;
    }

    @Transactional
    public ConfigImportResultDto execute(ConfigExportDto config, ConfigExportOptionsDto options) {
        log.info("Начало импорта конфигурации v{} от {}", config.getVersion(), config.getExportedAt());
        ConfigImportResultDto result = new ConfigImportResultDto();

        try {
            if (options.isIncludeClients() && config.getClients() != null) {
                importClients(config.getClients(), options, result);
            }
        } catch (Exception e) {
            log.error("Ошибка при импорте конфигурации", e);
            result.setSuccess(false);
            result.getErrors().add("Критическая ошибка: " + e.getMessage());
        }

        log.info("Импорт завершён. Успех: {}, ошибок: {}", result.isSuccess(), result.getErrors().size());
        return result;
    }

    private void importClients(List<ClientConfigDto> clients, ConfigExportOptionsDto options,
                               ConfigImportResultDto result) {
        for (ClientConfigDto dto : clients) {
            Client client = clientRepository.findByNameIgnoreCase(dto.getName())
                    .orElseGet(Client::new);
            boolean isNew = client.getId() == null;

            client.setName(dto.getName());
            client.setDescription(dto.getDescription());
            client.setRegionCode(dto.getRegionCode());
            client.setRegionName(dto.getRegionName());
            client.setActive(dto.isActive());
            client.setSortOrder(dto.getSortOrder());

            client = clientRepository.save(client);

            if (isNew) result.setCreatedClients(result.getCreatedClients() + 1);
            else result.setUpdatedClients(result.getUpdatedClients() + 1);

            if (options.isIncludeImportTemplates() && dto.getImportTemplates() != null) {
                importImportTemplates(dto.getImportTemplates(), client, result);
            }
            if (options.isIncludeExportTemplates() && dto.getExportTemplates() != null) {
                importExportTemplates(dto.getExportTemplates(), client, result);
            }
        }
    }

    private void importImportTemplates(List<ImportTemplateConfigDto> templates, Client client,
                                       ConfigImportResultDto result) {
        for (ImportTemplateConfigDto dto : templates) {
            ImportTemplate template = importTemplateRepository.findByNameAndClient(dto.getName(), client)
                    .orElseGet(ImportTemplate::new);
            boolean isNew = template.getId() == null;

            template.setName(dto.getName());
            template.setDescription(dto.getDescription());
            template.setClient(client);
            template.setEntityType(parseEnum(EntityType.class, dto.getEntityType()));
            template.setDataSourceType(parseEnum(DataSourceType.class, dto.getDataSourceType()));
            template.setDuplicateStrategy(parseEnum(DuplicateStrategy.class, dto.getDuplicateStrategy()));
            template.setErrorStrategy(parseEnum(ErrorStrategy.class, dto.getErrorStrategy()));
            template.setFileType(dto.getFileType());
            template.setDelimiter(dto.getDelimiter());
            template.setEncoding(dto.getEncoding());
            template.setSkipHeaderRows(dto.getSkipHeaderRows());
            template.setIsActive(dto.getIsActive());

            template.getFields().clear();
            if (dto.getFields() != null) {
                for (ImportTemplateFieldConfigDto fDto : dto.getFields()) {
                    ImportTemplateField field = ImportTemplateField.builder()
                            .template(template)
                            .columnName(fDto.getColumnName())
                            .columnIndex(fDto.getColumnIndex())
                            .entityFieldName(fDto.getEntityFieldName())
                            .fieldType(parseEnum(FieldType.class, fDto.getFieldType()))
                            .isRequired(fDto.getIsRequired())
                            .isUnique(fDto.getIsUnique())
                            .defaultValue(fDto.getDefaultValue())
                            .dateFormat(fDto.getDateFormat())
                            .transformationRule(fDto.getTransformationRule())
                            .validationRegex(fDto.getValidationRegex())
                            .validationMessage(fDto.getValidationMessage())
                            .build();
                    template.getFields().add(field);
                }
            }

            importTemplateRepository.save(template);

            if (isNew) result.setCreatedImportTemplates(result.getCreatedImportTemplates() + 1);
            else result.setUpdatedImportTemplates(result.getUpdatedImportTemplates() + 1);
        }
    }

    private void importExportTemplates(List<ExportTemplateConfigDto> templates, Client client,
                                       ConfigImportResultDto result) {
        for (ExportTemplateConfigDto dto : templates) {
            ExportTemplate template = exportTemplateRepository.findByNameAndClient(dto.getName(), client)
                    .orElseGet(ExportTemplate::new);
            boolean isNew = template.getId() == null;

            template.setName(dto.getName());
            template.setDescription(dto.getDescription());
            template.setClient(client);
            template.setEntityType(parseEnum(EntityType.class, dto.getEntityType()));
            template.setExportStrategy(parseEnum(ExportStrategy.class, dto.getExportStrategy()));
            template.setFileFormat(dto.getFileFormat());
            template.setCsvDelimiter(dto.getCsvDelimiter());
            template.setCsvEncoding(dto.getCsvEncoding());
            template.setCsvQuoteChar(dto.getCsvQuoteChar());
            template.setCsvIncludeHeader(dto.getCsvIncludeHeader());
            template.setXlsxSheetName(dto.getXlsxSheetName());
            template.setXlsxAutoSizeColumns(dto.getXlsxAutoSizeColumns());
            template.setMaxRowsPerFile(dto.getMaxRowsPerFile());
            template.setIsActive(dto.getIsActive());
            template.setEnableStatistics(dto.getEnableStatistics());
            template.setStatisticsCountFields(dto.getStatisticsCountFields());
            template.setStatisticsGroupField(dto.getStatisticsGroupField());
            template.setStatisticsFilterFields(dto.getStatisticsFilterFields());
            template.setFilterableFields(dto.getFilterableFields());
            template.setFilenameTemplate(dto.getFilenameTemplate());
            template.setIncludeClientName(dto.getIncludeClientName());
            template.setIncludeExportType(dto.getIncludeExportType());
            template.setIncludeTaskNumber(dto.getIncludeTaskNumber());
            template.setExportTypeLabel(dto.getExportTypeLabel());
            template.setOperationNameSource(dto.getOperationNameSource());

            template.getFields().clear();
            if (dto.getFields() != null) {
                for (ExportTemplateFieldConfigDto fDto : dto.getFields()) {
                    ExportTemplateField field = ExportTemplateField.builder()
                            .template(template)
                            .entityFieldName(fDto.getEntityFieldName())
                            .exportColumnName(fDto.getExportColumnName())
                            .fieldOrder(fDto.getFieldOrder())
                            .isIncluded(fDto.getIsIncluded())
                            .dataFormat(fDto.getDataFormat())
                            .transformationRule(fDto.getTransformationRule())
                            .normalizationType(parseEnum(NormalizationType.class, fDto.getNormalizationType()))
                            .normalizationRule(fDto.getNormalizationRule())
                            .build();
                    template.getFields().add(field);
                }
            }

            template.getFilters().clear();
            if (dto.getFilters() != null) {
                for (ExportTemplateFilterConfigDto filterDto : dto.getFilters()) {
                    ExportTemplateFilter filter = ExportTemplateFilter.builder()
                            .template(template)
                            .fieldName(filterDto.getFieldName())
                            .filterType(parseEnum(FilterType.class, filterDto.getFilterType()))
                            .filterValue(filterDto.getFilterValue())
                            .isActive(filterDto.getIsActive())
                            .build();
                    template.getFilters().add(filter);
                }
            }

            exportTemplateRepository.save(template);

            if (isNew) result.setCreatedExportTemplates(result.getCreatedExportTemplates() + 1);
            else result.setUpdatedExportTemplates(result.getUpdatedExportTemplates() + 1);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Неизвестное значение enum {}: '{}' — пропущено", enumClass.getSimpleName(), value);
            return null;
        }
    }
}
