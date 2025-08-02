package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Стратегия экспорта Задание-Отчет
 * Сверяет данные отчета с заданием, дополняет из справочника
 */
@Component("taskReportExportStrategy")
@Slf4j
@RequiredArgsConstructor
public class TaskReportExportStrategy implements ExportStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final DefaultExportStrategy defaultStrategy;

    @Override
    public String getName() {
        return "TASK_REPORT";
    }

    @Override
    public List<String> getRequiredContextParams() {
        return List.of("taskOperationId", "reportOperationId");
    }

    @Override
    public List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context) {

        log.info("Применение стратегии Задание-Отчет");

        // Получаем ID операций
        Long taskOperationId = (Long) context.get("taskOperationId");
        Long reportOperationId = (Long) context.get("reportOperationId");

        if (taskOperationId == null || reportOperationId == null) {
            throw new IllegalArgumentException("Не указаны операции задания и отчета");
        }

        // 1. Загружаем данные задания
        List<Map<String, Object>> taskData = loadTaskData(taskOperationId);
        log.debug("Загружено {} записей из задания", taskData.size());

        // 2. Создаем индекс задания по ключевым полям (например, productId)
        Map<String, Map<String, Object>> taskIndex = createTaskIndex(taskData);

        // 3. Обрабатываем данные отчета
        List<Map<String, Object>> processedData = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        for (Map<String, Object> reportRow : data) {
            String key = getRowKey(reportRow);
            processedKeys.add(key);

            Map<String, Object> taskRow = taskIndex.get(key);

            if (taskRow != null) {
                // Запись есть в задании - обрабатываем
                Map<String, Object> processedRow = processReportRow(reportRow, taskRow, template);

                // Дополняем данными из справочника если необходимо
                enrichWithHandbookData(processedRow);

                processedData.add(processedRow);
            } else {
                // Записи нет в задании - пропускаем или помечаем
                log.debug("Пропущена запись отчета, отсутствующая в задании: {}", key);
            }
        }

        // 4. Добавляем записи из задания, которых нет в отчете
        for (Map.Entry<String, Map<String, Object>> entry : taskIndex.entrySet()) {
            if (!processedKeys.contains(entry.getKey())) {
                Map<String, Object> missingRow = createMissingReportRow(entry.getValue(), template);
                processedData.add(missingRow);
                log.debug("Добавлена отсутствующая запись из задания: {}", entry.getKey());
            }
        }

        // 5. Применяем базовую обработку полей
        return defaultStrategy.processData(processedData, template, context);
    }

    /**
     * Загружает данные задания
     */
    private List<Map<String, Object>> loadTaskData(Long operationId) {
        String sql = "SELECT * FROM av_data WHERE operation_id = ? AND data_source = 'TASK'";
        return jdbcTemplate.queryForList(sql, operationId);
    }

    /**
     * Создает индекс задания по ключевым полям
     */
    private Map<String, Map<String, Object>> createTaskIndex(List<Map<String, Object>> taskData) {
        Map<String, Map<String, Object>> index = new HashMap<>();

        for (Map<String, Object> row : taskData) {
            String key = getRowKey(row);
            index.put(key, row);
        }

        return index;
    }

    /**
     * Получает ключ записи для сопоставления
     */
    private String getRowKey(Map<String, Object> row) {
        // Используем productId как ключ
        Object productId = row.get("product_id");
        return productId != null ? productId.toString() : "";
    }

    /**
     * Обрабатывает строку отчета с учетом данных задания
     */
    private Map<String, Object> processReportRow(
            Map<String, Object> reportRow,
            Map<String, Object> taskRow,
            ExportTemplate template) {

        Map<String, Object> processedRow = new HashMap<>(reportRow);

        // Применяем правила модификации
        // Например, проверяем и корректируем цены
        Object reportPrice = reportRow.get("competitor_price");
        Object taskPrice = taskRow.get("product_price");

        if (reportPrice != null && taskPrice != null) {
            try {
                double repPrice = Double.parseDouble(reportPrice.toString());
                double tskPrice = Double.parseDouble(taskPrice.toString());

                // Пример правила: если разница больше 50%, помечаем
                if (Math.abs(repPrice - tskPrice) / tskPrice > 0.5) {
                    processedRow.put("price_deviation_flag", "HIGH");
                }
            } catch (NumberFormatException e) {
                log.warn("Ошибка сравнения цен", e);
            }
        }

        // Корректируем даты
        if (processedRow.get("competitor_date") == null) {
            processedRow.put("competitor_date", LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
            ));
        }

        return processedRow;
    }

    /**
     * Дополняет данными из справочника
     */
    private void enrichWithHandbookData(Map<String, Object> row) {
        String competitorName = (String) row.get("competitor_name");

        if (competitorName != null) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_retail_network = ? LIMIT 1";
            List<Map<String, Object>> handbooks = jdbcTemplate.queryForList(sql, competitorName);

            if (!handbooks.isEmpty()) {
                Map<String, Object> handbook = handbooks.get(0);

                // Добавляем данные из справочника
                row.put("region", handbook.get("handbook_region_name"));
                row.put("region_address", handbook.get("handbook_physical_address"));
                row.put("price_zone", handbook.get("handbook_price_zone_code"));
            }
        }
    }

    /**
     * Создает запись для товара из задания, отсутствующего в отчете
     */
    private Map<String, Object> createMissingReportRow(
            Map<String, Object> taskRow,
            ExportTemplate template) {

        Map<String, Object> row = new HashMap<>();

        // Копируем основные поля из задания
        row.put("product_id", taskRow.get("product_id"));
        row.put("product_name", taskRow.get("product_name"));
        row.put("product_brand", taskRow.get("product_brand"));
        row.put("product_price", taskRow.get("product_price"));

        // Помечаем как отсутствующий в отчете
        row.put("competitor_stock_status", "НЕТ В НАЛИЧИИ");
        row.put("competitor_price", null);
        row.put("report_status", "MISSING");

        return row;
    }
}