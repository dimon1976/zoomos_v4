package com.java.service.statistics;

import com.java.dto.*;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportStatisticsService {

    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final JdbcTemplate jdbcTemplate;
    private final StatisticsSettingsService settingsService;

    /**
     * Вычисляет статистику для выбранных операций экспорта (без фильтра)
     */
    public List<StatisticsComparisonDto> calculateComparison(StatisticsRequestDto request) {
        return calculateComparison(request, null, null);
    }

    /**
     * Вычисляет статистику для выбранных операций экспорта с учётом фильтра
     */
    public List<StatisticsComparisonDto> calculateComparison(
            StatisticsRequestDto request,
            String filterFieldName,
            String filterFieldValue) {

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

        // Получаем статистику с учётом фильтра
        List<Long> sessionIds = sessions.stream().map(ExportSession::getId).toList();
        List<ExportStatistics> allStatistics;

        if (filterFieldName != null && filterFieldValue != null) {
            // Получаем отфильтрованную статистику
            allStatistics = statisticsRepository.findBySessionIdsAndFilter(
                    sessionIds, filterFieldName, filterFieldValue);
        } else {
            // Получаем общую статистику (filter = NULL)
            allStatistics = statisticsRepository.findBySessionIdsWithoutFilter(sessionIds);
        }

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

        // Кешируем имена операций для избежания повторных вызовов
        Map<Long, String> operationNamesCache = new HashMap<>();
        for (ExportSession session : sessions) {
            operationNamesCache.put(session.getId(), generateOperationName(session, template));
        }

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
                                    .operationName(operationNamesCache.get(session.getId()))
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

        // Добавляем итоговую строку "ОБЩЕЕ КОЛИЧЕСТВО" в начало списка
        if (!comparisons.isEmpty()) {
            StatisticsComparisonDto totalSummary = calculateTotalSummary(
                    statisticsByGroup, sessions, request, operationNamesCache);
            comparisons.add(0, totalSummary);
        }

        return comparisons;
    }

    /**
     * Генерирует название операции для отображения в статистике
     */
    public String generateOperationName(ExportSession session, ExportTemplate template) {
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
     * Использует ту же логику что и в ExportProcessorService
     */
    private String extractTaskNumberFromSession(ExportSession session) {
        try {
            // Парсим список операций-источников
            List<Long> sourceOperationIds = parseSourceOperationIds(session.getSourceOperationIds());

            if (sourceOperationIds.isEmpty()) {
                return null;
            }

            // Оптимизация: один запрос вместо N запросов (исправление N+1 Query)
            String sql = "SELECT product_additional1 FROM av_data " +
                        "WHERE operation_id IN (" + sourceOperationIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")) + ") " +
                        "AND product_additional1 IS NOT NULL AND product_additional1 != '' " +
                        "LIMIT 1";

            String taskNumber = jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    Object value = rs.getObject("product_additional1");
                    return value != null ? value.toString().trim() : null;
                }
                return null;
            });

            return (taskNumber != null && !taskNumber.isEmpty()) ? taskNumber : null;
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

        // Если нет предыдущего значения - не можем рассчитать изменение
        if (previousValue == null) {
            return metricBuilder
                    .changePercentage(0.0)
                    .changeType(StatisticsComparisonDto.ChangeType.STABLE)
                    .alertLevel(StatisticsComparisonDto.AlertLevel.NORMAL)
                    .build();
        }

        // Если предыдущее значение было 0, а текущее не 0 - это рост на 100%
        if (previousValue == 0) {
            if (currentValue == 0) {
                return metricBuilder
                        .changePercentage(0.0)
                        .changeType(StatisticsComparisonDto.ChangeType.STABLE)
                        .alertLevel(StatisticsComparisonDto.AlertLevel.NORMAL)
                        .build();
            } else {
                // Был 0, стало currentValue - это рост на 100%
                double changePercentage = 100.0;
                StatisticsComparisonDto.AlertLevel alertLevel = determineAlertLevel(
                        changePercentage, warningPercentage, criticalPercentage);

                return metricBuilder
                        .changePercentage(changePercentage)
                        .changeType(StatisticsComparisonDto.ChangeType.UP)
                        .alertLevel(alertLevel)
                        .build();
            }
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

    /**
     * Вычисляет метрики с отклонениями для итоговых сумм (перегрузка для Map)
     */
    private Map<String, StatisticsComparisonDto.MetricValue> calculateMetrics(
            Map<String, Long> currentTotals,
            Map<String, Long> previousTotals,
            StatisticsRequestDto request) {

        Map<String, StatisticsComparisonDto.MetricValue> metrics = new HashMap<>();

        // Для каждого поля вычисляем метрику
        for (Map.Entry<String, Long> entry : currentTotals.entrySet()) {
            String fieldName = entry.getKey();
            Long currentValue = entry.getValue();
            Long previousValue = previousTotals.get(fieldName);

            StatisticsComparisonDto.MetricValue metric = calculateMetricValue(
                    currentValue, previousValue,
                    request.getWarningPercentage(), request.getCriticalPercentage());

            metrics.put(fieldName, metric);
        }

        return metrics;
    }

    /**
     * Вычисляет общие итоги по всем группам для каждой операции экспорта
     */
    private StatisticsComparisonDto calculateTotalSummary(
            Map<String, List<ExportStatistics>> statisticsByGroup,
            List<ExportSession> sessions,
            StatisticsRequestDto request,
            Map<Long, String> operationNamesCache) {

        // 1. Собираем все статистики по сессиям (из всех групп)
        Map<Long, List<ExportStatistics>> allStatsBySession = new HashMap<>();
        for (List<ExportStatistics> groupStats : statisticsByGroup.values()) {
            for (ExportStatistics stat : groupStats) {
                allStatsBySession
                    .computeIfAbsent(stat.getExportSession().getId(), k -> new ArrayList<>())
                    .add(stat);
            }
        }

        // 2. Создаем итоговые операции
        List<StatisticsComparisonDto.OperationStatistics> totalOperations = new ArrayList<>();

        sessions.stream()
                .sorted((s1, s2) -> s2.getStartedAt().compareTo(s1.getStartedAt())) // новые первыми
                .forEach(session -> {
                    List<ExportStatistics> sessionStats = allStatsBySession.get(session.getId());

                    if (sessionStats != null && !sessionStats.isEmpty()) {
                        // Суммируем метрики (группируем по countFieldName и суммируем countValue)
                        // Исключаем DATE_MODIFICATIONS из итогов
                        Map<String, Long> totalMetrics = sessionStats.stream()
                                .filter(stat -> !"DATE_MODIFICATIONS".equals(stat.getCountFieldName()))
                                .collect(Collectors.groupingBy(
                                        ExportStatistics::getCountFieldName,
                                        Collectors.summingLong(ExportStatistics::getCountValue)
                                ));

                        // Находим предыдущую сессию и суммируем её метрики
                        ExportSession previousSession = findPreviousSession(session, sessions);
                        Map<String, Long> previousTotalMetrics = Collections.emptyMap();

                        if (previousSession != null) {
                            List<ExportStatistics> previousSessionStats = allStatsBySession.get(previousSession.getId());
                            if (previousSessionStats != null && !previousSessionStats.isEmpty()) {
                                previousTotalMetrics = previousSessionStats.stream()
                                        .filter(stat -> !"DATE_MODIFICATIONS".equals(stat.getCountFieldName()))
                                        .collect(Collectors.groupingBy(
                                                ExportStatistics::getCountFieldName,
                                                Collectors.summingLong(ExportStatistics::getCountValue)
                                        ));
                            }
                        }

                        // Создаем MetricValue с процентами изменений
                        Map<String, StatisticsComparisonDto.MetricValue> metrics =
                                calculateMetrics(totalMetrics, previousTotalMetrics, request);

                        totalOperations.add(StatisticsComparisonDto.OperationStatistics.builder()
                                .exportSessionId(session.getId())
                                .operationId(session.getFileOperation().getId())
                                .operationName(operationNamesCache.get(session.getId()))
                                .exportDate(session.getStartedAt())
                                .metrics(metrics)
                                .dateModificationStats(null) // нет статистики дат для итогов
                                .build());
                    }
                });

        return StatisticsComparisonDto.builder()
                .groupFieldValue("ОБЩЕЕ КОЛИЧЕСТВО")
                .operations(totalOperations)
                .build();
    }
}