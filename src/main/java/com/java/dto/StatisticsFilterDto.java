package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DTO для расширенной фильтрации статистики экспорта
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsFilterDto {

    @NotNull
    private Long clientId;

    /**
     * Список ID сессий экспорта для анализа
     */
    @Builder.Default
    private List<Long> exportSessionIds = List.of();

    /**
     * Фильтр по уровням предупреждений
     * Например: ["NORMAL", "WARNING", "CRITICAL"]
     */
    @Builder.Default
    private Set<String> alertLevels = Set.of();

    /**
     * Фильтр по полям групппировки
     * Ключ - название поля, значение - список допустимых значений
     */
    @Builder.Default
    private Map<String, List<String>> groupFieldFilters = Map.of();

    /**
     * Фильтр по полям подсчета
     * Ключ - название поля, значение - список допустимых значений
     */
    @Builder.Default
    private Map<String, List<String>> countFieldFilters = Map.of();

    /**
     * Фильтр по диапазону изменений (в процентах)
     * Минимальный процент изменения для отображения
     */
    @Min(0)
    @Max(1000)
    private Integer minChangePercentage;

    /**
     * Максимальный процент изменения для отображения
     */
    @Min(0)
    @Max(1000)
    private Integer maxChangePercentage;

    /**
     * Скрыть записи без изменений (countValue = 0 или изменения < 1%)
     */
    @Builder.Default
    private Boolean hideNoChanges = false;

    /**
     * Показывать только записи с предупреждениями (WARNING уровень)
     */
    @Builder.Default
    private Boolean onlyWarnings = false;

    /**
     * Показывать только проблемные записи (WARNING + CRITICAL)
     */
    @Builder.Default
    private Boolean onlyProblems = false;

    /**
     * Дополнительные условия фильтрации
     * Поддержка гибких условий для будущего расширения
     */
    @Builder.Default
    private Map<String, Object> additionalConditions = Map.of();

    /**
     * Настройки пагинации
     */
    @Builder.Default
    @Min(0)
    private Integer page = 0;

    @Builder.Default
    @Min(1)
    @Max(1000)
    private Integer size = 50;

    /**
     * Поле для сортировки
     */
    private String sortBy;

    /**
     * Направление сортировки: ASC или DESC
     */
    @Builder.Default
    private String sortDirection = "DESC";
}