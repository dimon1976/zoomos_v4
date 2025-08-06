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
 * Оставляет только строки отчета, для которых есть соответствующая запись задания
 */
@Component("taskReportExportStrategy")
@Slf4j
@RequiredArgsConstructor
public class TaskReportExportStrategy implements ExportStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final DefaultExportStrategy defaultStrategy;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        log.debug("Входных записей: {}", data.size());
        long reportRows = data.stream()
                .filter(row -> "REPORT".equals(row.get("data_source")))
                .count();
        log.debug("Из них отчеты: {}", reportRows);

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
                    .map(row -> Objects.toString(row.get("product_additional1"), null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (taskNumbers.isEmpty()) {
                throw new IllegalArgumentException("Не указаны операции задания и отсутствуют номера заданий");
            }

            taskData = loadTaskDataByNumbers(taskNumbers);
        }
        log.debug("Загружено {} записей из задания", taskData.size());

        // 2. Создаем множество допустимых комбинаций номер+код сети
        Set<String> allowedKeys = taskData.stream()
                .map(row -> buildKey(row.get("product_additional1"), row.get("product_additional4")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        log.debug("Допустимых комбинаций номер+код сети: {}", allowedKeys.size());

        // 3. Обрабатываем строки отчета
        List<Map<String, Object>> processedData = new ArrayList<>();
        int matched = 0;
        for (Map<String, Object> reportRow : data) {
            if (!"REPORT".equals(reportRow.get("data_source"))) {
                continue;
            }
            // перед проверкой соответствия заданию заполняем отсутствующие поля из справочника
            Map<String, Object> workingRow = new HashMap<>(reportRow);  // Исправлено: было originalRow
            enrichWithHandbookData(workingRow, clientRegionCode);

            String key = buildKey(workingRow.get("product_additional1"), workingRow.get("product_additional4"));
            if (key == null) {
                log.debug("Пропущена запись отчета без кода сети после обогащения: {}", workingRow);
                continue;
            }
            if (!allowedKeys.contains(key)) {
                log.debug("Пропущена запись отчета без соответствующего задания: {}", key);
                continue;
            }

            Map<String, Object> processedRow = processReportRow(workingRow, maxReportAgeDays);  // Используем workingRow для консистентности
            processedData.add(processedRow);
            matched++;
        }
        log.debug("После сопоставления с заданиями осталось {} записей", matched);
        return defaultStrategy.processData(processedData, template, context);
    }


    private String buildKey(Object taskNumber, Object retailerCode) {
        if (taskNumber == null || retailerCode == null || retailerCode.toString().isBlank()) {
            return null;
        }
        return taskNumber.toString().trim() + "|" + retailerCode.toString().trim().toUpperCase();
    }


    private List<Map<String, Object>> loadTaskData(Long operationId) {
        String sql = "SELECT * FROM av_data WHERE operation_id = ? AND data_source = 'TASK'";
        return jdbcTemplate.queryForList(sql, operationId);
    }

    private List<Map<String, Object>> loadTaskDataByNumbers(Set<String> taskNumbers) {
        if (taskNumbers == null || taskNumbers.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(", ", Collections.nCopies(taskNumbers.size(), "?"));
        String sql = "SELECT * FROM av_data WHERE data_source = 'TASK' AND product_additional1 IN (" + placeholders + ")";
        return jdbcTemplate.queryForList(sql, taskNumbers.toArray());
    }


    private Map<String, Object> processReportRow(Map<String, Object> reportRow, int maxReportAgeDays) {
        Map<String, Object> processedRow = new HashMap<>(reportRow);
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
        String regionCandidate = Objects.toString(row.get("region"), clientRegionCode);
        String normalizedRegion = normalizeRegionCode(regionCandidate);
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

        if (handbooks.isEmpty()) {
            log.debug("Справочник market.yandex.ru для региона {} не найден", regionCandidate);
            return;
        }

        Map<String, Object> handbook = handbooks.get(0);
        String handbookRegion = normalizeRegionCode(Objects.toString(handbook.get("handbook_region_code"), null));
        if (normalizedRegion != null && handbookRegion != null && !handbookRegion.equals(normalizedRegion)) {
            log.debug("Регион клиента {} не совпадает со справочником {}, запись не обогащена", normalizedRegion, handbookRegion);
            return;
        }


        if (isBlank(row.get("product_additional4"))) {
            row.put("product_additional4", handbook.get("handbook_retail_network_code"));
        }
        if (isBlank(row.get("competitor_name"))) {
            row.put("competitor_name", handbook.get("handbook_retail_network"));
        }
        if (isBlank(row.get("region"))) {
            row.put("region", handbook.get("handbook_region_code"));
        }
        if (isBlank(row.get("region_address"))) {
            row.put("region_address", handbook.get("handbook_region_name"));
        }
        log.debug("Строка обогащена из справочника: {}", handbook);

    }


    private boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private String normalizeRegionCode(String code) {
        if (code == null) return null;
        return code.startsWith("0") ? code.substring(1) : code;
    }
}