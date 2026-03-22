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
public class ConfigExportDto {

    private String exportedAt;
    @Builder.Default
    private int version = 1;
    @Builder.Default
    private String generatedBy = "zoomos-v4";

    /** Какие секции присутствуют в этом файле */
    @Builder.Default
    private List<String> sections = new ArrayList<>();

    @Builder.Default
    private List<ZoomosKnownSiteConfigDto> knownSites = new ArrayList<>();

    @Builder.Default
    private List<ZoomosCityNameConfigDto> cityNames = new ArrayList<>();

    @Builder.Default
    private List<ZoomosCityAddressConfigDto> cityAddresses = new ArrayList<>();

    @Builder.Default
    private List<ClientConfigDto> clients = new ArrayList<>();

    /** Магазины Zoomos Check без привязки к клиенту */
    @Builder.Default
    private List<ZoomosShopConfigDto> standaloneZoomosShops = new ArrayList<>();
}
