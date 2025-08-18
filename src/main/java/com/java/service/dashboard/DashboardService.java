package com.java.service.dashboard;

import com.java.dto.DashboardFilterDto;
import com.java.dto.DashboardOperationDto;
import com.java.dto.DashboardStatsDto;
import org.springframework.data.domain.Page;

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
}