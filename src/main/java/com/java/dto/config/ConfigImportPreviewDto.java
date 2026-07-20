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
}
