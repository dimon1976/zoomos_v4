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

    // === New Filtering Methods for Iteration 4 ===

    /**
     * Получает отфильтрованную статистику с поддержкой пагинации (оптимизированная версия)
     */
    public StatisticsFilteredResponseDto calculateFilteredComparison(StatisticsFilterDto filterDto) {
        log.info("Расчет отфильтрованной статистики для клиента: {} с фильтрами", filterDto.getClientId());

        // Используем оптимизированную фильтрацию на уровне БД
        if (shouldUseOptimizedFiltering(filterDto)) {
            return calculateFilteredComparisonOptimized(filterDto);
        }

        // Fallback к старому методу для сложных фильтров
        return calculateFilteredComparisonFallback(filterDto);
    }

    /**
     * Оптимизированная версия фильтрации с использованием нативных SQL запросов
     */
    private StatisticsFilteredResponseDto calculateFilteredComparisonOptimized(StatisticsFilterDto filterDto) {
        log.debug("Использование оптимизированной фильтрации для клиента: {}", filterDto.getClientId());

        // Подготавливаем параметры для нативного запроса
        String sessionIds = prepareArrayParameter(filterDto.getExportSessionIds());
        String groupFieldValues = prepareArrayParameter(extractGroupFieldValues(filterDto));
        String countFieldNames = prepareArrayParameter(extractCountFieldNames(filterDto));
        
        // Получаем общее количество для пагинации
        Long totalElements = statisticsRepository.countFilteredStatistics(
                filterDto.getClientId(), sessionIds, groupFieldValues, countFieldNames);
        
        if (totalElements == 0) {
            return StatisticsFilteredResponseDto.builder()
                    .results(Collections.emptyList())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(filterDto.getPage())
                    .pageSize(filterDto.getSize())
                    .hasNext(false)
                    .hasPrevious(false)
                    .appliedFilters(filterDto)
                    .aggregatedStats(Collections.emptyMap())
                    .availableFieldValues(Collections.emptyMap())
                    .fieldMetadata(Collections.emptyMap())
                    .build();
        }
        
        // Получаем отфильтрованные данные с пагинацией
        int offset = filterDto.getPage() * filterDto.getSize();
        List<ExportStatistics> rawStatistics = statisticsRepository.findFilteredStatisticsOptimized(
                filterDto.getClientId(), sessionIds, groupFieldValues, countFieldNames,
                filterDto.getSize(), offset);
        
        // Преобразуем в DTO и применяем логические фильтры (изменения, уровни предупреждений)
        List<StatisticsComparisonDto> results = convertToComparisonDtos(rawStatistics, filterDto);
        
        // Получаем агрегированную статистику
        Map<String, Object> aggregatedStats = getOptimizedAggregatedStats(
                filterDto.getClientId(), sessionIds, groupFieldValues);
        
        // Получаем метаданные полей
        Map<String, List<String>> availableValues = getOptimizedFieldValues(filterDto.getClientId());
        Map<String, Object> fieldMetadata = buildOptimizedFieldMetadata(filterDto.getClientId());
        
        int totalPages = (int) Math.ceil((double) totalElements / filterDto.getSize());
        boolean hasNext = (filterDto.getPage() + 1) < totalPages;
        boolean hasPrevious = filterDto.getPage() > 0;

        return StatisticsFilteredResponseDto.builder()
                .results(results)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(filterDto.getPage())
                .pageSize(filterDto.getSize())
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .appliedFilters(filterDto)
                .aggregatedStats(aggregatedStats)
                .availableFieldValues(availableValues)
                .fieldMetadata(fieldMetadata)
                .build();
    }

    /**
     * Fallback метод для сложных фильтров
     */
    private StatisticsFilteredResponseDto calculateFilteredComparisonFallback(StatisticsFilterDto filterDto) {
        log.debug("Использование fallback фильтрации для клиента: {}", filterDto.getClientId());

        // Создаем базовый запрос статистики
        StatisticsRequestDto baseRequest = StatisticsRequestDto.builder()
                .exportSessionIds(filterDto.getExportSessionIds())
                .warningPercentage(settingsService.getWarningPercentage())
                .criticalPercentage(settingsService.getCriticalPercentage())
                .build();

        // Получаем базовые результаты
        List<StatisticsComparisonDto> baseResults = calculateComparison(baseRequest);
        
        // Применяем фильтры
        List<StatisticsComparisonDto> filteredResults = applyFilters(baseResults, filterDto);
        
        // Применяем пагинацию
        PageRequest pageRequest = PageRequest.of(filterDto.getPage(), filterDto.getSize());
        List<StatisticsComparisonDto> paginatedResults = applyPagination(filteredResults, pageRequest);
        
        // Получаем метаданные полей
        Map<String, List<String>> availableValues = getAvailableFieldValues(filterDto.getClientId(), filterDto.getExportSessionIds());
        Map<String, Object> fieldMetadata = buildFieldMetadata(baseResults);
        
        // Вычисляем агрегированную статистику
        Map<String, Object> aggregatedStats = calculateAggregatedStats(filteredResults);

        return StatisticsFilteredResponseDto.builder()
                .results(paginatedResults)
                .totalElements((long) filteredResults.size())
                .totalPages((int) Math.ceil((double) filteredResults.size() / filterDto.getSize()))
                .currentPage(filterDto.getPage())
                .pageSize(filterDto.getSize())
                .hasNext((filterDto.getPage() + 1) * filterDto.getSize() < filteredResults.size())
                .hasPrevious(filterDto.getPage() > 0)
                .appliedFilters(filterDto)
                .aggregatedStats(aggregatedStats)
                .availableFieldValues(availableValues)
                .fieldMetadata(fieldMetadata)
                .build();
    }

    /**
     * Получает доступные значения полей для динамических фильтров
     */
    public Map<String, List<String>> getAvailableFieldValues(Long clientId, List<Long> sessionIds) {
        log.debug("Получение доступных значений полей для клиента: {}", clientId);
        
        List<ExportStatistics> statistics;
        if (sessionIds != null && !sessionIds.isEmpty()) {
            statistics = statisticsRepository.findByExportSessionIds(sessionIds);
        } else {
            // Получаем все статистики клиента за последние 3 месяца
            ZonedDateTime threeMonthsAgo = ZonedDateTime.now().minusMonths(3);
            statistics = cacheService.getStatisticsForPeriod(clientId, threeMonthsAgo, ZonedDateTime.now());
        }
        
        Map<String, List<String>> availableValues = new HashMap<>();
        
        // Группируем уникальные значения по полям
        Set<String> groupFieldValues = statistics.stream()
                .map(ExportStatistics::getGroupFieldValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Set<String> countFieldNames = statistics.stream()
                .map(ExportStatistics::getCountFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        Set<String> groupFieldNames = statistics.stream()
                .map(ExportStatistics::getGroupFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        availableValues.put("groupFieldValues", new ArrayList<>(groupFieldValues));
        availableValues.put("countFieldNames", new ArrayList<>(countFieldNames));
        availableValues.put("groupFieldNames", new ArrayList<>(groupFieldNames));
        
        return availableValues;
    }

    /**
     * Применяет фильтры к результатам статистики
     */
    private List<StatisticsComparisonDto> applyFilters(List<StatisticsComparisonDto> results, StatisticsFilterDto filterDto) {
        return results.stream()
                .filter(comparison -> applyGroupFieldFilter(comparison, filterDto))
                .map(comparison -> filterOperationsByAlertLevels(comparison, filterDto))
                .filter(comparison -> !comparison.getOperations().isEmpty()) // Убираем пустые группы
                .collect(Collectors.toList());
    }

    /**
     * Применяет фильтр по полям группировки
     */
    private boolean applyGroupFieldFilter(StatisticsComparisonDto comparison, StatisticsFilterDto filterDto) {
        if (filterDto.getGroupFieldFilters().isEmpty()) {
            return true;
        }
        
        // Проверяем, соответствует ли значение группы фильтрам
        return filterDto.getGroupFieldFilters().values().stream()
                .anyMatch(allowedValues -> allowedValues.contains(comparison.getGroupFieldValue()));
    }

    /**
     * Фильтрует операции по уровням предупреждений и другим условиям
     */
    private StatisticsComparisonDto filterOperationsByAlertLevels(StatisticsComparisonDto comparison, StatisticsFilterDto filterDto) {
        List<StatisticsComparisonDto.OperationStatistics> filteredOperations = comparison.getOperations().stream()
                .filter(operation -> applyAlertLevelFilter(operation, filterDto))
                .filter(operation -> applyChangePercentageFilter(operation, filterDto))
                .filter(operation -> applyNoChangesFilter(operation, filterDto))
                .filter(operation -> applyWarningOnlyFilter(operation, filterDto))
                .filter(operation -> applyProblemsOnlyFilter(operation, filterDto))
                .collect(Collectors.toList());

        return StatisticsComparisonDto.builder()
                .groupFieldValue(comparison.getGroupFieldValue())
                .operations(filteredOperations)
                .build();
    }

    /**
     * Применяет фильтр по уровням предупреждений
     */
    private boolean applyAlertLevelFilter(StatisticsComparisonDto.OperationStatistics operation, StatisticsFilterDto filterDto) {
        if (filterDto.getAlertLevels().isEmpty()) {
            return true;
        }
        
        // Проверяем, есть ли метрики с нужными уровнями предупреждений
        return operation.getMetrics().values().stream()
                .anyMatch(metric -> filterDto.getAlertLevels().contains(metric.getAlertLevel().name()));
    }

    /**
     * Применяет фильтр по диапазону изменений
     */
    private boolean applyChangePercentageFilter(StatisticsComparisonDto.OperationStatistics operation, StatisticsFilterDto filterDto) {
        if (filterDto.getMinChangePercentage() == null && filterDto.getMaxChangePercentage() == null) {
            return true;
        }
        
        return operation.getMetrics().values().stream()
                .anyMatch(metric -> {
                    double absChangePercent = Math.abs(metric.getChangePercentage());
                    boolean minOk = filterDto.getMinChangePercentage() == null || absChangePercent >= filterDto.getMinChangePercentage();
                    boolean maxOk = filterDto.getMaxChangePercentage() == null || absChangePercent <= filterDto.getMaxChangePercentage();
                    return minOk && maxOk;
                });
    }

    /**
     * Применяет фильтр "скрыть без изменений"
     */
    private boolean applyNoChangesFilter(StatisticsComparisonDto.OperationStatistics operation, StatisticsFilterDto filterDto) {
        if (!filterDto.getHideNoChanges()) {
            return true;
        }
        
        // Скрываем операции где все метрики имеют изменения < 1%
        return operation.getMetrics().values().stream()
                .anyMatch(metric -> Math.abs(metric.getChangePercentage()) >= 1.0);
    }

    /**
     * Применяет фильтр "только предупреждения" (WARNING уровень)
     */
    private boolean applyWarningOnlyFilter(StatisticsComparisonDto.OperationStatistics operation, StatisticsFilterDto filterDto) {
        if (!filterDto.getOnlyWarnings()) {
            return true;
        }
        
        // Показываем только операции с WARNING уровнем (не CRITICAL)
        return operation.getMetrics().values().stream()
                .anyMatch(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.WARNING);
    }

    /**
     * Применяет фильтр "только проблемы" (WARNING + CRITICAL)
     */
    private boolean applyProblemsOnlyFilter(StatisticsComparisonDto.OperationStatistics operation, StatisticsFilterDto filterDto) {
        if (!filterDto.getOnlyProblems()) {
            return true;
        }
        
        // Показываем только операции с WARNING или CRITICAL уровнем
        return operation.getMetrics().values().stream()
                .anyMatch(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.WARNING ||
                                   metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.CRITICAL);
    }

    /**
     * Применяет пагинацию к результатам
     */
    private List<StatisticsComparisonDto> applyPagination(List<StatisticsComparisonDto> results, PageRequest pageRequest) {
        int start = (int) pageRequest.getOffset();
        int end = Math.min(start + pageRequest.getPageSize(), results.size());
        
        if (start >= results.size()) {
            return Collections.emptyList();
        }
        
        return results.subList(start, end);
    }

    /**
     * Строит метаданные полей для UI
     */
    private Map<String, Object> buildFieldMetadata(List<StatisticsComparisonDto> results) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Собираем информацию о типах полей и диапазонах значений
        Set<String> allMetricFields = results.stream()
                .flatMap(comp -> comp.getOperations().stream())
                .flatMap(op -> op.getMetrics().keySet().stream())
                .collect(Collectors.toSet());
        
        Map<String, String> fieldTypes = new HashMap<>();
        Map<String, Map<String, Double>> fieldRanges = new HashMap<>();
        
        for (String field : allMetricFields) {
            fieldTypes.put(field, "NUMERIC");
            
            // Находим диапазон значений для поля
            List<Double> values = results.stream()
                    .flatMap(comp -> comp.getOperations().stream())
                    .map(op -> op.getMetrics().get(field))
                    .filter(Objects::nonNull)
                    .map(StatisticsComparisonDto.MetricValue::getChangePercentage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (!values.isEmpty()) {
                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                
                Map<String, Double> range = new HashMap<>();
                range.put("min", min);
                range.put("max", max);
                fieldRanges.put(field, range);
            }
        }
        
        metadata.put("fieldTypes", fieldTypes);
        metadata.put("fieldRanges", fieldRanges);
        metadata.put("totalFields", allMetricFields.size());
        
        return metadata;
    }

    /**
     * Вычисляет агрегированную статистику по отфильтрованным данным
     */
    private Map<String, Object> calculateAggregatedStats(List<StatisticsComparisonDto> results) {
        Map<String, Object> stats = new HashMap<>();
        
        int totalOperations = results.stream()
                .mapToInt(comp -> comp.getOperations().size())
                .sum();
        
        long normalCount = results.stream()
                .flatMap(comp -> comp.getOperations().stream())
                .flatMap(op -> op.getMetrics().values().stream())
                .mapToLong(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.NORMAL ? 1 : 0)
                .sum();
        
        long warningCount = results.stream()
                .flatMap(comp -> comp.getOperations().stream())
                .flatMap(op -> op.getMetrics().values().stream())
                .mapToLong(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.WARNING ? 1 : 0)
                .sum();
        
        long criticalCount = results.stream()
                .flatMap(comp -> comp.getOperations().stream())
                .flatMap(op -> op.getMetrics().values().stream())
                .mapToLong(metric -> metric.getAlertLevel() == StatisticsComparisonDto.AlertLevel.CRITICAL ? 1 : 0)
                .sum();
        
        stats.put("totalOperations", totalOperations);
        stats.put("totalGroups", results.size());
        stats.put("normalCount", normalCount);
        stats.put("warningCount", warningCount);
        stats.put("criticalCount", criticalCount);
        stats.put("problemsCount", warningCount + criticalCount);
        
        return stats;
    }

    // === Optimization Helper Methods ===

    /**
     * Определяет, можно ли использовать оптимизированную фильтрацию
     */
    private boolean shouldUseOptimizedFiltering(StatisticsFilterDto filterDto) {
        // Простые фильтры: группировка, поля, сессии
        boolean hasSimpleFilters = !filterDto.getGroupFieldFilters().isEmpty() || 
                                  !filterDto.getCountFieldFilters().isEmpty() ||
                                  !filterDto.getExportSessionIds().isEmpty();
        
        // Сложные фильтры требуют fallback
        boolean hasComplexFilters = filterDto.getMinChangePercentage() != null ||
                                   filterDto.getMaxChangePercentage() != null ||
                                   filterDto.getOnlyWarnings() ||
                                   filterDto.getOnlyProblems() ||
                                   filterDto.getHideNoChanges();
        
        // Используем оптимизацию только для простых фильтров или без фильтров
        return !hasComplexFilters;
    }

    /**
     * Подготавливает параметр массива для PostgreSQL
     */
    private String prepareArrayParameter(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return "{" + list.stream()
                .map(Object::toString)
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "}";
    }

    /**
     * Извлекает значения полей группировки из фильтров
     */
    private List<String> extractGroupFieldValues(StatisticsFilterDto filterDto) {
        return filterDto.getGroupFieldFilters().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Извлекает значения полей подсчета из фильтров
     */
    private List<String> extractCountFieldNames(StatisticsFilterDto filterDto) {
        return filterDto.getCountFieldFilters().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Преобразует сырые данные статистики в DTO сравнения
     */
    private List<StatisticsComparisonDto> convertToComparisonDtos(List<ExportStatistics> rawStatistics, StatisticsFilterDto filterDto) {
        // Группируем по группе полей
        Map<String, List<ExportStatistics>> groupedStats = rawStatistics.stream()
                .collect(Collectors.groupingBy(ExportStatistics::getGroupFieldValue));

        List<StatisticsComparisonDto> comparisons = new ArrayList<>();

        for (Map.Entry<String, List<ExportStatistics>> entry : groupedStats.entrySet()) {
            String groupValue = entry.getKey();
            List<ExportStatistics> groupStatistics = entry.getValue();

            // Группируем по сессиям
            Map<Long, List<ExportStatistics>> statsBySession = groupStatistics.stream()
                    .collect(Collectors.groupingBy(stat -> stat.getExportSession().getId()));

            List<StatisticsComparisonDto.OperationStatistics> operationStats = new ArrayList<>();

            for (Map.Entry<Long, List<ExportStatistics>> sessionEntry : statsBySession.entrySet()) {
                Long sessionId = sessionEntry.getKey();
                List<ExportStatistics> sessionStats = sessionEntry.getValue();
                
                if (!sessionStats.isEmpty()) {
                    ExportSession session = sessionStats.get(0).getExportSession();
                    
                    // Простые метрики без сравнения с предыдущими (для оптимизации)
                    Map<String, StatisticsComparisonDto.MetricValue> metrics = new HashMap<>();
                    for (ExportStatistics stat : sessionStats) {
                        StatisticsComparisonDto.MetricValue metric = StatisticsComparisonDto.MetricValue.builder()
                                .currentValue(stat.getCountValue())
                                .previousValue(null)
                                .changePercentage(0.0)
                                .changeType(StatisticsComparisonDto.ChangeType.STABLE)
                                .alertLevel(StatisticsComparisonDto.AlertLevel.NORMAL)
                                .build();
                        metrics.put(stat.getCountFieldName(), metric);
                    }

                    operationStats.add(StatisticsComparisonDto.OperationStatistics.builder()
                            .exportSessionId(sessionId)
                            .operationId(session.getFileOperation().getId())
                            .operationName("Экспорт " + sessionId)
                            .exportDate(session.getStartedAt())
                            .metrics(metrics)
                            .dateModificationStats(null)
                            .build());
                }
            }

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
     * Получает оптимизированную агрегированную статистику
     */
    private Map<String, Object> getOptimizedAggregatedStats(Long clientId, String sessionIds, String groupFieldValues) {
        List<Object[]> rawStats = statisticsRepository.getFilteredAggregatedStats(clientId, sessionIds, groupFieldValues);
        
        Map<String, Object> stats = new HashMap<>();
        for (Object[] row : rawStats) {
            String metricName = (String) row[0];
            String metricValue = (String) row[1];
            
            try {
                stats.put(metricName, Long.parseLong(metricValue));
            } catch (NumberFormatException e) {
                stats.put(metricName, metricValue);
            }
        }
        
        // Добавляем дефолтные значения
        stats.putIfAbsent("total_operations", 0L);
        stats.putIfAbsent("total_groups", 0L);
        stats.putIfAbsent("total_records", 0L);
        stats.putIfAbsent("normalCount", 0L);
        stats.putIfAbsent("warningCount", 0L);
        stats.putIfAbsent("criticalCount", 0L);
        stats.putIfAbsent("problemsCount", 0L);
        
        return stats;
    }

    /**
     * Получает оптимизированные значения полей
     */
    private Map<String, List<String>> getOptimizedFieldValues(Long clientId) {
        ZonedDateTime sinceDate = ZonedDateTime.now().minusMonths(3);
        List<Object[]> fieldMetadata = statisticsRepository.getFieldMetadataForClient(clientId, sinceDate);
        
        Map<String, List<String>> availableValues = new HashMap<>();
        
        for (Object[] row : fieldMetadata) {
            String fieldType = (String) row[0];
            Object jsonValues = row[1];
            
            if (jsonValues != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<String> values = mapper.readValue(jsonValues.toString(), 
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                    availableValues.put(fieldType, values);
                } catch (Exception e) {
                    log.warn("Ошибка парсинга JSON для поля {}: {}", fieldType, e.getMessage());
                    availableValues.put(fieldType, Collections.emptyList());
                }
            }
        }
        
        return availableValues;
    }

    /**
     * Создает оптимизированные метаданные полей
     */
    private Map<String, Object> buildOptimizedFieldMetadata(Long clientId) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Базовая информация
        metadata.put("clientId", clientId);
        metadata.put("optimized", true);
        metadata.put("lastUpdated", ZonedDateTime.now().toString());
        
        // Счетчики полей добавим позже при необходимости
        metadata.put("totalFields", 0);
        metadata.put("fieldTypes", Collections.emptyMap());
        metadata.put("fieldRanges", Collections.emptyMap());
        
        return metadata;
    }
}