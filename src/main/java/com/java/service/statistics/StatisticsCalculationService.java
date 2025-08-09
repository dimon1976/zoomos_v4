package com.java.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportStatistics;
import com.java.model.entity.ExportTemplate;
import com.java.repository.ExportStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для расчета и сохранения статистики при экспорте
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsCalculationService {

    private final ExportStatisticsRepository statisticsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Рассчитывает и сохраняет статистику для экспортированных данных
     */
    @Transactional
    public void calculateAndSaveStatistics(
            ExportSession session,
            List<Map<String, Object>> exportedData) {

        log.info("Начало расчета статистики для сессии экспорта ID: {}", session.getId());

        ExportTemplate template = session.getTemplate();

        // Проверяем, включена ли статистика
        if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
            log.debug("Статистика отключена для шаблона {}", template.getName());
            return;
        }

        // Получаем настройки статистики
        String groupField = template.getStatisticsGroupField();
        List<String> countFields = parseJsonList(template.getStatisticsCountFields());

        if (groupField == null || countFields.isEmpty()) {
            log.warn("Некорректные настройки статистики: groupField={}, countFields={}",
                    groupField, countFields);
            return;
        }

        // Удаляем старую статистику если есть (на случай повторного расчета)
        statisticsRepository.deleteByExportSessionId(session.getId());

        // Группируем данные
        Map<String, List<Map<String, Object>>> groupedData = exportedData.stream()
                .collect(Collectors.groupingBy(
                        row -> String.valueOf(row.getOrDefault(groupField, "NULL"))
                ));

        log.debug("Данные сгруппированы по полю '{}', найдено {} групп",
                groupField, groupedData.size());

        // Список для batch insert
        List<ExportStatistics> statisticsToSave = new ArrayList<>();

        // Для каждой группы
        for (Map.Entry<String, List<Map<String, Object>>> groupEntry : groupedData.entrySet()) {
            String groupValue = groupEntry.getKey();
            List<Map<String, Object>> groupRows = groupEntry.getValue();

            // Для каждого поля подсчета
            for (String countField : countFields) {
                // Считаем непустые значения
                long count = groupRows.stream()
                        .map(row -> row.get(countField))
                        .filter(this::isNotEmpty)
                        .count();

                // Создаем запись статистики
                ExportStatistics stat = ExportStatistics.builder()
                        .exportSessionId(session.getId())
                        .groupFieldName(groupField)
                        .groupFieldValue(groupValue)
                        .countFieldName(countField)
                        .countValue(count)
                        .filterConditions(null) // Фильтры не применяем при сохранении
                        .build();

                statisticsToSave.add(stat);

                log.trace("Статистика: группа='{}', поле='{}', количество={}",
                        groupValue, countField, count);
            }
        }

        // Сохраняем все записи
        if (!statisticsToSave.isEmpty()) {
            statisticsRepository.saveAll(statisticsToSave);
            log.info("Сохранено {} записей статистики для сессии {}",
                    statisticsToSave.size(), session.getId());
        } else {
            log.warn("Нет данных статистики для сохранения");
        }
    }

    /**
     * Проверяет, не пустое ли значение
     */
    private boolean isNotEmpty(Object value) {
        if (value == null) return false;
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        return true;
    }

    /**
     * Парсит JSON список строк
     */
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
}