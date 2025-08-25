package com.java.service.dashboard;

import com.java.dto.DashboardFilterDto;
import com.java.dto.DashboardOperationDto;
import com.java.dto.DashboardStatsDto;
import com.java.dto.TimeSeriesDataDto;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * Сервис для работы с дашбордом
 */
public interface DashboardService {

    /**
     * Получение общей статистики для дашборда
     */
    DashboardStatsDto getDashboardStats();

    /**
     * Получение операций с фильтрацией и пагинацией
     */
    Page<DashboardOperationDto> getFilteredOperations(DashboardFilterDto filter);

    /**
     * Получение доступных типов файлов
     */
    List<String> getAvailableFileTypes();

    /**
     * Получение статистики по клиентам
     */
    List<ClientStatsDto> getClientStats();

    /**
     * Получение расширенной статистики дашборда
     */
    DashboardStatsDto getAdvancedDashboardStats();

    /**
     * Получение временных рядов для графиков
     */
    TimeSeriesDataDto getTimeSeriesData(LocalDate fromDate, LocalDate toDate);

    /**
     * Получение детальной статистики по клиенту
     */
    ClientDetailedStatsDto getDetailedClientStats(Long clientId);

    /**
     * DTO для статистики клиентов
     */
    record ClientStatsDto(
            Long clientId,
            String clientName,
            Long operationCount,
            Long successCount,
            Long failureCount,
            Double successRate
    ) {}

    /**
     * DTO для детальной статистики клиента
     */
    record ClientDetailedStatsDto(
            Long clientId,
            String clientName,
            Long totalOperations,
            Long importOperations,
            Long exportOperations,
            Long totalRecords,
            Long totalFileSize,
            String formattedFileSize,
            Double successRate,
            Double avgProcessingTimeMinutes,
            String lastOperationDate,
            List<String> mostUsedFileTypes
    ) {}
}