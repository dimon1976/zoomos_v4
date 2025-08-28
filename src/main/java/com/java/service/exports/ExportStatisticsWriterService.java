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

        log.info("НАЧАЛО сохранения статистики для сессии ID: {}, шаблон ID: {}, записей данных: {}", 
            session.getId(), template.getId(), exportedData != null ? exportedData.size() : 0);
        log.debug("Контекст: {}", context != null ? context.keySet() : "null");

        // Проверяем, включена ли статистика для шаблона
        if (!Boolean.TRUE.equals(template.getEnableStatistics())) {
            log.warn("Статистика отключена для шаблона ID: {} ({}), пропускаем сохранение", 
                template.getId(), template.getTemplateName());
            return;
        }

        // Получаем настройки статистики из шаблона
        log.debug("Настройки статистики шаблона: statisticsCountFields='{}', statisticsGroupField='{}'", 
            template.getStatisticsCountFields(), template.getStatisticsGroupField());
            
        List<String> countFields = parseJsonStringList(template.getStatisticsCountFields());
        String groupField = template.getStatisticsGroupField();
        
        log.info("Парсинг настроек статистики: поля для подсчета: {}, поле группировки: '{}'", 
            countFields, groupField);

        if (countFields.isEmpty()) {
            log.error("КРИТИЧЕСКАЯ ОШИБКА: Нет полей для подсчета в шаблоне ID: {} ({}). " +
                "Исходные данные: '{}'", template.getId(), template.getTemplateName(), template.getStatisticsCountFields());
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
        Integer dateModificationsCount = (Integer) context.get("dateModificationsCount");
        Integer totalProcessedRecords = (Integer) context.get("totalProcessedRecords");
        
        // Получаем детализированную статистику изменений дат по группам
        @SuppressWarnings("unchecked")
        Map<String, Integer> dateModificationsByGroup = (Map<String, Integer>) context.get("dateModificationsByGroup");
        
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
                log.debug("Подсчет для поля '{}' -> колонка '{}' в группе '{}'", countField, countColumnName, groupValue);
                
                long countValue = countNonEmptyValues(groupRows, countColumnName);
                log.debug("Результат подсчета для поля '{}': {} из {} записей", countField, countValue, groupRows.size());

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

                log.debug("Создана запись статистики: Группа '{}', поле '{}' (колонка '{}'): {} значений из {} записей",
                        groupValue, countField, countColumnName, countValue, groupRows.size());
                        
                // Диагностика: если значение 0, покажем примеры данных
                if (countValue == 0 && log.isDebugEnabled() && !groupRows.isEmpty()) {
                    log.debug("ВНИМАНИЕ: countValue=0 для поля '{}'. Примеры данных в группе '{}':", countField, groupValue);
                    groupRows.stream().limit(2).forEach(row -> {
                        Object value = row.get(countColumnName);
                        log.debug("  Строка: колонка '{}' = '{}' (тип: {})", 
                            countColumnName, value, value != null ? value.getClass().getSimpleName() : "null");
                    });
                    log.debug("Доступные колонки в первой строке: {}", 
                        groupRows.isEmpty() ? "нет данных" : groupRows.get(0).keySet());
                }
            }
            
            // Если есть детализированная статистика изменений дат по группам, используем её
            Integer groupDateModifications = null;
            if (dateModificationsByGroup != null && !dateModificationsByGroup.isEmpty()) {
                groupDateModifications = dateModificationsByGroup.get(groupValue);
            }
            // Fallback к общей статистике, если детализированной нет
            else if (dateModificationsCount != null && dateModificationsCount > 0) {
                groupDateModifications = dateModificationsCount;
            }
            
            // Сохраняем статистику изменений дат для текущей группы
            if (groupDateModifications != null && groupDateModifications > 0) {
                ExportStatistics dateModStats = ExportStatistics.builder()
                        .exportSession(session)
                        .groupFieldName(groupField)
                        .groupFieldValue(groupValue)
                        .countFieldName("DATE_MODIFICATIONS") // специальное поле для изменений дат
                        .countValue(groupDateModifications.longValue())
                        .totalRecordsCount(totalProcessedRecords != null ? totalProcessedRecords.longValue() : (long) groupRows.size())
                        .dateModificationsCount(groupDateModifications.longValue())
                        .modificationType("DATE_ADJUSTMENT")
                        .build();
                        
                statisticsToSave.add(dateModStats);
                
                log.debug("Группа '{}': сохранена статистика изменений дат - {} из {} записей",
                        groupValue, groupDateModifications, totalProcessedRecords);
            }
        }

        // Сохраняем всю статистику
        try {
            List<ExportStatistics> savedStatistics = statisticsRepository.saveAll(statisticsToSave);
            log.info("Успешно сохранено {} записей статистики для сессии ID: {}", savedStatistics.size(), session.getId());
            
            // Диагностика: проверим, что статистика действительно сохранилась
            List<ExportStatistics> verificationStats = statisticsRepository.findByExportSessionId(session.getId());
            log.debug("Верификация: найдено {} записей статистики в базе для сессии ID: {}", 
                verificationStats.size(), session.getId());
            
            if (verificationStats.size() != savedStatistics.size()) {
                log.error("ОШИБКА: Количество сохраненных записей ({}) не совпадает с найденными в базе ({})", 
                    savedStatistics.size(), verificationStats.size());
            }
            
            // Логируем первые несколько записей для диагностики
            if (log.isDebugEnabled() && !verificationStats.isEmpty()) {
                log.debug("Примеры сохраненной статистики:");
                verificationStats.stream().limit(3).forEach(stat -> 
                    log.debug("  ID: {}, Группа: '{}' = '{}', Поле: '{}' = {}, Тип: '{}'", 
                        stat.getId(), stat.getGroupFieldName(), stat.getGroupFieldValue(),
                        stat.getCountFieldName(), stat.getCountValue(), stat.getModificationType()));
            }
        } catch (Exception e) {
            log.error("КРИТИЧЕСКАЯ ОШИБКА при сохранении статистики для сессии ID: {}", session.getId(), e);
            throw e; // Перебрасываем исключение, чтобы не маскировать ошибку
        }
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
        
        if (rows.isEmpty()) {
            log.debug("Строки данных пустые, возвращаем 0");
            return 0L;
        }

        // Проверяем, есть ли такое поле в данных
        boolean fieldExists = rows.get(0).containsKey(fieldName);
        log.debug("Поле '{}' существует в данных: {}", fieldName, fieldExists);
        
        if (!fieldExists) {
            log.warn("ОШИБКА: Поле '{}' не найдено в экспортных данных. Доступные поля: {}",
                    fieldName, rows.get(0).keySet());
            return 0L;
        }

        // Подсчитываем непустые значения
        long nonEmptyCount = rows.stream()
                .map(row -> row.get(fieldName))
                .filter(this::isNotEmpty)
                .count();
                
        log.debug("Поле '{}': {} непустых значений из {} всего", fieldName, nonEmptyCount, rows.size());
        
        // Диагностика: показать примеры значений
        if (log.isDebugEnabled()) {
            log.debug("Примеры значений поля '{}':", fieldName);
            rows.stream().limit(3).forEach(row -> {
                Object value = row.get(fieldName);
                boolean isEmpty = !isNotEmpty(value);
                log.debug("  '{}' -> пустое: {}", value, isEmpty);
            });
        }

        return nonEmptyCount;
    }

    /**
     * Проверяет, что значение не пустое
     */
    private boolean isNotEmpty(Object value) {
        if (value == null) {
            return false;
        }

        String stringValue = value.toString().trim();
        boolean result = !stringValue.isEmpty() && !"null".equalsIgnoreCase(stringValue);
        
        // Диагностика только для первых нескольких вызовов
        if (log.isTraceEnabled()) {
            log.trace("isNotEmpty проверка: '{}' -> {}", value, result);
        }
        
        return result;
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