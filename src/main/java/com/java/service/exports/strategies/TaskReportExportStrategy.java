package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    private static final int DEFAULT_MAX_REPORT_AGE_DAYS = 3;

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

        // Получаем параметры из контекста
        String clientRegionCode = (String) context.get("clientRegionCode");
        int maxReportAgeDays = context.get("maxReportAgeDays") != null
                ? ((Integer) context.get("maxReportAgeDays"))
                : DEFAULT_MAX_REPORT_AGE_DAYS;

        // 1. Отбираем только записи с data_source = 'REPORT'
        List<Map<String, Object>> reportData = data.stream()
                .filter(row -> "REPORT".equals(row.get("data_source")))
                .collect(Collectors.toList());
        log.debug("Записей с data_source=REPORT: {}", reportData.size());

        if (reportData.isEmpty()) {
            log.warn("Нет записей с data_source=REPORT");
            return Collections.emptyList();
        }

        // 2. Получаем уникальные номера заданий из отчетов
        Set<String> taskNumbers = reportData.stream()
                .map(row -> Objects.toString(row.get("product_additional1"), null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        log.debug("Уникальных номеров заданий в отчетах: {}", taskNumbers.size());

        if (taskNumbers.isEmpty()) {
            log.warn("В отчетах нет номеров заданий (product_additional1)");
            return Collections.emptyList();
        }

        // 3. Загружаем данные заданий для этих номеров
        List<Map<String, Object>> taskData = loadTaskDataByNumbers(taskNumbers);
        log.debug("Загружено {} записей из заданий", taskData.size());

        // 4. Создаем множество допустимых комбинаций номер_задания + код_розничной_сети
        Set<String> allowedKeys = taskData.stream()
                .map(row -> buildKey(
                        row.get("product_additional1"),
                        row.get("product_additional4")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        log.debug("Допустимых комбинаций номер+код_сети: {}", allowedKeys.size());

        // 5. Фильтруем и обрабатываем строки отчета
        List<Map<String, Object>> processedData = new ArrayList<>();
        int matched = 0;
        int enriched = 0;

        for (Map<String, Object> reportRow : reportData) {
            // Создаем копию строки для изменений
            Map<String, Object> workingRow = new HashMap<>(reportRow);

            // 5.1. Обогащаем данными из справочника если нужно
            if (needsEnrichment(workingRow)) {
                boolean wasEnriched = enrichWithHandbookData(workingRow, clientRegionCode);
                if (wasEnriched) {
                    enriched++;
                }
            }

            // 5.2. Проверяем соответствие заданию
            String key = buildKey(
                    workingRow.get("product_additional1"),
                    workingRow.get("product_additional4"));

            if (key == null) {
                log.debug("Пропущена запись отчета без ключа: номер={}, код_сети={}",
                        workingRow.get("product_additional1"),
                        workingRow.get("product_additional4"));
                continue;
            }

            if (!allowedKeys.contains(key)) {
                log.debug("Пропущена запись отчета без соответствующего задания: {}", key);
                continue;
            }

            // 5.3. Корректируем дату мониторинга
            adjustMonitoringDate(workingRow, maxReportAgeDays);

            processedData.add(workingRow);
            matched++;
        }

        log.info("После фильтрации осталось {} записей из {} (обогащено: {})",
                matched, reportData.size(), enriched);

        // 6. Применяем стандартную обработку для форматирования полей
        return defaultStrategy.processData(processedData, template, context);
    }

    /**
     * Проверяет, нужно ли обогащение данными из справочника
     */
    private boolean needsEnrichment(Map<String, Object> row) {
        String competitorUrl = Objects.toString(row.get("competitor_url"), "");
        if (!competitorUrl.contains("market.yandex.ru")) {
            return false;
        }

        return isBlank(row.get("product_additional4")) ||
                isBlank(row.get("competitor_name")) ||
                isBlank(row.get("region")) ||
                isBlank(row.get("region_address"));
    }

    /**
     * Обогащает строку данными из справочника
     */
    private boolean enrichWithHandbookData(Map<String, Object> row, String clientRegionCode) {
        String competitorUrl = Objects.toString(row.get("competitor_url"), "");
        if (!competitorUrl.contains("market.yandex.ru")) {
            return false;
        }

        // Определяем код региона для поиска
        String regionToSearch = clientRegionCode;
        if (isBlank(regionToSearch)) {
            // Если код региона клиента не задан, пытаемся взять из данных
            regionToSearch = Objects.toString(row.get("region"), null);
        }

        // Ищем в справочнике
        Map<String, Object> handbook = findHandbook("market.yandex.ru", regionToSearch);
        if (handbook == null) {
            log.debug("Справочник для market.yandex.ru и региона {} не найден", regionToSearch);
            return false;
        }

        boolean wasEnriched = false;

        // Заполняем пустые поля
        if (isBlank(row.get("product_additional4"))) {
            row.put("product_additional4", handbook.get("handbook_retail_network_code"));
            wasEnriched = true;
        }
        if (isBlank(row.get("competitor_name"))) {
            row.put("competitor_name", handbook.get("handbook_retail_network"));
            wasEnriched = true;
        }
        if (isBlank(row.get("region"))) {
            row.put("region", handbook.get("handbook_region_code"));
            wasEnriched = true;
        }
        if (isBlank(row.get("region_address"))) {
            row.put("region_address", handbook.get("handbook_physical_address"));
            wasEnriched = true;
        }

        if (wasEnriched) {
            log.debug("Строка обогащена из справочника для региона {}", regionToSearch);
        }

        return wasEnriched;
    }

    /**
     * Ищет запись в справочнике
     */
    private Map<String, Object> findHandbook(String webSite, String regionCode) {
        if (isBlank(regionCode)) {
            // Если регион не указан, ищем любую запись для сайта
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite);
            return results.isEmpty() ? null : results.get(0);
        }

        // Нормализуем код региона (убираем ведущие нули)
        String normalizedRegion = normalizeRegionCode(regionCode);

        // Создаем альтернативный вариант (с/без ведущего нуля)
        String altRegion = null;
        if (normalizedRegion.length() == 1) {
            altRegion = "0" + normalizedRegion;
        } else if (normalizedRegion.length() == 2 && !normalizedRegion.startsWith("0")) {
            altRegion = "0" + normalizedRegion;
        }

        // Ищем с учетом обоих вариантов
        if (altRegion != null) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? " +
                    "AND (handbook_region_code = ? OR handbook_region_code = ?) LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite, normalizedRegion, altRegion);
            return results.isEmpty() ? null : results.get(0);
        } else {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? " +
                    "AND handbook_region_code = ? LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite, normalizedRegion);
            return results.isEmpty() ? null : results.get(0);
        }
    }

    /**
     * Корректирует дату мониторинга если она слишком старая
     */
    private void adjustMonitoringDate(Map<String, Object> row, int maxReportAgeDays) {
        String dateStr = Objects.toString(row.get("competitor_date"), null);
        if (dateStr == null) {
            // Если даты нет, устанавливаем текущую
            row.put("competitor_date", LocalDate.now().format(DATE_FORMAT));
            return;
        }

        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            LocalDate today = LocalDate.now();
            LocalDate minAllowedDate = today.minusDays(maxReportAgeDays - 1L);

            if (date.isBefore(minAllowedDate)) {
                // Заменяем на крайнюю допустимую дату
                row.put("competitor_date", minAllowedDate.format(DATE_FORMAT));
                log.debug("Дата {} заменена на {} (макс. давность {} дней)",
                        dateStr, minAllowedDate.format(DATE_FORMAT), maxReportAgeDays);
            }
        } catch (DateTimeParseException e) {
            log.warn("Некорректная дата отчета: {}, установлена текущая", dateStr);
            row.put("competitor_date", LocalDate.now().format(DATE_FORMAT));
        }
    }

    /**
     * Строит ключ из номера задания и кода розничной сети
     */
    private String buildKey(Object taskNumber, Object retailerCode) {
        if (taskNumber == null || retailerCode == null) {
            return null;
        }

        String taskStr = taskNumber.toString().trim();
        String codeStr = retailerCode.toString().trim();

        if (taskStr.isEmpty() || codeStr.isEmpty()) {
            return null;
        }

        return taskStr + "|" + codeStr.toUpperCase();
    }

    /**
     * Загружает данные заданий по номерам
     */
    private List<Map<String, Object>> loadTaskDataByNumbers(Set<String> taskNumbers) {
        if (taskNumbers == null || taskNumbers.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(", ", Collections.nCopies(taskNumbers.size(), "?"));
        String sql = "SELECT * FROM av_data WHERE data_source = 'TASK' " +
                "AND product_additional1 IN (" + placeholders + ")";

        return jdbcTemplate.queryForList(sql, taskNumbers.toArray());
    }

    /**
     * Проверяет, пустое ли значение
     */
    private boolean isBlank(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    /**
     * Нормализует код региона (убирает ведущие нули)
     */
    private String normalizeRegionCode(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        // Убираем ведущие нули
        return trimmed.replaceFirst("^0+(?!$)", "");
    }
}