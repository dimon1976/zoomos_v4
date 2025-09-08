package com.java.service.statistics;

import com.java.dto.*;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import com.java.service.statistics.StatisticsCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportStatisticsService {

    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final StatisticsSettingsService settingsService;
    private final StatisticsCacheService cacheService;

    /**
     * Вычисляет статистику для выбранных операций экспорта
     */
    public List<StatisticsComparisonDto> calculateComparison(StatisticsRequestDto request) {
        log.info("Расчет статистики для операций: {}", request.getExportSessionIds());

        // Получаем шаблон
        ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(request.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
            throw new IllegalArgumentException("Статистика не включена для данного шаблона");
        }

        // Получаем сессии экспорта
        List<ExportSession> sessions = getExportSessions(request.getExportSessionIds(), template);
        if (sessions.isEmpty()) {
            log.warn("Нет сессий экспорта для анализа");
            return Collections.emptyList();
        }

        // Получаем сохранённую статистику для всех сессий
        List<Long> sessionIds = sessions.stream().map(ExportSession::getId).toList();
        List<ExportStatistics> allStatistics = statisticsRepository.findByExportSessionIds(sessionIds);

        if (allStatistics.isEmpty()) {
            log.warn("Нет сохранённой статистики для сессий: {}", sessionIds);
            return Collections.emptyList();
        }

        // Группируем статистику по значению группировки
        Map<String, List<ExportStatistics>> statisticsByGroup = allStatistics.stream()
                .collect(Collectors.groupingBy(ExportStatistics::getGroupFieldValue));

        // Создаем сравнительную статистику
        return createComparison(statisticsByGroup, sessions, request, template);
    }

    /**
     * Создает сравнительную статистику
     */
    private List<StatisticsComparisonDto> createComparison(
            Map<String, List<ExportStatistics>> statisticsByGroup,
            List<ExportSession> sessions,
            StatisticsRequestDto request,
            ExportTemplate template) {

        List<StatisticsComparisonDto> comparisons = new ArrayList<>();

        for (Map.Entry<String, List<ExportStatistics>> entry : statisticsByGroup.entrySet()) {
            String groupValue = entry.getKey();
            List<ExportStatistics> groupStatistics = entry.getValue();

            // Группируем по сессиям и создаем карту session -> statistics
            Map<Long, List<ExportStatistics>> statsBySession = groupStatistics.stream()
                    .collect(Collectors.groupingBy(stat -> stat.getExportSession().getId()));

            // Создаем статистику по операциям (сортируем по дате)
            List<StatisticsComparisonDto.OperationStatistics> operationStats = new ArrayList<>();

            sessions.stream()
                    .sorted((s1, s2) -> s2.getStartedAt().compareTo(s1.getStartedAt())) // новые первыми
                    .forEach(session -> {
                        List<ExportStatistics> sessionStats = statsBySession.get(session.getId());
                        if (sessionStats != null && !sessionStats.isEmpty()) {
                            // Находим предыдущую сессию для сравнения
                            ExportSession previousSession = findPreviousSession(session, sessions);
                            List<ExportStatistics> previousStats = previousSession != null ?
                                    statsBySession.get(previousSession.getId()) : null;

                            Map<String, StatisticsComparisonDto.MetricValue> metrics =
                                    calculateMetrics(sessionStats, previousStats, request);

                            // Вычисляем статистику изменений дат
                            StatisticsComparisonDto.DateModificationStats dateModStats = 
                                    calculateDateModificationStats(sessionStats);

                            operationStats.add(StatisticsComparisonDto.OperationStatistics.builder()
                                    .exportSessionId(session.getId())
                                    .operationId(session.getFileOperation().getId())
                                    .operationName(generateOperationName(session, template))
                                    .exportDate(session.getStartedAt())
                                    .metrics(metrics)
                                    .dateModificationStats(dateModStats)
                                    .build());
                        }
                    });

            if (!operationStats.isEmpty()) {
                comparisons.add(StatisticsComparisonDto.builder()
                        .groupFieldValue(groupValue)
                        .operations(operationStats)
                        .build());
            }
        }

        return comparisons;
    }

    /**
     * Генерирует название операции для отображения в статистике
     */
    private String generateOperationName(ExportSession session, ExportTemplate template) {
        String nameSource = template.getOperationNameSource();

        if ("TASK_NUMBER".equals(nameSource)) {
            // Извлекаем номер задания из данных операции
            String taskNumber = extractTaskNumberFromSession(session);
            return taskNumber != null ? taskNumber : "Экспорт " + session.getId();
        } else if ("FILE_NAME".equals(nameSource)) {
            // Используем имя файла
            String fileName = session.getFileOperation().getFileName();
            return fileName != null ? fileName.replace(".csv", "").replace(".xlsx", "") : "Экспорт " + session.getId();
        } else {
            // По умолчанию
            return "Экспорт " + session.getId();
        }
    }

    /**
     * Извлекает номер задания из операций-источников сессии экспорта
     */
    private String extractTaskNumberFromSession(ExportSession session) {
        try {
            // Парсим список операций-источников
            List<Long> sourceOperationIds = parseSourceOperationIds(session.getSourceOperationIds());
            if (sourceOperationIds.isEmpty()) {
                return null;
            }

            // Берем первую операцию и ищем в ней номер задания
            // Это требует дополнительного запроса к БД
            // Пока возвращаем null, можно доработать позже
            return null;
        } catch (Exception e) {
            log.error("Ошибка извлечения номера задания из сессии {}", session.getId(), e);
            return null;
        }
    }

    /**
     * Парсит JSON строку с ID операций-источников
     */
    private List<Long> parseSourceOperationIds(String sourceOperationIds) {
        if (sourceOperationIds == null || sourceOperationIds.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(sourceOperationIds, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга операций-источников: {}", sourceOperationIds, e);
            return Collections.emptyList();
        }
    }

    /**
     * Вычисляет метрики с отклонениями
     */
    private Map<String, StatisticsComparisonDto.MetricValue> calculateMetrics(
            List<ExportStatistics> currentStats,
            List<ExportStatistics> previousStats,
            StatisticsRequestDto request) {

        Map<String, StatisticsComparisonDto.MetricValue> metrics = new HashMap<>();

        // Создаем карту поле -> значение для текущих и предыдущих данных
        Map<String, Long> currentValues = currentStats.stream()
                .collect(Collectors.toMap(
                        ExportStatistics::getCountFieldName,
                        ExportStatistics::getCountValue,
                        (v1, v2) -> v1 // в случае дубликатов берем первое значение
                ));

        Map<String, Long> previousValues = previousStats != null ?
                previousStats.stream().collect(Collectors.toMap(
                        ExportStatistics::getCountFieldName,
                        ExportStatistics::getCountValue,
                        (v1, v2) -> v1
                )) : Collections.emptyMap();

        // Для каждого поля вычисляем метрику
        for (Map.Entry<String, Long> entry : currentValues.entrySet()) {
            String fieldName = entry.getKey();
            Long currentValue = entry.getValue();
            Long previousValue = previousValues.get(fieldName);

            StatisticsComparisonDto.MetricValue metric = calculateMetricValue(
                    currentValue, previousValue,
                    request.getWarningPercentage(), request.getCriticalPercentage());

            metrics.put(fieldName, metric);
        }

        return metrics;
    }

    /**
     * Вычисляет значение метрики с отклонением
     */
    private StatisticsComparisonDto.MetricValue calculateMetricValue(
            Long currentValue, Long previousValue, Integer warningPercentage, Integer criticalPercentage) {

        StatisticsComparisonDto.MetricValue.MetricValueBuilder metricBuilder =
                StatisticsComparisonDto.MetricValue.builder()
                        .currentValue(currentValue)
                        .previousValue(previousValue);

        if (previousValue == null || previousValue == 0) {
            return metricBuilder
                    .changePercentage(0.0)
                    .changeType(StatisticsComparisonDto.ChangeType.STABLE)
                    .alertLevel(StatisticsComparisonDto.AlertLevel.NORMAL)
                    .build();
        }

        // Вычисляем процент изменения
        double changePercentage = ((double) (currentValue - previousValue) / previousValue) * 100;

        // Определяем тип изменения
        StatisticsComparisonDto.ChangeType changeType;
        if (changePercentage > 1) {
            changeType = StatisticsComparisonDto.ChangeType.UP;
        } else if (changePercentage < -1) {
            changeType = StatisticsComparisonDto.ChangeType.DOWN;
        } else {
            changeType = StatisticsComparisonDto.ChangeType.STABLE;
        }

        // Определяем уровень предупреждения
        StatisticsComparisonDto.AlertLevel alertLevel = determineAlertLevel(
                Math.abs(changePercentage), warningPercentage, criticalPercentage);

        return metricBuilder
                .changePercentage(changePercentage)
                .changeType(changeType)
                .alertLevel(alertLevel)
                .build();
    }

    /**
     * Определяет уровень предупреждения на основе процента отклонения
     */
    private StatisticsComparisonDto.AlertLevel determineAlertLevel(
            double absChangePercentage, Integer warningPercentage, Integer criticalPercentage) {

        int warning = warningPercentage != null ? warningPercentage : settingsService.getWarningPercentage();
        int critical = criticalPercentage != null ? criticalPercentage : settingsService.getCriticalPercentage();

        if (absChangePercentage >= critical) {
            return StatisticsComparisonDto.AlertLevel.CRITICAL;
        } else if (absChangePercentage >= warning) {
            return StatisticsComparisonDto.AlertLevel.WARNING;
        } else {
            return StatisticsComparisonDto.AlertLevel.NORMAL;
        }
    }

    // Вспомогательные методы

    /**
     * Находит предыдущую сессию экспорта для сравнения
     */
    private ExportSession findPreviousSession(ExportSession currentSession, List<ExportSession> allSessions) {
        return allSessions.stream()
                .filter(session -> session.getStartedAt().isBefore(currentSession.getStartedAt()))
                .max(Comparator.comparing(ExportSession::getStartedAt))
                .orElse(null);
    }

    private List<ExportSession> getExportSessions(List<Long> sessionIds, ExportTemplate template) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            // Если не выбраны конкретные сессии, берем все для данного шаблона
            return sessionRepository.findByTemplateWithTemplate(template, org.springframework.data.domain.Pageable.unpaged())
                    .getContent();
        } else {
            return sessionRepository.findAllByIdsWithTemplate(sessionIds);
        }
    }

    /**
     * Вычисляет статистику изменений дат для операции
     */
    private StatisticsComparisonDto.DateModificationStats calculateDateModificationStats(
            List<ExportStatistics> sessionStats) {
        
        // Ищем запись с изменениями дат
        ExportStatistics dateModRecord = sessionStats.stream()
                .filter(stat -> "DATE_MODIFICATIONS".equals(stat.getCountFieldName()))
                .filter(stat -> "DATE_ADJUSTMENT".equals(stat.getModificationType()))
                .findFirst()
                .orElse(null);
        
        if (dateModRecord == null) {
            // Нет данных об изменениях дат
            return null;
        }
        
        Long modifiedCount = dateModRecord.getDateModificationsCount();
        Long totalCount = dateModRecord.getTotalRecordsCount();
        
        if (totalCount == null || totalCount == 0) {
            return null;
        }
        
        double modificationPercentage = (modifiedCount.doubleValue() / totalCount.doubleValue()) * 100.0;
        
        // Определяем уровень предупреждения (если более 50% дат изменено - критическое)
        StatisticsComparisonDto.AlertLevel alertLevel;
        if (modificationPercentage >= 50.0) {
            alertLevel = StatisticsComparisonDto.AlertLevel.CRITICAL;
        } else if (modificationPercentage >= 25.0) {
            alertLevel = StatisticsComparisonDto.AlertLevel.WARNING;
        } else {
            alertLevel = StatisticsComparisonDto.AlertLevel.NORMAL;
        }
        
        return StatisticsComparisonDto.DateModificationStats.builder()
                .modifiedCount(modifiedCount)
                .totalCount(totalCount)
                .modificationPercentage(modificationPercentage)
                .alertLevel(alertLevel)
                .build();
    }

    // === Enhanced Methods with Caching and Pagination ===

    /**
     * Получает агрегированную статистику с кэшированием
     */
    public List<Object[]> getAggregatedStatistics(List<Long> sessionIds) {
        return cacheService.getAggregatedStats(sessionIds);
    }

    /**
     * Получает топ групп для клиента с пагинацией
     */
    public Page<Object[]> getTopGroupsForClient(Long clientId, ZonedDateTime sinceDate, Pageable pageable) {
        log.debug("Получение топ групп для клиента {} с {}", clientId, sinceDate);
        return statisticsRepository.findTopGroupsByClient(clientId, sinceDate, pageable);
    }

    /**
     * Получает дневные тренды с кэшированием
     */
    public List<Object[]> getDailyTrendsForClient(Long clientId, int daysBack) {
        ZonedDateTime sinceDate = ZonedDateTime.now().minusDays(daysBack);
        return cacheService.getDailyTrends(clientId, sinceDate);
    }

    /**
     * Получает статистику за период с кэшированием
     */
    public List<ExportStatistics> getStatisticsForDateRange(Long clientId, 
                                                           ZonedDateTime startDate, 
                                                           ZonedDateTime endDate) {
        return cacheService.getStatisticsForPeriod(clientId, startDate, endDate);
    }

    /**
     * Получает статистику с поддержкой пагинации для больших датасетов
     */
    public Page<ExportStatistics> getStatisticsPagedBySessionIds(List<Long> sessionIds, Pageable pageable) {
        log.debug("Получение пагинированной статистики для {} сессий", sessionIds.size());
        return statisticsRepository.findByExportSessionIdsPaged(sessionIds, pageable);
    }

    /**
     * Получает сводку статистики для dashboard
     */
    public Map<String, Object> getStatisticsSummary(Long clientId, int daysBack) {
        ZonedDateTime sinceDate = ZonedDateTime.now().minusDays(daysBack);
        
        // Получаем топ 5 групп
        List<Object[]> topGroups = cacheService.getTopGroups(clientId, sinceDate, 5);
        
        // Получаем дневные тренды
        List<Object[]> dailyTrends = cacheService.getDailyTrends(clientId, sinceDate);
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("topGroups", topGroups);
        summary.put("dailyTrends", dailyTrends);
        summary.put("period", daysBack + " дней");
        summary.put("generatedAt", ZonedDateTime.now());
        
        return summary;
    }

    /**
     * Получает детальную статистику для конкретной группы
     */
    public Map<String, Object> getGroupDetailedStats(Long sessionId, String groupValue) {
        List<ExportStatistics> groupStats = statisticsRepository
                .findByExportSessionIdAndGroupFieldValue(sessionId, groupValue);
        
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", sessionId);
        details.put("groupValue", groupValue);
        details.put("statistics", groupStats);
        details.put("totalRecords", groupStats.size());
        
        // Подсчитываем общие метрики
        long totalCount = groupStats.stream()
                .mapToLong(ExportStatistics::getCountValue)
                .sum();
        long totalDateModifications = groupStats.stream()
                .mapToLong(ExportStatistics::getDateModificationsCount)
                .sum();
        
        details.put("totalCount", totalCount);
        details.put("totalDateModifications", totalDateModifications);
        
        return details;
    }

    /**
     * Проверяет здоровье системы статистики
     */
    public Map<String, Object> getStatisticsHealthCheck(Long clientId) {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Проверяем количество сессий с статистикой
            ZonedDateTime lastWeek = ZonedDateTime.now().minusWeeks(1);
            List<ExportStatistics> recentStats = cacheService
                    .getStatisticsForPeriod(clientId, lastWeek, ZonedDateTime.now());
            
            health.put("status", "healthy");
            health.put("recentStatisticsCount", recentStats.size());
            health.put("lastWeekPeriod", lastWeek.toString() + " - " + ZonedDateTime.now().toString());
            health.put("cacheStatus", "active");
            
        } catch (Exception e) {
            log.warn("Проблема с проверкой здоровья статистики для клиента {}: {}", clientId, e.getMessage());
            health.put("status", "degraded");
            health.put("error", e.getMessage());
        }
        
        return health;
    }
}