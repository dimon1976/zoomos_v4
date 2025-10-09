package com.java.service.statistics;

import com.java.dto.StatisticsHistoryDto;
import com.java.model.entity.ExportStatistics;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.ExportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с историческими данными статистики
 * Предоставляет данные для построения графиков трендов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HistoricalStatisticsService {

    private final ExportStatisticsRepository statisticsRepository;
    private final ExportTemplateRepository templateRepository;
    private final TrendAnalysisService trendAnalysisService;
    private final ExportStatisticsService exportStatisticsService;

    /**
     * Получает историю значений метрики для конкретной группы
     * Результат кешируется на 5 минут
     *
     * @param templateId ID шаблона экспорта
     * @param groupValue значение группы
     * @param metricName название метрики
     * @param filterFieldName поле фильтрации (может быть null)
     * @param filterFieldValue значение фильтра (может быть null)
     * @param limit максимальное количество точек (0 = все данные)
     * @return исторические данные с трендом
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "statisticsHistory", key = "#templateId + '_' + #groupValue + '_' + #metricName + '_' + #filterFieldName + '_' + #filterFieldValue + '_' + #limit")
    public StatisticsHistoryDto getHistoryForMetric(
            Long templateId,
            String groupValue,
            String metricName,
            String filterFieldName,
            String filterFieldValue,
            int limit) {

        log.info("Получение истории для метрики: templateId={}, group={}, metric={}, filter={}={}",
                templateId, groupValue, metricName, filterFieldName, filterFieldValue);

        // Проверяем существование шаблона и получаем clientId
        ExportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден: " + templateId));

        Long clientId = template.getClient().getId();

        // Получаем исторические данные из БД
        List<ExportStatistics> history = statisticsRepository.findHistoryForMetric(
                templateId, clientId, groupValue, metricName, filterFieldName, filterFieldValue);

        log.debug("Найдено {} исторических записей", history.size());

        if (history.isEmpty()) {
            return createEmptyHistory(groupValue, metricName);
        }

        // Ограничиваем количество точек если указан лимит
        if (limit > 0 && history.size() > limit) {
            history = history.subList(0, limit);
        }

        // Преобразуем в DataPoint (данные уже отсортированы по дате DESC)
        List<StatisticsHistoryDto.DataPoint> dataPoints = history.stream()
                .map(stat -> {
                    String operationName = exportStatisticsService.generateOperationName(
                            stat.getExportSession(), template);

                    return StatisticsHistoryDto.DataPoint.builder()
                            .date(stat.getExportSession().getStartedAt())
                            .value(stat.getCountValue())
                            .exportSessionId(stat.getExportSession().getId())
                            .operationName(operationName)
                            .build();
                })
                .collect(Collectors.toList());

        // Для анализа тренда нужна сортировка от старых к новым
        List<StatisticsHistoryDto.DataPoint> dataPointsForTrend = new ArrayList<>(dataPoints);
        Collections.reverse(dataPointsForTrend);

        // Анализируем тренд
        StatisticsHistoryDto.TrendInfo trendInfo = trendAnalysisService.analyzeTrend(dataPointsForTrend);

        return StatisticsHistoryDto.builder()
                .groupValue(groupValue)
                .metricName(metricName)
                .dataPoints(dataPoints) // Возвращаем отсортированные DESC (новые первыми для графика)
                .trendInfo(trendInfo)
                .build();
    }

    /**
     * Получает историю для всех групп по одной метрике
     * Используется для построения сводных графиков
     *
     * @param templateId ID шаблона экспорта
     * @param metricName название метрики
     * @param filterFieldName поле фильтрации (может быть null)
     * @param filterFieldValue значение фильтра (может быть null)
     * @param limit максимальное количество точек на группу
     * @return список историй для каждой группы
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "statisticsHistoryAllGroups", key = "#templateId + '_' + #metricName + '_' + #filterFieldName + '_' + #filterFieldValue + '_' + #limit")
    public List<StatisticsHistoryDto> getHistoryForMetricAllGroups(
            Long templateId,
            String metricName,
            String filterFieldName,
            String filterFieldValue,
            int limit) {

        log.info("Получение истории всех групп для метрики: templateId={}, metric={}, filter={}={}",
                templateId, metricName, filterFieldName, filterFieldValue);

        // Проверяем существование шаблона и получаем clientId
        ExportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден: " + templateId));

        Long clientId = template.getClient().getId();

        // Получаем список всех групп для шаблона
        List<String> groupValues = statisticsRepository.findDistinctGroupValuesByTemplateId(templateId, clientId);
        log.debug("Найдено {} групп", groupValues.size());

        // Для каждой группы получаем историю
        return groupValues.stream()
                .map(groupValue -> getHistoryForMetric(
                        templateId, groupValue, metricName, filterFieldName, filterFieldValue, limit))
                .filter(history -> !history.getDataPoints().isEmpty()) // Исключаем пустые истории
                .collect(Collectors.toList());
    }

    /**
     * Получает список доступных метрик для шаблона
     *
     * @param templateId ID шаблона экспорта
     * @return список названий метрик
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "statisticsMetrics", key = "#templateId")
    public List<String> getAvailableMetrics(Long templateId) {
        ExportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден: " + templateId));

        return statisticsRepository.findDistinctMetricNamesByTemplateId(templateId, template.getClient().getId());
    }

    /**
     * Получает список доступных групп для шаблона
     *
     * @param templateId ID шаблона экспорта
     * @return список значений групп
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "statisticsGroups", key = "#templateId")
    public List<String> getAvailableGroups(Long templateId) {
        ExportTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден: " + templateId));

        return statisticsRepository.findDistinctGroupValuesByTemplateId(templateId, template.getClient().getId());
    }

    /**
     * Создает пустую историю (для случаев когда данных нет)
     */
    private StatisticsHistoryDto createEmptyHistory(String groupValue, String metricName) {
        return StatisticsHistoryDto.builder()
                .groupValue(groupValue)
                .metricName(metricName)
                .dataPoints(Collections.emptyList())
                .trendInfo(StatisticsHistoryDto.TrendInfo.builder()
                        .direction(StatisticsHistoryDto.TrendDirection.STABLE)
                        .slope(0.0)
                        .confidence(0.0)
                        .changePercentage(0.0)
                        .description("Нет данных")
                        .build())
                .build();
    }
}
