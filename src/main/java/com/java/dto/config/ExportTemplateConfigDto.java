package com.java.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportTemplateConfigDto {

    private String name;
    private String description;
    private String entityType;
    private String exportStrategy;
    @Builder.Default
    private String fileFormat = "CSV";
    @Builder.Default
    private String csvDelimiter = ";";
    @Builder.Default
    private String csvEncoding = "UTF-8";
    @Builder.Default
    private String csvQuoteChar = "\"";
    @Builder.Default
    private Boolean csvIncludeHeader = true;
    @Builder.Default
    private String xlsxSheetName = "Данные";
    @Builder.Default
    private Boolean xlsxAutoSizeColumns = true;
    private Integer maxRowsPerFile;
    @Builder.Default
    private Boolean isActive = true;
    @Builder.Default
    private Boolean enableStatistics = false;
    private String statisticsCountFields;
    private String statisticsGroupField;
    private String statisticsFilterFields;
    private String filterableFields;
    private String filenameTemplate;
    @Builder.Default
    private Boolean includeClientName = true;
    @Builder.Default
    private Boolean includeExportType = false;
    @Builder.Default
    private Boolean includeTaskNumber = false;
    private String exportTypeLabel;
    private String operationNameSource;

    @Builder.Default
    private List<ExportTemplateFieldConfigDto> fields = new ArrayList<>();

    @Builder.Default
    private List<ExportTemplateFilterConfigDto> filters = new ArrayList<>();
}
