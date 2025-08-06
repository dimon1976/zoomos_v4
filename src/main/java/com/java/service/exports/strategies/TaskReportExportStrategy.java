package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public String getName() {
        return "TASK_REPORT";
    }

    @Override
    public List<String> getRequiredContextParams() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context) {

        log.info("Применение стратегии Задание-Отчет");

        // Получаем ID операций
        Long taskOperationId = (Long) context.get("taskOperationId");
        String clientRegionCode = (String) context.get("clientRegionCode");
        int maxReportAgeDays = context.get("maxReportAgeDays") != null
                ? ((Integer) context.get("maxReportAgeDays"))
                : 3;

        // 1. Загружаем данные задания
        List<Map<String, Object>> taskData;
        if (taskOperationId != null) {
            taskData = loadTaskData(taskOperationId);
        } else {
            Set<String> taskNumbers = data.stream()
                    .map(this::getRowKey)
                    .filter(key -> key != null && !key.isBlank())
                    .collect(Collectors.toSet());

            if (taskNumbers.isEmpty()) {
                throw new IllegalArgumentException("Не указаны операции задания и отсутствуют номера заданий");
            }

            taskData = loadTaskDataByNumbers(taskNumbers);
        }
        log.debug("Загружено {} записей из задания", taskData.size());

        // 2. Создаем индекс задания по номеру задания
        Map<String, Map<String, Object>> taskIndex = createTaskIndex(taskData);

        // 3. Обрабатываем данные отчета
        List<Map<String, Object>> processedData = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        for (Map<String, Object> reportRow : data) {
            String key = getRowKey(reportRow);

            Map<String, Object> taskRow = taskIndex.get(key);

            if (taskRow != null) {
                Object retailerCode = taskRow.get("product_additional4");
                if (retailerCode != null && !retailerCode.toString().isBlank()) {
                    processedKeys.add(key);
                    Map<String, Object> processedRow = processReportRow(reportRow, taskRow, maxReportAgeDays);
                    enrichWithHandbookData(processedRow, clientRegionCode);
                    processedData.add(processedRow);
                } else {
                    log.debug("Пропущена запись отчета без кода сети: {}", key);
                }
            } else {
                log.debug("Пропущена запись отчета, отсутствующая в задании: {}", key);
            }
        }

        // 4. Добавляем записи из задания, которых нет в отчете
        for (Map.Entry<String, Map<String, Object>> entry : taskIndex.entrySet()) {
            if (!processedKeys.contains(entry.getKey())) {
                Map<String, Object> taskRow = entry.getValue();
                Object retailerCode = taskRow.get("product_additional4");
                if (retailerCode != null && !retailerCode.toString().isBlank()) {
                    Map<String, Object> missingRow = createMissingReportRow(taskRow, template);
                    processedData.add(missingRow);
                    log.debug("Добавлена отсутствующая запись из задания: {}", entry.getKey());
                }
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
     * Загружает данные задания по номерам задания
     */
    private List<Map<String, Object>> loadTaskDataByNumbers(Set<String> taskNumbers) {
        if (taskNumbers == null || taskNumbers.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(", ", Collections.nCopies(taskNumbers.size(), "?"));
        String sql = "SELECT * FROM av_data WHERE data_source = 'TASK' AND product_additional1 IN (" + placeholders + ")";
        return jdbcTemplate.queryForList(sql, taskNumbers.toArray());
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
        Object taskNumber = row.get("product_additional1");
        return taskNumber != null ? taskNumber.toString() : "";
    }

    /**
     * Обрабатывает строку отчета с учетом данных задания
     */
    private Map<String, Object> processReportRow(
            Map<String, Object> reportRow,
            Map<String, Object> taskRow,
            int maxReportAgeDays) {

        Map<String, Object> processedRow = new HashMap<>(reportRow);

        processedRow.put("product_additional1", taskRow.get("product_additional1"));
        processedRow.put("product_additional4", taskRow.get("product_additional4"));

        String dateStr = (String) processedRow.get("competitor_date");
        if (dateStr == null) {
            processedRow.put("competitor_date", LocalDate.now().format(DATE_FORMAT));
        } else {
            try {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
                LocalDate minDate = LocalDate.now().minusDays(maxReportAgeDays - 1L);
                if (date.isBefore(minDate)) {
                    processedRow.put("competitor_date", minDate.format(DATE_FORMAT));
                }
            } catch (DateTimeParseException e) {
                log.warn("Некорректная дата отчета: {}", dateStr);
            }
        }
        return processedRow;
    }

    /**
     * Дополняет данными из справочника
     */
    private void enrichWithHandbookData(Map<String, Object> row, String clientRegionCode) {
        String competitorUrl = (String) row.get("competitor_url");
        if (competitorUrl == null || !competitorUrl.contains("market.yandex.ru")) {
            return;
        }

        boolean need = isBlank(row.get("product_additional4")) ||
                isBlank(row.get("competitor_name")) ||
                isBlank(row.get("region")) ||
                isBlank(row.get("region_address"));

        if (!need) {
            return;
        }
        String normalizedRegion = normalizeRegionCode(clientRegionCode);
        String altRegion = null;
        if (normalizedRegion != null) {
            altRegion = normalizedRegion.length() == 2 ? "0" + normalizedRegion
                    : (normalizedRegion.length() == 3 && normalizedRegion.startsWith("0")
                    ? normalizedRegion.substring(1) : null);
        }

        List<Map<String, Object>> handbooks;
        if (normalizedRegion != null && altRegion != null) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? AND handbook_region_code IN (?, ?) LIMIT 1";
            handbooks = jdbcTemplate.queryForList(sql, "market.yandex.ru", normalizedRegion, altRegion);
        } else if (normalizedRegion != null) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? AND handbook_region_code = ? LIMIT 1";
            handbooks = jdbcTemplate.queryForList(sql, "market.yandex.ru", normalizedRegion);
        } else {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? LIMIT 1";
            handbooks = jdbcTemplate.queryForList(sql, "market.yandex.ru");
        }

        if (!handbooks.isEmpty()) {
            Map<String, Object> handbook = handbooks.get(0);
            row.putIfAbsent("product_additional4", handbook.get("handbook_retail_network_code"));
            row.putIfAbsent("competitor_name", handbook.get("handbook_retail_network"));
            row.putIfAbsent("region", handbook.get("handbook_region_code"));
            row.putIfAbsent("region_address", handbook.get("handbook_region_name"));

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
        row.put("product_additional1", taskRow.get("product_additional1"));
        row.put("product_additional4", taskRow.get("product_additional4"));
        row.put("product_name", taskRow.get("product_name"));
        row.put("product_brand", taskRow.get("product_brand"));
        row.put("product_price", taskRow.get("product_price"));


        row.put("competitor_stock_status", "НЕТ В НАЛИЧИИ");
        row.put("competitor_price", null);
        row.put("report_status", "MISSING");

        return row;
    }

    private boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private String normalizeRegionCode(String code) {
        if (code == null) return null;
        return code.startsWith("0") ? code.substring(1) : code;
    }
}