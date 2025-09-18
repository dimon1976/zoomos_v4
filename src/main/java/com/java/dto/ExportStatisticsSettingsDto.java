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
public class ExportStatisticsSettingsDto {

    @Builder.Default
    private Boolean enableStatistics = false;

    @Builder.Default
    private List<String> countFields = new ArrayList<>(); // Поля для подсчета

    private String groupField; // Поле для группировки

    @Builder.Default
    private List<String> filterFields = new ArrayList<>(); // Поля для фильтрации
}