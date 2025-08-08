package com.java.dto;

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
public class ExportStatisticsConfigDto {

    private Long id;
    private Long templateId;

    @Builder.Default
    private List<String> metricFields = new ArrayList<>();

    private String groupByField;

    private String filterField;

    @Builder.Default
    private List<String> filterValues = new ArrayList<>();

    @Builder.Default
    private Integer deviationThresholdPercent = 10;

    @Builder.Default
    private Boolean isEnabled = true;
}