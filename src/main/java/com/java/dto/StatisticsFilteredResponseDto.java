package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO для ответа с отфильтрованными результатами статистики
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsFilteredResponseDto {

    /**
     * Отфильтрованные результаты статистики
     */
    private List<StatisticsComparisonDto> results;

    /**
     * Общее количество найденных записей (без пагинации)
     */
    private Long totalElements;

    /**
     * Общее количество страниц
     */
    private Integer totalPages;

    /**
     * Текущая страница (начинается с 0)
     */
    private Integer currentPage;

    /**
     * Размер страницы
     */
    private Integer pageSize;

    /**
     * Есть ли следующая страница
     */
    private Boolean hasNext;

    /**
     * Есть ли предыдущая страница
     */
    private Boolean hasPrevious;

    /**
     * Примененные фильтры для информации
     */
    private StatisticsFilterDto appliedFilters;

    /**
     * Агрегированная статистика по отфильтрованным данным
     */
    private Map<String, Object> aggregatedStats;

    /**
     * Доступные значения для динамических фильтров
     * Ключ - имя поля, значение - список уникальных значений
     */
    @Builder.Default
    private Map<String, List<String>> availableFieldValues = Map.of();

    /**
     * Метаинформация о полях для построения UI фильтров
     */
    @Builder.Default
    private Map<String, Object> fieldMetadata = Map.of();
}