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
public class ConfigImportResultDto {

    @Builder.Default
    private boolean success = true;

    @Builder.Default
    private int createdClients = 0;
    @Builder.Default
    private int updatedClients = 0;

    @Builder.Default
    private int createdImportTemplates = 0;
    @Builder.Default
    private int updatedImportTemplates = 0;

    @Builder.Default
    private int createdExportTemplates = 0;
    @Builder.Default
    private int updatedExportTemplates = 0;

    @Builder.Default
    private int createdZoomosShops = 0;
    @Builder.Default
    private int updatedZoomosShops = 0;

    @Builder.Default
    private int createdSchedules = 0;
    @Builder.Default
    private int updatedSchedules = 0;

    @Builder.Default
    private int createdKnownSites = 0;
    @Builder.Default
    private int updatedKnownSites = 0;

    @Builder.Default
    private int createdCityNames = 0;
    @Builder.Default
    private int updatedCityNames = 0;

    @Builder.Default
    private int createdCityAddresses = 0;
    @Builder.Default
    private int updatedCityAddresses = 0;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
