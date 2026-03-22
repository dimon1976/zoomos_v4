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
public class ClientConfigDto {

    private String name;
    private String description;
    private String regionCode;
    private String regionName;
    @Builder.Default
    private boolean isActive = true;
    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private List<ImportTemplateConfigDto> importTemplates = new ArrayList<>();

    @Builder.Default
    private List<ExportTemplateConfigDto> exportTemplates = new ArrayList<>();

    @Builder.Default
    private List<ZoomosShopConfigDto> zoomosShops = new ArrayList<>();
}
