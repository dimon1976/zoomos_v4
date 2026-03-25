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
public class ConfigImportPreviewDto {

    private int fileVersion;
    private String exportedAt;
    private String generatedBy;

    /** Секции, присутствующие в файле */
    @Builder.Default
    private List<String> sections = new ArrayList<>();

    @Builder.Default
    private int newClients = 0;
    @Builder.Default
    private int updatedClients = 0;

    @Builder.Default
    private int newImportTemplates = 0;
    @Builder.Default
    private int updatedImportTemplates = 0;

    @Builder.Default
    private int newExportTemplates = 0;
    @Builder.Default
    private int updatedExportTemplates = 0;

    @Builder.Default
    private int newZoomosShops = 0;
    @Builder.Default
    private int updatedZoomosShops = 0;

    @Builder.Default
    private int newSchedules = 0;
    @Builder.Default
    private int updatedSchedules = 0;

    @Builder.Default
    private int newKnownSites = 0;
    @Builder.Default
    private int updatedKnownSites = 0;

    @Builder.Default
    private int newCityNames = 0;
    @Builder.Default
    private int updatedCityNames = 0;

    @Builder.Default
    private int newCityAddresses = 0;
    @Builder.Default
    private int updatedCityAddresses = 0;

    /** Расписания при импорте всегда отключены — требуют явного включения */
    @Builder.Default
    private boolean schedulesImportedDisabled = false;
}
