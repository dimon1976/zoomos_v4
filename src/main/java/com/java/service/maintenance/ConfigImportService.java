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
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigImportService {

    private final ClientRepository clientRepository;
    private final ImportTemplateRepository importTemplateRepository;
    private final ExportTemplateRepository exportTemplateRepository;
    private final ZoomosShopRepository zoomosShopRepository;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ZoomosCityIdRepository cityIdRepository;
    private final ZoomosCityNameRepository cityNameRepository;
    private final ZoomosCityAddressRepository cityAddressRepository;

    /**
     * Анализирует файл конфигурации без сохранения — возвращает предварительный просмотр.
     */
    @Transactional(readOnly = true)
    public ConfigImportPreviewDto preview(ConfigExportDto config, ConfigExportOptionsDto options) {
        ConfigImportPreviewDto preview = ConfigImportPreviewDto.builder()
                .fileVersion(config.getVersion())
                .exportedAt(config.getExportedAt())
                .generatedBy(config.getGeneratedBy())
                .sections(config.getSections())
                .build();

        if (options.isIncludeKnownSites() && config.getKnownSites() != null) {
            for (ZoomosKnownSiteConfigDto dto : config.getKnownSites()) {
                if (knownSiteRepository.existsBySiteName(dto.getSiteName())) {
                    preview.setUpdatedKnownSites(preview.getUpdatedKnownSites() + 1);
                } else {
                    preview.setNewKnownSites(preview.getNewKnownSites() + 1);
                }
            }
        }

        if (options.isIncludeCityDirectory()) {
            if (config.getCityNames() != null) {
                for (ZoomosCityNameConfigDto dto : config.getCityNames()) {
                    if (cityNameRepository.existsById(dto.getCityId())) {
                        preview.setUpdatedCityNames(preview.getUpdatedCityNames() + 1);
                    } else {
                        preview.setNewCityNames(preview.getNewCityNames() + 1);
                    }
                }
            }
            if (config.getCityAddresses() != null) {
                preview.setNewCityAddresses(config.getCityAddresses().size());
            }
        }

        if (options.isIncludeClients() && config.getClients() != null) {
            for (ClientConfigDto clientDto : config.getClients()) {
                boolean clientExists = clientRepository.existsByNameIgnoreCase(clientDto.getName());
                if (clientExists) {
                    preview.setUpdatedClients(preview.getUpdatedClients() + 1);
                } else {
                    preview.setNewClients(preview.getNewClients() + 1);
                }

                Client existingClient = clientExists
                        ? clientRepository.findByNameIgnoreCase(clientDto.getName()).orElse(null)
                        : null;

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

                if (options.isIncludeZoomosShops() && clientDto.getZoomosShops() != null) {
                    for (ZoomosShopConfigDto shopDto : clientDto.getZoomosShops()) {
                        boolean shopExists = zoomosShopRepository.existsByShopName(shopDto.getShopName());
                        if (shopExists) {
                            preview.setUpdatedZoomosShops(preview.getUpdatedZoomosShops() + 1);
                        } else {
                            preview.setNewZoomosShops(preview.getNewZoomosShops() + 1);
                        }

                        if (options.isIncludeSchedules() && shopDto.getSchedules() != null) {
                            ZoomosShop existingShop = shopExists
                                    ? zoomosShopRepository.findByShopName(shopDto.getShopName()).orElse(null)
                                    : null;
                            for (ZoomosScheduleConfigDto sDto : shopDto.getSchedules()) {
                                boolean schedExists = existingShop != null &&
                                        scheduleRepository.findAllByShopId(existingShop.getId()).stream()
                                                .anyMatch(s -> labelMatches(s.getLabel(), sDto.getLabel()));
                                if (schedExists) {
                                    preview.setUpdatedSchedules(preview.getUpdatedSchedules() + 1);
                                } else {
                                    preview.setNewSchedules(preview.getNewSchedules() + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        return preview;
    }

    /**
     * Выполняет импорт конфигурации. Транзакция — весь импорт атомарен.
     */
    @Transactional
    public ConfigImportResultDto execute(ConfigExportDto config, ConfigExportOptionsDto options) {
        log.info("Начало импорта конфигурации v{} от {}", config.getVersion(), config.getExportedAt());
        ConfigImportResultDto result = new ConfigImportResultDto();

        try {
            if (options.isIncludeKnownSites() && config.getKnownSites() != null) {
                importKnownSites(config.getKnownSites(), result);
            }

            if (options.isIncludeCityDirectory()) {
                if (config.getCityNames() != null) {
                    importCityNames(config.getCityNames(), result);
                }
                if (config.getCityAddresses() != null) {
                    importCityAddresses(config.getCityAddresses(), result);
                }
            }

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

    private void importKnownSites(List<ZoomosKnownSiteConfigDto> sites, ConfigImportResultDto result) {
        for (ZoomosKnownSiteConfigDto dto : sites) {
            ZoomosKnownSite site = knownSiteRepository.findBySiteName(dto.getSiteName())
                    .orElseGet(ZoomosKnownSite::new);
            boolean isNew = site.getId() == null;

            site.setSiteName(dto.getSiteName());
            site.setCheckType(dto.getCheckType() != null ? dto.getCheckType() : "ITEM");
            site.setDescription(dto.getDescription());
            site.setPriority(dto.isPriority());
            site.setIgnoreStock(dto.isIgnoreStock());

            knownSiteRepository.save(site);

            if (isNew) result.setCreatedKnownSites(result.getCreatedKnownSites() + 1);
            else result.setUpdatedKnownSites(result.getUpdatedKnownSites() + 1);
        }
    }

    private void importCityNames(List<ZoomosCityNameConfigDto> cityNames, ConfigImportResultDto result) {
        for (ZoomosCityNameConfigDto dto : cityNames) {
            if (dto.getCityId() == null || dto.getCityName() == null) continue;
            boolean isNew = !cityNameRepository.existsById(dto.getCityId());
            cityNameRepository.upsert(dto.getCityId(), dto.getCityName());
            if (isNew) result.setCreatedCityNames(result.getCreatedCityNames() + 1);
            else result.setUpdatedCityNames(result.getUpdatedCityNames() + 1);
        }
    }

    private void importCityAddresses(List<ZoomosCityAddressConfigDto> addresses, ConfigImportResultDto result) {
        for (ZoomosCityAddressConfigDto dto : addresses) {
            if (dto.getCityId() == null || dto.getAddressId() == null) continue;
            cityAddressRepository.upsert(dto.getCityId(), dto.getAddressId(), dto.getAddressName());
            result.setCreatedCityAddresses(result.getCreatedCityAddresses() + 1);
        }
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
            if (options.isIncludeZoomosShops() && dto.getZoomosShops() != null) {
                importShops(dto.getZoomosShops(), client, options, result);
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

            // Полная замена полей (orphanRemoval=true)
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

            // Полная замена полей и фильтров (orphanRemoval=true)
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

    private void importShops(List<ZoomosShopConfigDto> shops, Client client,
                             ConfigExportOptionsDto options, ConfigImportResultDto result) {
        for (ZoomosShopConfigDto dto : shops) {
            ZoomosShop shop = zoomosShopRepository.findByShopName(dto.getShopName())
                    .orElseGet(ZoomosShop::new);
            boolean isNew = shop.getId() == null;

            shop.setShopName(dto.getShopName());
            shop.setEnabled(dto.isEnabled());
            shop.setPriority(dto.isPriority());
            shop.setClient(client);

            // Upsert cityIds
            shop = zoomosShopRepository.save(shop);

            if (dto.getCityIds() != null) {
                importCityIds(dto.getCityIds(), shop);
            }

            if (options.isIncludeSchedules() && dto.getSchedules() != null) {
                importSchedules(dto.getSchedules(), shop, result);
            }

            if (isNew) result.setCreatedZoomosShops(result.getCreatedZoomosShops() + 1);
            else result.setUpdatedZoomosShops(result.getUpdatedZoomosShops() + 1);
        }
    }

    private void importCityIds(List<ZoomosCityIdConfigDto> cityIds, ZoomosShop shop) {
        for (ZoomosCityIdConfigDto dto : cityIds) {
            ZoomosCityId cityId = cityIdRepository.findByShopIdAndSiteName(shop.getId(), dto.getSiteName())
                    .orElseGet(ZoomosCityId::new);
            cityId.setShop(shop);
            cityId.setSiteName(dto.getSiteName());
            cityId.setCityIds(dto.getCityIds());
            cityId.setAddressIds(dto.getAddressIds());
            cityId.setCheckType(dto.getCheckType() != null ? dto.getCheckType() : "API");
            cityId.setIsActive(dto.getIsActive());
            cityId.setParserInclude(dto.getParserInclude());
            cityId.setParserIncludeMode(dto.getParserIncludeMode() != null ? dto.getParserIncludeMode() : "OR");
            cityId.setParserExclude(dto.getParserExclude());
            cityIdRepository.save(cityId);
        }
    }

    private void importSchedules(List<ZoomosScheduleConfigDto> schedules, ZoomosShop shop,
                                 ConfigImportResultDto result) {
        List<ZoomosShopSchedule> existing = scheduleRepository.findAllByShopId(shop.getId());
        for (ZoomosScheduleConfigDto dto : schedules) {
            ZoomosShopSchedule schedule = existing.stream()
                    .filter(s -> labelMatches(s.getLabel(), dto.getLabel()))
                    .findFirst()
                    .orElseGet(ZoomosShopSchedule::new);
            boolean isNew = schedule.getId() == null;

            schedule.setShopId(shop.getId());
            schedule.setLabel(dto.getLabel());
            schedule.setCronExpression(dto.getCronExpression());
            // Расписания при импорте всегда отключены — требуют явного включения
            schedule.setEnabled(false);
            schedule.setTimeFrom(dto.getTimeFrom());
            schedule.setTimeTo(dto.getTimeTo());
            schedule.setDropThreshold(dto.getDropThreshold());
            schedule.setErrorGrowthThreshold(dto.getErrorGrowthThreshold());
            schedule.setBaselineDays(dto.getBaselineDays());
            schedule.setMinAbsoluteErrors(dto.getMinAbsoluteErrors());
            schedule.setTrendDropThreshold(dto.getTrendDropThreshold());
            schedule.setTrendErrorThreshold(dto.getTrendErrorThreshold());
            schedule.setDateOffsetFrom(dto.getDateOffsetFrom());
            schedule.setDateOffsetTo(dto.getDateOffsetTo());

            scheduleRepository.save(schedule);

            if (isNew) result.setCreatedSchedules(result.getCreatedSchedules() + 1);
            else result.setUpdatedSchedules(result.getUpdatedSchedules() + 1);
        }
    }

    private boolean labelMatches(String existingLabel, String dtoLabel) {
        if (existingLabel == null && dtoLabel == null) return true;
        if (existingLabel == null || dtoLabel == null) return false;
        return existingLabel.equals(dtoLabel);
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
