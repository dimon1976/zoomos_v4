package com.java.service.exports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.ExportStatisticsConfigDto;
import com.java.dto.ExportStatisticsDto;
import com.java.dto.ExportStatisticsDto.*;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatisticsCache;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateStatistics;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsCacheRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportStatisticsService {

    private final ExportStatisticsRepository statisticsRepository;
    private final ExportStatisticsCacheRepository cacheRepository;
    private final ExportSessionRepository sessionRepository;
    private final ExportTemplateRepository templateRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Сохраняет или обновляет настройки статистики для шаблона
     */
    @Transactional
    public ExportStatisticsConfigDto saveStatisticsConfig(ExportStatisticsConfigDto dto) {
        log.info("Сохранение настроек статистики для шаблона ID: {}", dto.getTemplateId());

        ExportTemplate template = templateRepository.findById(dto.getTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

        ExportTemplateStatistics entity = statisticsRepository.findByTemplateId(dto.getTemplateId())
                .orElse(ExportTemplateStatistics.builder()
                        .template(template)
                        .build());

        // Обновляем поля
        entity.setMetricFields(toJson(dto.getMetricFields()));
        entity.setGroupByField(dto.getGroupByField());
        entity.setFilterField(dto.getFilterField());
        entity.setFilterValues(toJson(dto.getFilterValues()));
        entity.setDeviationThresholdPercent(dto.getDeviationThresholdPercent());
        entity.setIsEnabled(dto.getIsEnabled());

        entity = statisticsRepository.save(entity);

        return toConfigDto(entity);
    }

    /**
     * Получает настройки статистики для шаблона
     */
    @Transactional(readOnly = true)
    public Optional<ExportStatisticsConfigDto> getStatisticsConfig(Long templateId) {
        return statisticsRepository.findByTemplateId(templateId)
                .map(this::toConfigDto);
    }

    /**
     * Рассчитывает статистику после экспорта
     */
    @Transactional
    public void calculateStatistics(Long exportSessionId) {
        log.info("Расчет статистики для сессии экспорта ID: {}", exportSessionId);

        ExportSession session = sessionRepository.findById(exportSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        ExportTemplateStatistics config = statisticsRepository.findByTemplateId(session.getTemplate().getId())
                .orElse(null);

        if (config == null || !config.getIsEnabled()) {
            log.debug("Статистика не настроена или отключена для шаблона");
            return;
        }

        // Удаляем старый кэш
        cacheRepository.deleteByExportSessionId(exportSessionId);

        // Загружаем данные из файла экспорта
        List<Map<String, Object>> data = loadExportData(session.getResultFilePath());

        if (data.isEmpty()) {
            log.warn("Нет данных для расчета статистики");
            return;
        }

        // Рассчитываем статистику
        Map<String, Map<String, Long>> statistics = calculateMetrics(
                data,
                parseJsonArray(config.getMetricFields()),
                config.getGroupByField());

        // Сохраняем в кэш
        saveToCache(session, statistics, config.getGroupByField(), false);

        log.info("Статистика рассчитана: {} групп", statistics.size());
    }

    /**
     * Сравнивает статистику между операциями экспорта
     */
    @Transactional(readOnly = true)
    public ExportStatisticsDto compareExports(List<Long> sessionIds, Long templateId, boolean applyFilter) {
        log.info("Сравнение экспортов: {}", sessionIds);

        // Если не указаны операции, берем все для шаблона
        if (sessionIds == null || sessionIds.isEmpty()) {
            sessionIds = sessionRepository.findByTemplateIdOrderByStartedAtDesc(templateId)
                    .stream()
                    .map(ExportSession::getId)
                    .collect(Collectors.toList());
        }

        if (sessionIds.isEmpty()) {
            return ExportStatisticsDto.builder().build();
        }

        // Получаем настройки
        ExportStatisticsConfigDto config = getStatisticsConfig(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Статистика не настроена"));

        // Загружаем кэш
        List<ExportStatisticsCache> cacheData = cacheRepository.findByExportSessionIds(sessionIds);

        // Если нужно применить фильтр и его еще нет в кэше
        if (applyFilter && config.getFilterField() != null) {
            cacheData = applyFilterToCache(cacheData, config);
        }

        // Формируем результат
        return buildComparisonResult(cacheData, sessionIds, config);
    }

    /**
     * Рассчитывает метрики
     */
    private Map<String, Map<String, Long>> calculateMetrics(
            List<Map<String, Object>> data,
            List<String> metricFields,
            String groupByField) {

        Map<String, Map<String, Long>> result = new HashMap<>();

        for (Map<String, Object> row : data) {
            String groupValue = Objects.toString(row.get(groupByField), "NULL");

            if ("NULL".equals(groupValue) || groupValue.trim().isEmpty()) {
                continue; // Пропускаем NULL значения
            }

            Map<String, Long> groupMetrics = result.computeIfAbsent(groupValue, k -> new HashMap<>());

            for (String metricField : metricFields) {
                Object value = row.get(metricField);
                if (value != null && !value.toString().trim().isEmpty()) {
                    String metricKey = metricField + "_count";
                    groupMetrics.merge(metricKey, 1L, Long::sum);
                }
            }
        }

        return result;
    }

    /**
     * Загружает данные из файла экспорта
     */
    private List<Map<String, Object>> loadExportData(String filePath) {
        if (filePath == null) {
            return Collections.emptyList();
        }

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.error("Файл экспорта не найден: {}", filePath);
                return Collections.emptyList();
            }

            // Простая загрузка CSV (расширить при необходимости)
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                return Collections.emptyList();
            }

            String[] headers = lines.get(0).split(";");
            List<Map<String, Object>> data = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i).split(";", -1);
                Map<String, Object> row = new HashMap<>();

                for (int j = 0; j < Math.min(headers.length, values.length); j++) {
                    row.put(headers[j], values[j]);
                }

                data.add(row);
            }

            return data;

        } catch (IOException e) {
            log.error("Ошибка чтения файла экспорта", e);
            return Collections.emptyList();
        }
    }

    /**
     * Сохраняет статистику в кэш
     */
    private void saveToCache(ExportSession session,
                             Map<String, Map<String, Long>> statistics,
                             String groupByField,
                             boolean filterApplied) {

        for (Map.Entry<String, Map<String, Long>> entry : statistics.entrySet()) {
            ExportStatisticsCache cache = ExportStatisticsCache.builder()
                    .exportSession(session)
                    .groupKey(groupByField)
                    .groupValue(entry.getKey())
                    .metrics(toJson(entry.getValue()))
                    .filterApplied(filterApplied)
                    .build();

            cacheRepository.save(cache);
        }
    }

    /**
     * Применяет фильтр к кэшированным данным
     */
    private List<ExportStatisticsCache> applyFilterToCache(
            List<ExportStatisticsCache> cacheData,
            ExportStatisticsConfigDto config) {

        // Здесь нужно перезагрузить и отфильтровать данные
        // Для упрощения возвращаем как есть
        return cacheData;
    }

    /**
     * Формирует результат сравнения
     */
    private ExportStatisticsDto buildComparisonResult(
            List<ExportStatisticsCache> cacheData,
            List<Long> sessionIds,
            ExportStatisticsConfigDto config) {

        // Получаем информацию об операциях
        List<ExportOperationInfo> operations = sessionIds.stream()
                .map(id -> sessionRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(session -> ExportOperationInfo.builder()
                        .sessionId(session.getId())
                        .fileName(session.getFileOperation().getFileName())
                        .exportDate(session.getStartedAt())
                        .totalRows(session.getExportedRows())
                        .build())
                .collect(Collectors.toList());

        // Группируем данные
        Map<String, List<ExportStatisticsCache>> groupedData = cacheData.stream()
                .collect(Collectors.groupingBy(ExportStatisticsCache::getGroupValue));

        // Формируем строки статистики
        List<StatisticsRow> rows = new ArrayList<>();

        for (Map.Entry<String, List<ExportStatisticsCache>> entry : groupedData.entrySet()) {
            StatisticsRow row = StatisticsRow.builder()
                    .groupValue(entry.getKey())
                    .metrics(new ArrayList<>())
                    .build();

            // Сортируем по дате убывания
            List<ExportStatisticsCache> sortedCache = entry.getValue().stream()
                    .sorted((a, b) -> b.getExportSession().getStartedAt()
                            .compareTo(a.getExportSession().getStartedAt()))
                    .collect(Collectors.toList());

            Long previousCount = null;

            for (ExportStatisticsCache cache : sortedCache) {
                Map<String, Long> metrics = parseJsonMap(cache.getMetrics());

                // Берем первую метрику для упрощения
                Long count = metrics.values().stream().findFirst().orElse(0L);

                StatisticsRow.MetricValue metricValue = StatisticsRow.MetricValue.builder()
                        .sessionId(cache.getExportSession().getId())
                        .count(count)
                        .build();

                // Рассчитываем изменение
                if (previousCount != null) {
                    double change = ((double)(previousCount - count) / previousCount) * 100;
                    metricValue.setChangePercent(change);

                    if (Math.abs(change) < 0.01) {
                        metricValue.setChangeType(ChangeType.NO_CHANGE);
                    } else if (change > 0) {
                        metricValue.setChangeType(ChangeType.DECREASE);
                    } else {
                        metricValue.setChangeType(ChangeType.INCREASE);
                    }
                } else {
                    metricValue.setChangeType(ChangeType.NO_DATA);
                }

                row.getMetrics().add(metricValue);
                previousCount = count;
            }

            rows.add(row);
        }

        return ExportStatisticsDto.builder()
                .operations(operations)
                .rows(rows)
                .deviationThreshold(config.getDeviationThresholdPercent())
                .groupByField(config.getGroupByField())
                .metricFields(config.getMetricFields())
                .filterApplied(false)
                .build();
    }

    // Вспомогательные методы для JSON

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Ошибка сериализации в JSON", e);
            return "{}";
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON массива", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Long> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON map", e);
            return Collections.emptyMap();
        }
    }

    private ExportStatisticsConfigDto toConfigDto(ExportTemplateStatistics entity) {
        return ExportStatisticsConfigDto.builder()
                .id(entity.getId())
                .templateId(entity.getTemplate().getId())
                .metricFields(parseJsonArray(entity.getMetricFields()))
                .groupByField(entity.getGroupByField())
                .filterField(entity.getFilterField())
                .filterValues(parseJsonArray(entity.getFilterValues()))
                .deviationThresholdPercent(entity.getDeviationThresholdPercent())
                .isEnabled(entity.getIsEnabled())
                .build();
    }
}