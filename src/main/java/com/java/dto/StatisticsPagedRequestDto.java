package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * DTO для запроса пагинированной статистики с фильтрами
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsPagedRequestDto {

    @NotNull(message = "ID клиента обязателен")
    private Long clientId;

    // Пагинация
    @Min(value = 0, message = "Номер страницы не может быть отрицательным")
    @Builder.Default
    private Integer page = 0;

    @Min(value = 1, message = "Размер страницы должен быть больше 0")
    @Builder.Default
    private Integer size = 20;

    // Сортировка
    private String sortBy = "createdAt"; // Поле для сортировки
    private String sortDirection = "DESC"; // ASC или DESC

    // Фильтры
    private List<Long> sessionIds; // Конкретные сессии
    private ZonedDateTime startDate; // Период от
    private ZonedDateTime endDate;   // Период до
    private String groupFieldValue; // Фильтр по группе
    private String countFieldName;  // Фильтр по полю подсчета
    
    // Дополнительные фильтры
    private Long minCountValue; // Минимальное значение счетчика
    private Long maxCountValue; // Максимальное значение счетчика
    private Boolean hasDateModifications; // Только с изменениями дат
    
    // Настройки отображения
    private Boolean includeMetadata = true; // Включать метаданные
    private Boolean useCache = true; // Использовать кэш
}