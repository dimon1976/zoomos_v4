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
public class ZoomosCityIdConfigDto {

    private String siteName;
    private String cityIds;
    private String addressIds;
    @Builder.Default
    private String checkType = "API";
    @Builder.Default
    private Boolean isActive = true;
    private String parserInclude;
    @Builder.Default
    private String parserIncludeMode = "OR";
    private String parserExclude;
}
