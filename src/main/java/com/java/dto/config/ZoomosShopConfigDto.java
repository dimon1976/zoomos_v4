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
public class ZoomosShopConfigDto {

    private String shopName;
    @Builder.Default
    private boolean isEnabled = true;
    @Builder.Default
    private boolean isPriority = false;

    @Builder.Default
    private List<ZoomosCityIdConfigDto> cityIds = new ArrayList<>();

    @Builder.Default
    private List<ZoomosScheduleConfigDto> schedules = new ArrayList<>();
}
