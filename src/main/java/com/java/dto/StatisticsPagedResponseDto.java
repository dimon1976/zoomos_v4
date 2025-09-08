package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO для ответа с пагинированной статистикой
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsPagedResponseDto {

    // Данные статистики
    private List<StatisticsItemDto> statistics;
    
    // Пагинация
    private PaginationDto pagination;
    
    // Метаданные
    private MetadataDto metadata;
    
    // Агрегированная информация для текущей страницы
    private AggregationDto aggregation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsItemDto {
        private Long id;
        private Long exportSessionId;
        private String operationName;
        private ZonedDateTime exportDate;
        private String groupFieldName;
        private String groupFieldValue;
        private String countFieldName;
        private Long countValue;
        private Long dateModificationsCount;
        private Long totalRecordsCount;
        private String modificationType;
        private ZonedDateTime createdAt;
        
        // Дополнительные вычисляемые поля
        private Double dateModificationRate; // Процент изменений дат
        private String alertLevel; // NORMAL, WARNING, CRITICAL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationDto {
        private Integer currentPage;
        private Integer pageSize;
        private Long totalElements;
        private Integer totalPages;
        private Boolean hasNext;
        private Boolean hasPrevious;
        private String sortBy;
        private String sortDirection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDto {
        private ZonedDateTime generatedAt;
        private Long queryTimeMs;
        private String cacheStatus; // HIT, MISS, DISABLED
        private Map<String, Object> appliedFilters;
        private Long totalRecordsInPeriod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregationDto {
        private Long totalCountValue; // Сумма всех countValue на текущей странице
        private Long totalDateModifications; // Сумма всех изменений дат
        private Double averageCountValue; // Среднее значение
        private String topGroupValue; // Самая частая группа на странице
        private Integer uniqueGroups; // Количество уникальных групп
        private Integer uniqueSessions; // Количество уникальных сессий
    }
}