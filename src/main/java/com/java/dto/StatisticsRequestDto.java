package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsRequestDto {

    @Builder.Default
    private List<Long> exportSessionIds = new ArrayList<>(); // Выбранные операции

    private Long templateId; // Шаблон с настройками статистики

    // Фильтры для применения (опционально)
    private Map<String, String> additionalFilters;

    // Настройки отклонений (опционально, если не заданы - берем из глобальных)
    private Integer warningPercentage;
    private Integer criticalPercentage;
}