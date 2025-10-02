package com.java.service.exports;

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

/**
 * Сервис для сохранения статистики во время экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportStatisticsWriterService {

    private final ExportStatisticsRepository statisticsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Сохраняет статистику по данным экспорта
     *
     * @param session сессия экспорта
     * @param template шаблон экспорта с настройками статистики
     * @param exportedData данные, которые были экспортированы в файл
     * @param context контекст операции экспорта с дополнительными данными
     */
    @Transactional
    public void saveExportStatistics(ExportSession session, ExportTemplate template,
                                     List<Map<String, Object>> exportedData, 
                                     Map<String, Object> context) {

        // Проверяем, включена ли статистика для шаблона
        if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
            log.debug("Статистика отключена для шаблона ID: {}", template.getId());
            return;
        }

        // Получаем настройки статистики из шаблона
        List<String> countFields = parseJsonStringList(template.getStatisticsCountFields());
        String groupField = template.getStatisticsGroupField();

        if (countFields.isEmpty()) {
            log.warn("Нет полей для подсчета в шаблоне ID: {}", template.getId());
            return;
        }

        // Если поле группировки не указано, используем константу
        if (groupField == null || groupField.trim().isEmpty()) {
            groupField = "ALL";
            log.debug("Поле группировки не указано, используется 'ALL' для шаблона ID: {}", template.getId());
        }

        // Создаем маппинг entity field -> export column
        Map<String, String> fieldMapping = createFieldMapping(template);

        log.info("Сохранение статистики для сессии ID: {}, поле группировки: {}, полей для подсчета: {}",
                session.getId(), groupField, countFields.size());
        log.debug("Маппинг полей: {}", fieldMapping);
        log.debug("Доступные поля в первой строке данных: {}",
                exportedData.isEmpty() ? "нет данных" : exportedData.get(0).keySet());

        // Удаляем старую статистику для этой сессии (если есть)
        statisticsRepository.deleteByExportSessionId(session.getId());

        // Получаем название колонки группировки в экспортных данных
        String groupColumnName = getExportColumnName(groupField, fieldMapping);

        // Группируем данные по значению поля группировки
        Map<String, List<Map<String, Object>>> groupedData = groupDataByField(exportedData, groupColumnName);

        log.debug("Данные сгруппированы на {} групп по колонке '{}'", groupedData.size(), groupColumnName);

        // Получаем статистику изменений дат из контекста (если есть)
        Map<String, Integer> dateModificationsByGroup = (Map<String, Integer>) context.get("dateModificationsByGroup");
        Integer totalDateModifications = (Integer) context.get("totalDateModifications");
        Integer totalProcessedRecords = (Integer) context.get("totalProcessedRecords");
        
        // Для каждой группы подсчитываем статистику
        List<ExportStatistics> statisticsToSave = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> groupEntry : groupedData.entrySet()) {
            String groupValue = groupEntry.getKey();
            List<Map<String, Object>> groupRows = groupEntry.getValue();

            log.debug("Обработка группы '{}' с {} строками", groupValue, groupRows.size());

            // Для каждого поля подсчета вычисляем количество непустых значений
            for (String countField : countFields) {
                // Получаем название колонки в экспортных данных
                String countColumnName = getExportColumnName(countField, fieldMapping);
                long countValue = countNonEmptyValues(groupRows, countColumnName);

                ExportStatistics statistics = ExportStatistics.builder()
                        .exportSession(session)
                        .groupFieldName(groupField) // сохраняем исходное название поля
                        .groupFieldValue(groupValue)
                        .countFieldName(countField) // сохраняем исходное название поля
                        .countValue(countValue)
                        .totalRecordsCount((long) groupRows.size())
                        .dateModificationsCount(0L) // для обычной статистики всегда 0
                        .modificationType("STANDARD")
                        .build();

                statisticsToSave.add(statistics);

                log.debug("Группа '{}', поле '{}' (колонка '{}'): {} значений",
                        groupValue, countField, countColumnName, countValue);
            }
        }
        
        // Сохраняем статистику изменений дат по группам (если есть)
        if (dateModificationsByGroup != null && !dateModificationsByGroup.isEmpty()) {
            log.debug("Сохранение статистики изменений дат по {} группам", dateModificationsByGroup.size());

            for (Map.Entry<String, Integer> entry : dateModificationsByGroup.entrySet()) {
                String groupValue = entry.getKey();
                Integer modificationsCount = entry.getValue();

                if (modificationsCount != null && modificationsCount > 0) {
                    // Получаем количество записей именно в этой группе
                    List<Map<String, Object>> groupRows = groupedData.get(groupValue);
                    long groupRecordsCount = groupRows != null ? groupRows.size() : 0L;

                    ExportStatistics dateModStats = ExportStatistics.builder()
                            .exportSession(session)
                            .groupFieldName(groupField)
                            .groupFieldValue(groupValue)
                            .countFieldName("DATE_MODIFICATIONS")
                            .countValue(modificationsCount.longValue())
                            .totalRecordsCount(groupRecordsCount)
                            .dateModificationsCount(modificationsCount.longValue())
                            .modificationType("DATE_ADJUSTMENT")
                            .build();

                    statisticsToSave.add(dateModStats);

                    log.debug("Группа '{}': сохранена статистика изменений дат - {} из {} записей",
                            groupValue, modificationsCount, groupRecordsCount);
                }
            }
        }

        // Сохраняем всю статистику
        statisticsRepository.saveAll(statisticsToSave);

        log.info("Сохранено {} записей статистики для сессии ID: {}", statisticsToSave.size(), session.getId());
    }

    /**
     * Сохраняет статистику по данным экспорта (версия без контекста для обратной совместимости)
     *
     * @param session сессия экспорта
     * @param template шаблон экспорта с настройками статистики
     * @param exportedData данные, которые были экспортированы в файл
     */
    @Transactional
    public void saveExportStatistics(ExportSession session, ExportTemplate template,
                                     List<Map<String, Object>> exportedData) {
        saveExportStatistics(session, template, exportedData, new HashMap<>());
    }

    /**
     * Создает маппинг entity field name -> export column name из шаблона
     */
    private Map<String, String> createFieldMapping(ExportTemplate template) {
        Map<String, String> mapping = new HashMap<>();

        if (template.getFields() != null) {
            for (var field : template.getFields()) {
                if (Boolean.TRUE.equals(field.getIsIncluded())) {
                    mapping.put(field.getEntityFieldName(), field.getExportColumnName());
                }
            }
        }

        return mapping;
    }

    /**
     * Получает название колонки в экспортных данных для указанного поля entity
     */
    private String getExportColumnName(String entityFieldName, Map<String, String> fieldMapping) {
        if ("ALL".equals(entityFieldName)) {
            return "ALL";
        }

        // Сначала ищем в маппинге
        String exportColumnName = fieldMapping.get(entityFieldName);
        if (exportColumnName != null) {
            return exportColumnName;
        }

        // Если не найдено в маппинге, пробуем найти в данных по исходному названию
        log.warn("Поле '{}' не найдено в маппинге шаблона, используется исходное название", entityFieldName);
        return entityFieldName;
    }

    /**
     * Группирует данные по значению указанного поля
     */
    private Map<String, List<Map<String, Object>>> groupDataByField(
            List<Map<String, Object>> data, String groupField) {

        Map<String, List<Map<String, Object>>> groups = new HashMap<>();

        for (Map<String, Object> row : data) {
            // Получаем значение поля группировки
            String groupValue = getGroupValue(row, groupField);

            // Добавляем строку в соответствующую группу
            groups.computeIfAbsent(groupValue, k -> new ArrayList<>()).add(row);
        }

        return groups;
    }

    /**
     * Получает значение поля группировки из строки данных
     */
    private String getGroupValue(Map<String, Object> row, String groupColumnName) {
        if ("ALL".equals(groupColumnName)) {
            return "ALL";
        }

        Object value = row.get(groupColumnName);
        if (value == null) {
            return "NULL";
        }

        String stringValue = value.toString().trim();
        return stringValue.isEmpty() ? "EMPTY" : stringValue;
    }

    /**
     * Подсчитывает количество непустых значений для указанного поля
     */
    private long countNonEmptyValues(List<Map<String, Object>> rows, String fieldName) {
        log.debug("Подсчет значений для поля '{}' в {} строках", fieldName, rows.size());
        if (!rows.isEmpty()) {
            log.debug("Поле '{}' существует в данных: {}", fieldName, rows.get(0).containsKey(fieldName));
        }
        if (rows.isEmpty()) {
            return 0L;
        }

        // Проверяем, есть ли такое поле в данных
        boolean fieldExists = rows.get(0).containsKey(fieldName);
        if (!fieldExists) {
            log.warn("Поле '{}' не найдено в экспортных данных. Доступные поля: {}",
                    fieldName, rows.get(0).keySet());
            return 0L;
        }

        return rows.stream()
                .map(row -> row.get(fieldName))
                .filter(this::isNotEmpty)
                .count();
    }

    /**
     * Проверяет, что значение не пустое
     */
    private boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        }

        String stringValue = value.toString().trim();
        return !stringValue.isEmpty() && !"null".equalsIgnoreCase(stringValue);
    }

    /**
     * Парсит JSON строку в список строк
     */
    private List<String> parseJsonStringList(String json) {
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