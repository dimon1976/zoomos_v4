package com.java.service.statistics;

import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsRequestDto;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportStatisticsService {

    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final ExportStatisticsRepository statisticsRepository;
    private final StatisticsSettingsService settingsService;

    /**
     * Вычисляет статистику для выбранных операций экспорта
     */
    public List<StatisticsComparisonDto> calculateComparison(StatisticsRequestDto request) {
        log.info("Расчет статистики для операций: {}", request.getExportSessionIds());

        // Получаем шаблон с настройками статистики
        ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(request.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
            throw new IllegalArgumentException("Статистика не включена для данного шаблона");
        }

        // Получаем сессии экспорта
        List<ExportSession> sessions = getExportSessions(request.getExportSessionIds(), template);

        if (sessions.isEmpty()) {
            log.warn("Нет сессий для анализа");
            return new ArrayList<>();
        }

        // Получаем ID сессий
        List<Long> sessionIds = sessions.stream()
                .map(ExportSession::getId)
                .collect(Collectors.toList());

        // Загружаем статистику из БД
        List<ExportStatistics> statistics = statisticsRepository.findBySessionIds(sessionIds);

        if (statistics.isEmpty()) {
            log.warn("Нет сохраненной статистики для выбранных сессий");
            return new ArrayList<>();
        }

        // Группируем статистику по группам и сессиям
        Map<String, Map<Long, List<ExportStatistics>>> groupedStats = statistics.stream()
                .collect(Collectors.groupingBy(
                        ExportStatistics::getGroupFieldValue,
                        Collectors.groupingBy(ExportStatistics::getExportSessionId)
                ));

        // Создаем карту сессий для быстрого доступа
        Map<Long, ExportSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ExportSession::getId, s -> s));

        // Создаем сравнительную статистику
        List<StatisticsComparisonDto> comparisons = new ArrayList<>();

        for (Map.Entry<String, Map<Long, List<ExportStatistics>>> groupEntry : groupedStats.entrySet()) {
            String groupValue = groupEntry.getKey();
            Map<Long, List<ExportStatistics>> sessionStats = groupEntry.getValue();

            // Создаем список операций для группы
            List<StatisticsComparisonDto.OperationStatistics> operations = new ArrayList<>();

            // Сортируем сессии по дате (новые первыми)
            List<Long> sortedSessionIds = sessionStats.keySet().stream()
                    .sorted((id1, id2) -> {
                        ExportSession s1 = sessionMap.get(id1);
                        ExportSession s2 = sessionMap.get(id2);
                        return s2.getStartedAt().compareTo(s1.getStartedAt());
                    })
                    .toList();

            for (int i = 0; i < sortedSessionIds.size(); i++) {
                Long sessionId = sortedSessionIds.get(i);
                ExportSession session = sessionMap.get(sessionId);
                List<ExportStatistics> sessionStatsList = sessionStats.get(sessionId);

                // Собираем метрики для сессии
                Map<String, Long> currentMetrics = sessionStatsList.stream()
                        .collect(Collectors.toMap(
                                ExportStatistics::getCountFieldName,
                                ExportStatistics::getCountValue,
                                (v1, v2) -> v1 // При дубликатах берем первое
                        ));

                // Получаем предыдущие метрики если есть
                Map<String, Long> previousMetrics = null;
                if (i + 1 < sortedSessionIds.size()) {
                    Long prevSessionId = sortedSessionIds.get(i + 1);
                    List<ExportStatistics> prevStats = sessionStats.get(prevSessionId);
                    previousMetrics = prevStats.stream()
                            .collect(Collectors.toMap(
                                    ExportStatistics::getCountFieldName,
                                    ExportStatistics::getCountValue,
                                    (v1, v2) -> v1
                            ));
                }

                // Вычисляем метрики с отклонениями
                Map<String, StatisticsComparisonDto.MetricValue> metrics =
                        calculateMetrics(currentMetrics, previousMetrics, request);

                operations.add(StatisticsComparisonDto.OperationStatistics.builder()
                        .exportSessionId(sessionId)
                        .operationName("Экспорт #" + sessionId)
                        .exportDate(session.getStartedAt())
                        .metrics(metrics)
                        .build());
            }

            comparisons.add(StatisticsComparisonDto.builder()
                    .groupFieldValue(groupValue)
                    .operations(operations)
                    .build());
        }

        // Применяем дополнительные фильтры если указаны
        if (request.getAdditionalFilters() != null && !request.getAdditionalFilters().isEmpty()) {
            log.debug("Применение дополнительных фильтров: {}", request.getAdditionalFilters());
            // Фильтры можно применить здесь при необходимости
        }

        return comparisons;
    }

    /**
     * Вычисляет метрики с отклонениями
     */
    private Map<String, StatisticsComparisonDto.MetricValue> calculateMetrics(
            Map<String, Long> currentMetrics,
            Map<String, Long> previousMetrics,
            StatisticsRequestDto request) {

        Map<String, StatisticsComparisonDto.MetricValue> result = new HashMap<>();

        for (Map.Entry<String, Long> entry : currentMetrics.entrySet()) {
            String fieldName = entry.getKey();
            Long currentValue = entry.getValue();
            Long previousValue = previousMetrics != null ? previousMetrics.get(fieldName) : null;

            StatisticsComparisonDto.MetricValue metric = calculateMetricValue(
                    currentValue, previousValue,
                    request.getWarningPercentage(),
                    request.getCriticalPercentage());

            result.put(fieldName, metric);
        }

        return result;
    }

    /**
     * Вычисляет значение метрики с отклонением
     */
    private StatisticsComparisonDto.MetricValue calculateMetricValue(
            Long currentValue, Long previousValue,
            Integer warningPercentage, Integer criticalPercentage) {

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
     * Определяет уровень предупреждения
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

    /**
     * Получает сессии экспорта
     */
    private List<ExportSession> getExportSessions(List<Long> sessionIds, ExportTemplate template) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            // Если не выбраны конкретные сессии, берем последние для шаблона
            return sessionRepository.findByTemplate(template,
                            org.springframework.data.domain.PageRequest.of(0, settingsService.getMaxOperations(),
                                    org.springframework.data.domain.Sort.by(
                                            org.springframework.data.domain.Sort.Direction.DESC, "startedAt")))
                    .getContent();
        } else {
            return sessionRepository.findAllById(sessionIds);
        }
    }
}