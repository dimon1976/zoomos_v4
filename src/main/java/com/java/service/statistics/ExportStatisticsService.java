// src/main/java/com/java/service/statistics/ExportStatisticsService.java
package com.java.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.*;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportSessionRepository;
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

    private final JdbcTemplate jdbcTemplate;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final StatisticsSettingsService settingsService;
    private final ObjectMapper objectMapper;

    /**
     * Вычисляет статистику для выбранных операций экспорта
     */
    public List<StatisticsComparisonDto> calculateComparison(StatisticsRequestDto request) {
        log.info("Расчет статистики для операций: {}", request.getExportSessionIds());

        // Получаем шаблон с настройками статистики
        ExportTemplate template = templateRepository.findByIdWithFieldsAndFilters(request.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        ExportStatisticsSettingsDto settings = parseStatisticsSettings(template);
        if (!Boolean.TRUE.equals(settings.getEnableStatistics())) {
            throw new IllegalArgumentException("Статистика не включена для данного шаблона");
        }

        // Получаем сессии экспорта
        List<ExportSession> sessions = getExportSessions(request.getExportSessionIds(), template);

        // Вычисляем статистику для каждой сессии
        Map<String, List<StatisticsResultDto>> statisticsByGroup = new HashMap<>();

        for (ExportSession session : sessions) {
            List<StatisticsResultDto> sessionStats = calculateSessionStatistics(session, settings, request.getAdditionalFilters());

            // Группируем по значению группировки
            for (StatisticsResultDto stat : sessionStats) {
                statisticsByGroup.computeIfAbsent(stat.getGroupFieldValue(), k -> new ArrayList<>()).add(stat);
            }
        }

        // Создаем сравнительную статистику
        return createComparison(statisticsByGroup, request);
    }

    /**
     * Получает статистику для одной сессии экспорта
     */
    private List<StatisticsResultDto> calculateSessionStatistics(
            ExportSession session,
            ExportStatisticsSettingsDto settings,
            Map<String, String> additionalFilters) {

        log.debug("Расчет статистики для сессии: {}", session.getId());

        // Определяем таблицу для запроса на основе типа сущности
        String tableName = getTableName(session.getTemplate().getEntityType());

        // Получаем операции-источники
        List<Long> sourceOperationIds = parseSourceOperationIds(session.getSourceOperationIds());

        // Строим SQL запрос
        String sql = buildStatisticsQuery(tableName, settings, sourceOperationIds, additionalFilters);

        // Выполняем запрос
        List<Map<String, Object>> rawResults = jdbcTemplate.queryForList(sql, sourceOperationIds.toArray());

        // Преобразуем результаты
        return rawResults.stream()
                .map(row -> convertToStatisticsResult(row, session, settings))
                .collect(Collectors.toList());
    }

    /**
     * Строит SQL запрос для подсчета статистики
     */
    private String buildStatisticsQuery(String tableName, ExportStatisticsSettingsDto settings,
                                        List<Long> sourceOperationIds, Map<String, String> additionalFilters) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(toSnakeCase(settings.getGroupField())).append(" as group_value, ");

        // Добавляем COUNT для каждого поля
        for (String countField : settings.getCountFields()) {
            String snakeField = toSnakeCase(countField);
            sql.append("COUNT(CASE WHEN ").append(snakeField).append(" IS NOT NULL AND ")
                    .append(snakeField).append(" != '' THEN 1 END) as ").append(snakeField).append("_count, ");
        }

        // Убираем последнюю запятую
        sql.setLength(sql.length() - 2);
        sql.append(" FROM ").append(tableName);
        sql.append(" WHERE 1=1");

        // Фильтр по операциям-источникам
        if (!sourceOperationIds.isEmpty()) {
            sql.append(" AND operation_id IN (");
            sql.append(sourceOperationIds.stream().map(id -> "?").collect(Collectors.joining(", ")));
            sql.append(")");
        }

        // Применяем дополнительные фильтры
        if (additionalFilters != null) {
            for (Map.Entry<String, String> filter : additionalFilters.entrySet()) {
                sql.append(" AND ").append(toSnakeCase(filter.getKey())).append(" = '").append(filter.getValue()).append("'");
            }
        }

        // Группировка
        sql.append(" GROUP BY ").append(toSnakeCase(settings.getGroupField()));
        sql.append(" ORDER BY ").append(toSnakeCase(settings.getGroupField()));

        log.debug("Построенный SQL: {}", sql.toString());
        return sql.toString();
    }

    /**
     * Создает сравнительную статистику
     */
    private List<StatisticsComparisonDto> createComparison(
            Map<String, List<StatisticsResultDto>> statisticsByGroup,
            StatisticsRequestDto request) {

        List<StatisticsComparisonDto> comparisons = new ArrayList<>();

        for (Map.Entry<String, List<StatisticsResultDto>> entry : statisticsByGroup.entrySet()) {
            String groupValue = entry.getKey();
            List<StatisticsResultDto> groupStats = entry.getValue();

            // Сортируем по дате (новые первыми)
            groupStats.sort((a, b) -> b.getExportDate().compareTo(a.getExportDate()));

            // Создаем статистику по операциям
            List<StatisticsComparisonDto.OperationStatistics> operationStats = new ArrayList<>();

            for (int i = 0; i < groupStats.size(); i++) {
                StatisticsResultDto current = groupStats.get(i);
                StatisticsResultDto previous = i + 1 < groupStats.size() ? groupStats.get(i + 1) : null;

                Map<String, StatisticsComparisonDto.MetricValue> metrics = calculateMetrics(current, previous, request);

                operationStats.add(StatisticsComparisonDto.OperationStatistics.builder()
                        .exportSessionId(current.getExportSessionId())
                        .operationName(current.getExportSessionName())
                        .exportDate(current.getExportDate())
                        .metrics(metrics)
                        .build());
            }

            comparisons.add(StatisticsComparisonDto.builder()
                    .groupFieldValue(groupValue)
                    .operations(operationStats)
                    .build());
        }

        return comparisons;
    }

    /**
     * Вычисляет метрики с отклонениями
     */
    private Map<String, StatisticsComparisonDto.MetricValue> calculateMetrics(
            StatisticsResultDto current,
            StatisticsResultDto previous,
            StatisticsRequestDto request) {

        Map<String, StatisticsComparisonDto.MetricValue> metrics = new HashMap<>();

        for (Map.Entry<String, Long> entry : current.getCountValues().entrySet()) {
            String fieldName = entry.getKey();
            Long currentValue = entry.getValue();
            Long previousValue = previous != null ? previous.getCountValues().get(fieldName) : null;

            StatisticsComparisonDto.MetricValue metric = calculateMetricValue(
                    currentValue, previousValue, request.getWarningPercentage(), request.getCriticalPercentage());

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

    private List<ExportSession> getExportSessions(List<Long> sessionIds, ExportTemplate template) {
        if (sessionIds.isEmpty()) {
            // Если не выбраны конкретные сессии, берем все для данного шаблона
            return sessionRepository.findByTemplate(template, org.springframework.data.domain.Pageable.unpaged())
                    .getContent();
        } else {
            return sessionRepository.findAllById(sessionIds);
        }
    }

    private ExportStatisticsSettingsDto parseStatisticsSettings(ExportTemplate template) {
        try {
            return ExportStatisticsSettingsDto.builder()
                    .enableStatistics(Boolean.TRUE.equals(template.getEnableStatistics()))
                    .countFields(parseJsonList(template.getStatisticsCountFields()))
                    .groupField(template.getStatisticsGroupField())
                    .filterFields(parseJsonList(template.getStatisticsFilterFields()))
                    .build();
        } catch (Exception e) {
            log.error("Ошибка парсинга настроек статистики для шаблона ID: {}", template.getId(), e);
            return ExportStatisticsSettingsDto.builder().enableStatistics(false).build();
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON списка: {}", json, e);
            return new ArrayList<>();
        }
    }

    private List<Long> parseSourceOperationIds(String sourceOperationIds) {
        if (sourceOperationIds == null || sourceOperationIds.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(sourceOperationIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга операций-источников: {}", sourceOperationIds, e);
            return new ArrayList<>();
        }
    }

    private String getTableName(com.java.model.enums.EntityType entityType) {
        return switch (entityType) {
            case AV_DATA -> "av_data";
            case AV_HANDBOOK -> "av_handbook";
        };
    }

    private StatisticsResultDto convertToStatisticsResult(Map<String, Object> row, ExportSession session,
                                                          ExportStatisticsSettingsDto settings) {
        String groupValue = (String) row.get("group_value");
        Map<String, Long> countValues = new HashMap<>();

        for (String countField : settings.getCountFields()) {
            String snakeField = toSnakeCase(countField) + "_count";
            Long count = ((Number) row.get(snakeField)).longValue();
            countValues.put(countField, count);
        }

        return StatisticsResultDto.builder()
                .exportSessionId(session.getId())
                .exportSessionName("Экспорт " + session.getId())
                .exportDate(session.getStartedAt())
                .groupFieldValue(groupValue)
                .countValues(countValues)
                .build();
    }

    private String toSnakeCase(String value) {
        if (value == null) return null;
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}