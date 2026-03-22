package com.java.dto.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomosKnownSiteConfigDto {

    private String siteName;
    @Builder.Default
    private String checkType = "ITEM";
    private String description;
    @Builder.Default
    private boolean isPriority = false;
    @Builder.Default
    private boolean ignoreStock = false;
}
