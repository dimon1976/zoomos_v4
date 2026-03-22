package com.java.service.maintenance;

import com.java.dto.config.*;
import com.java.model.Client;
import com.java.model.entity.*;
import com.java.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigExportService {

    private final ClientRepository clientRepository;
    private final ImportTemplateRepository importTemplateRepository;
    private final ExportTemplateRepository exportTemplateRepository;
    private final ZoomosShopRepository zoomosShopRepository;
    private final ZoomosShopScheduleRepository scheduleRepository;
    private final ZoomosKnownSiteRepository knownSiteRepository;
    private final ZoomosCityNameRepository cityNameRepository;
    private final ZoomosCityAddressRepository cityAddressRepository;

    @Transactional(readOnly = true)
    public ConfigExportDto exportConfig(ConfigExportOptionsDto options) {
        log.info("Экспорт конфигурации, опции: {}", options);

        List<String> sections = buildSectionsList(options);

        ConfigExportDto dto = ConfigExportDto.builder()
                .exportedAt(ZonedDateTime.now().toString())
                .sections(sections)
                .build();

        if (options.isIncludeKnownSites()) {
            dto.setKnownSites(exportKnownSites());
        }

        if (options.isIncludeCityDirectory()) {
            dto.setCityNames(exportCityNames());
            dto.setCityAddresses(exportCityAddresses());
        }

        if (options.isIncludeClients()) {
            dto.setClients(exportClients(options));
        }

        if (options.isIncludeZoomosShops()) {
            dto.setStandaloneZoomosShops(exportStandaloneShops(options));
        }

        log.info("Экспорт завершён: {} секций, {} клиентов, {} standalone-магазинов, {} известных сайтов",
                sections.size(),
                dto.getClients().size(),
                dto.getStandaloneZoomosShops().size(),
                dto.getKnownSites().size());
        return dto;
    }

    private List<String> buildSectionsList(ConfigExportOptionsDto options) {
        List<String> sections = new ArrayList<>();
        if (options.isIncludeClients()) sections.add("clients");
        if (options.isIncludeImportTemplates()) sections.add("importTemplates");
        if (options.isIncludeExportTemplates()) sections.add("exportTemplates");
        if (options.isIncludeZoomosShops()) sections.add("zoomosShops");
        if (options.isIncludeSchedules()) sections.add("schedules");
        if (options.isIncludeKnownSites()) sections.add("knownSites");
        if (options.isIncludeCityDirectory()) sections.add("cityDirectory");
        return sections;
    }

    private List<ZoomosKnownSiteConfigDto> exportKnownSites() {
        return knownSiteRepository.findAllByOrderBySiteNameAsc().stream()
                .map(this::toKnownSiteDto)
                .toList();
    }

    private List<ZoomosCityNameConfigDto> exportCityNames() {
        return cityNameRepository.findAll().stream()
                .map(cn -> ZoomosCityNameConfigDto.builder()
                        .cityId(cn.getCityId())
                        .cityName(cn.getCityName())
                        .build())
                .toList();
    }

    private List<ZoomosCityAddressConfigDto> exportCityAddresses() {
        return cityAddressRepository.findAll().stream()
                .map(ca -> ZoomosCityAddressConfigDto.builder()
                        .cityId(ca.getCityId())
                        .addressId(ca.getAddressId())
                        .addressName(ca.getAddressName())
                        .build())
                .toList();
    }

    private List<ClientConfigDto> exportClients(ConfigExportOptionsDto options) {
        return clientRepository.findAll().stream()
                .map(client -> toClientDto(client, options))
                .toList();
    }

    private ClientConfigDto toClientDto(Client client, ConfigExportOptionsDto options) {
        ClientConfigDto dto = ClientConfigDto.builder()
                .name(client.getName())
                .description(client.getDescription())
                .regionCode(client.getRegionCode())
                .regionName(client.getRegionName())
                .isActive(client.isActive())
                .sortOrder(client.getSortOrder())
                .build();

        if (options.isIncludeImportTemplates()) {
            dto.setImportTemplates(exportImportTemplates(client));
        }

        if (options.isIncludeExportTemplates()) {
            dto.setExportTemplates(exportExportTemplates(client));
        }

        if (options.isIncludeZoomosShops()) {
            dto.setZoomosShops(exportShops(client, options));
        }

        return dto;
    }

    private List<ImportTemplateConfigDto> exportImportTemplates(Client client) {
        return importTemplateRepository.findByClient(client).stream()
                .map(this::toImportTemplateDto)
                .toList();
    }

    private ImportTemplateConfigDto toImportTemplateDto(com.java.model.entity.ImportTemplate t) {
        return ImportTemplateConfigDto.builder()
                .name(t.getName())
                .description(t.getDescription())
                .entityType(t.getEntityType() != null ? t.getEntityType().name() : null)
                .dataSourceType(t.getDataSourceType() != null ? t.getDataSourceType().name() : null)
                .duplicateStrategy(t.getDuplicateStrategy() != null ? t.getDuplicateStrategy().name() : null)
                .errorStrategy(t.getErrorStrategy() != null ? t.getErrorStrategy().name() : null)
                .fileType(t.getFileType())
                .delimiter(t.getDelimiter())
                .encoding(t.getEncoding())
                .skipHeaderRows(t.getSkipHeaderRows())
                .isActive(t.getIsActive())
                .fields(t.getFields().stream().map(this::toImportFieldDto).toList())
                .build();
    }

    private ImportTemplateFieldConfigDto toImportFieldDto(com.java.model.entity.ImportTemplateField f) {
        return ImportTemplateFieldConfigDto.builder()
                .columnName(f.getColumnName())
                .columnIndex(f.getColumnIndex())
                .entityFieldName(f.getEntityFieldName())
                .fieldType(f.getFieldType() != null ? f.getFieldType().name() : null)
                .isRequired(f.getIsRequired())
                .isUnique(f.getIsUnique())
                .defaultValue(f.getDefaultValue())
                .dateFormat(f.getDateFormat())
                .transformationRule(f.getTransformationRule())
                .validationRegex(f.getValidationRegex())
                .validationMessage(f.getValidationMessage())
                .build();
    }

    private List<ExportTemplateConfigDto> exportExportTemplates(Client client) {
        return exportTemplateRepository.findByClient(client).stream()
                .map(this::toExportTemplateDto)
                .toList();
    }

    private ExportTemplateConfigDto toExportTemplateDto(com.java.model.entity.ExportTemplate t) {
        return ExportTemplateConfigDto.builder()
                .name(t.getName())
                .description(t.getDescription())
                .entityType(t.getEntityType() != null ? t.getEntityType().name() : null)
                .exportStrategy(t.getExportStrategy() != null ? t.getExportStrategy().name() : null)
                .fileFormat(t.getFileFormat())
                .csvDelimiter(t.getCsvDelimiter())
                .csvEncoding(t.getCsvEncoding())
                .csvQuoteChar(t.getCsvQuoteChar())
                .csvIncludeHeader(t.getCsvIncludeHeader())
                .xlsxSheetName(t.getXlsxSheetName())
                .xlsxAutoSizeColumns(t.getXlsxAutoSizeColumns())
                .maxRowsPerFile(t.getMaxRowsPerFile())
                .isActive(t.getIsActive())
                .enableStatistics(t.getEnableStatistics())
                .statisticsCountFields(t.getStatisticsCountFields())
                .statisticsGroupField(t.getStatisticsGroupField())
                .statisticsFilterFields(t.getStatisticsFilterFields())
                .filterableFields(t.getFilterableFields())
                .filenameTemplate(t.getFilenameTemplate())
                .includeClientName(t.getIncludeClientName())
                .includeExportType(t.getIncludeExportType())
                .includeTaskNumber(t.getIncludeTaskNumber())
                .exportTypeLabel(t.getExportTypeLabel())
                .operationNameSource(t.getOperationNameSource())
                .fields(t.getFields().stream().map(this::toExportFieldDto).toList())
                .filters(t.getFilters().stream().map(this::toExportFilterDto).toList())
                .build();
    }

    private ExportTemplateFieldConfigDto toExportFieldDto(com.java.model.entity.ExportTemplateField f) {
        return ExportTemplateFieldConfigDto.builder()
                .entityFieldName(f.getEntityFieldName())
                .exportColumnName(f.getExportColumnName())
                .fieldOrder(f.getFieldOrder())
                .isIncluded(f.getIsIncluded())
                .dataFormat(f.getDataFormat())
                .transformationRule(f.getTransformationRule())
                .normalizationType(f.getNormalizationType() != null ? f.getNormalizationType().name() : null)
                .normalizationRule(f.getNormalizationRule())
                .build();
    }

    private ExportTemplateFilterConfigDto toExportFilterDto(com.java.model.entity.ExportTemplateFilter f) {
        return ExportTemplateFilterConfigDto.builder()
                .fieldName(f.getFieldName())
                .filterType(f.getFilterType() != null ? f.getFilterType().name() : null)
                .filterValue(f.getFilterValue())
                .isActive(f.getIsActive())
                .build();
    }

    private List<ZoomosShopConfigDto> exportShops(Client client, ConfigExportOptionsDto options) {
        return zoomosShopRepository.findAllByClient(client).stream()
                .map(shop -> toShopDto(shop, options))
                .toList();
    }

    private List<ZoomosShopConfigDto> exportStandaloneShops(ConfigExportOptionsDto options) {
        return zoomosShopRepository.findAllByClientIsNull().stream()
                .map(shop -> toShopDto(shop, options))
                .toList();
    }

    private ZoomosShopConfigDto toShopDto(ZoomosShop shop, ConfigExportOptionsDto options) {
        ZoomosShopConfigDto dto = ZoomosShopConfigDto.builder()
                .shopName(shop.getShopName())
                .isEnabled(shop.isEnabled())
                .isPriority(shop.isPriority())
                .cityIds(shop.getCityIds().stream().map(this::toCityIdDto).toList())
                .build();

        if (options.isIncludeSchedules()) {
            dto.setSchedules(scheduleRepository.findAllByShopId(shop.getId()).stream()
                    .map(this::toScheduleDto)
                    .toList());
        }

        return dto;
    }

    private ZoomosCityIdConfigDto toCityIdDto(ZoomosCityId c) {
        return ZoomosCityIdConfigDto.builder()
                .siteName(c.getSiteName())
                .cityIds(c.getCityIds())
                .addressIds(c.getAddressIds())
                .checkType(c.getCheckType())
                .isActive(c.getIsActive())
                .parserInclude(c.getParserInclude())
                .parserIncludeMode(c.getParserIncludeMode())
                .parserExclude(c.getParserExclude())
                .build();
    }

    private ZoomosScheduleConfigDto toScheduleDto(ZoomosShopSchedule s) {
        return ZoomosScheduleConfigDto.builder()
                .label(s.getLabel())
                .cronExpression(s.getCronExpression())
                .isEnabled(s.isEnabled())
                .timeFrom(s.getTimeFrom())
                .timeTo(s.getTimeTo())
                .dropThreshold(s.getDropThreshold())
                .errorGrowthThreshold(s.getErrorGrowthThreshold())
                .baselineDays(s.getBaselineDays())
                .minAbsoluteErrors(s.getMinAbsoluteErrors())
                .trendDropThreshold(s.getTrendDropThreshold())
                .trendErrorThreshold(s.getTrendErrorThreshold())
                .dateOffsetFrom(s.getDateOffsetFrom())
                .dateOffsetTo(s.getDateOffsetTo())
                .build();
    }

    private ZoomosKnownSiteConfigDto toKnownSiteDto(ZoomosKnownSite site) {
        return ZoomosKnownSiteConfigDto.builder()
                .siteName(site.getSiteName())
                .checkType(site.getCheckType())
                .description(site.getDescription())
                .isPriority(site.isPriority())
                .ignoreStock(site.isIgnoreStock())
                .build();
    }
}
