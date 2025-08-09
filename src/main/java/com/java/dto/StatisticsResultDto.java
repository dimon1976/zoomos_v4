package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResultDto {

    private Long exportSessionId;
    private String exportSessionName;
    private ZonedDateTime exportDate;
    private String groupFieldValue; // Значение группировки (например, "OZON", "Wildberries")

    // Карта: название поля -> количество значений
    private Map<String, Long> countValues;

    // Примененные фильтры
    private Map<String, String> appliedFilters;
}