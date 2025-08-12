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

@Component("taskReportExportStrategy")
@Slf4j
@RequiredArgsConstructor
public class TaskReportExportStrategy implements ExportStrategy {

    private final JdbcTemplate jdbcTemplate;
    private final DefaultExportStrategy defaultStrategy;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DEFAULT_MAX_REPORT_AGE_DAYS = 3;

    private record TaskNetworkKey(String number, String network) {}

    @Override
    public String getName() {
        return "TASK_REPORT";
    }

    @Override
    public List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context) {

        log.info("Применение стратегии Задание-Отчет");
        log.debug("Входных записей: {}", data.size());

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

        // Логируем первые несколько записей отчета
        if (log.isDebugEnabled() && !reportData.isEmpty()) {
            log.debug("Пример первой записи отчета:");
            Map<String, Object> firstRow = reportData.get(0);
            log.debug("  product_additional1: {}", firstRow.get("product_additional1"));
            log.debug("  competitor_additional: {}", firstRow.get("competitor_additional"));
            log.debug("  competitor_name: {}", firstRow.get("competitor_name"));
            log.debug("  competitor_url: {}", firstRow.get("competitor_url"));
        }

        // 2. Получаем уникальные номера заданий из отчетов
        Set<String> taskNumbers = reportData.stream()
                .map(row -> Objects.toString(row.get("product_additional1"), null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        log.debug("Уникальных номеров заданий в отчетах: {}", taskNumbers.size());
        log.debug("Номера заданий: {}", taskNumbers);

        if (taskNumbers.isEmpty()) {
            log.warn("В отчетах нет номеров заданий (product_additional1)");
            return Collections.emptyList();
        }

        // 3. Загружаем допустимые комбинации номер_задания + код_розничной_сети
        Set<String> allowedKeys = loadAllowedTaskKeys(taskNumbers);
        log.debug("Допустимых комбинаций номер+код_сети из заданий: {}", allowedKeys.size());
        if (log.isDebugEnabled()) {
            log.debug("Допустимые ключи: {}", allowedKeys);
        }

        // 4. Анализируем какие ключи ожидаются в отчетах
        if (log.isDebugEnabled()) {
            Set<String> reportKeys = reportData.stream()
                    .map(row -> {
                        String taskNum = Objects.toString(row.get("product_additional1"), null);
                        String networkCode = Objects.toString(row.get("competitor_additional"), null);
                        return buildKey(taskNum, networkCode);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            log.debug("Уникальных ключей в отчетах: {}", reportKeys.size());
            log.debug("Ключи отчетов: {}", reportKeys);

            // Показываем какие ключи отчетов не найдены в заданиях
            Set<String> missingKeys = new HashSet<>(reportKeys);
            missingKeys.removeAll(allowedKeys);
            if (!missingKeys.isEmpty()) {
                log.debug("Ключи отчетов, не найденные в заданиях: {}", missingKeys);
            }
        }

        // 5. Фильтруем и обрабатываем строки отчета
        List<Map<String, Object>> processedData = new ArrayList<>();
        int matched = 0;
        int enriched = 0;
        int skippedWithoutKey = 0;
        Map<TaskNetworkKey, Integer> missingKeyCounts = new HashMap<>();

        for (Map<String, Object> reportRow : reportData) {
            Map<String, Object> workingRow = new HashMap<>(reportRow);

            // 5.1. Обогащаем данными из справочника если нужно (только для market.yandex.ru)
            if (needsEnrichment(workingRow)) {
                boolean wasEnriched = enrichWithHandbookData(workingRow, clientRegionCode);
                if (wasEnriched) {
                    enriched++;
                }
            }

            // 5.2. Проверяем соответствие заданию (строгое соответствие)
            String key = buildKey(
                    workingRow.get("product_additional1"),
                    workingRow.get("competitor_additional"));

            if (key == null) {
                skippedWithoutKey++;
                TaskNetworkKey logKey = new TaskNetworkKey(
                        Objects.toString(workingRow.get("product_additional1"), null),
                        Objects.toString(workingRow.get("competitor_additional"), null)
                );
                missingKeyCounts.merge(logKey, 1, Integer::sum);
                continue;
            }

            if (!allowedKeys.contains(key)) {
                log.debug("Пропущена запись отчета без соответствующего задания: ключ={}", key);
                continue;
            }

            // 5.3. Корректируем дату мониторинга
            adjustMonitoringDate(workingRow, maxReportAgeDays);

            processedData.add(workingRow);
            matched++;
        }

        if (!missingKeyCounts.isEmpty()) {
            String summary = missingKeyCounts.entrySet().stream()
                    .map(e -> String.format(
                            "номер=%s, код_сети=%s, пропущено: %d",
                            e.getKey().number(),
                            e.getKey().network(),
                            e.getValue()))
                    .collect(Collectors.joining("; "));
            log.debug("Пропущенные записи отчета без ключа: {}", summary);
        }

        log.info("После фильтрации осталось {} записей из {} (обогащено: {}, пропущено без ключа: {})",
                matched, reportData.size(), enriched, skippedWithoutKey);

        // 6. Применяем стандартную обработку для форматирования полей
        return defaultStrategy.processData(processedData, template, context);
    }

    /**
     * Проверяет, нужно ли обогащение данными из справочника (только для market.yandex.ru)
     */
    private boolean needsEnrichment(Map<String, Object> row) {
        String competitorUrl = Objects.toString(row.get("competitor_url"), "");
        if (!competitorUrl.contains("market.yandex.ru")) {
            return false;
        }

        return isBlank(row.get("competitor_additional")) ||
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
            regionToSearch = Objects.toString(row.get("region"), null);
        }

        log.debug("Попытка обогащения для market.yandex.ru, регион: {}", regionToSearch);

        // Ищем в справочнике
        Map<String, Object> handbook = findHandbook("market.yandex.ru", regionToSearch);
        if (handbook == null) {
            log.debug("Справочник для market.yandex.ru и региона {} не найден", regionToSearch);
            return false;
        }

        boolean wasEnriched = false;

        // Заполняем пустые поля
        if (isBlank(row.get("competitor_additional"))) {
            Object newCode = handbook.get("handbook_retail_network_code");
            row.put("competitor_additional", newCode);
            log.debug("Обогащен код сети: {} -> {}", row.get("competitor_additional"), newCode);
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
        log.debug("Поиск в справочнике: webSite={}, regionCode={}", webSite, regionCode);

        if (isBlank(regionCode)) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? LIMIT 1";
            log.debug("SQL без региона: {}, параметр: {}", sql, webSite);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite);
            log.debug("Найдено записей без региона: {}", results.size());
            return results.isEmpty() ? null : results.get(0);
        }

        String normalizedRegion = normalizeRegionCode(regionCode);
        String altRegion = null;
        if (normalizedRegion.length() == 1) {
            altRegion = "0" + normalizedRegion;
        } else if (normalizedRegion.length() == 2 && !normalizedRegion.startsWith("0")) {
            altRegion = "0" + normalizedRegion;
        }

        if (altRegion != null) {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? " +
                    "AND (handbook_region_code = ? OR handbook_region_code = ?) LIMIT 1";
            log.debug("SQL с альтернативным регионом: {}, параметры: {}, {}, {}",
                    sql, webSite, normalizedRegion, altRegion);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite, normalizedRegion, altRegion);
            log.debug("Найдено записей с альтернативным регионом: {}", results.size());
            return results.isEmpty() ? null : results.get(0);
        } else {
            String sql = "SELECT * FROM av_handbook WHERE handbook_web_site = ? " +
                    "AND handbook_region_code = ? LIMIT 1";
            log.debug("SQL с точным регионом: {}, параметры: {}, {}", sql, webSite, normalizedRegion);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, webSite, normalizedRegion);
            log.debug("Найдено записей с точным регионом: {}", results.size());
            return results.isEmpty() ? null : results.get(0);
        }
    }

    /**
     * Корректирует дату мониторинга если она слишком старая
     */
    private void adjustMonitoringDate(Map<String, Object> row, int maxReportAgeDays) {
        String dateStr = Objects.toString(row.get("competitor_date"), null);
        if (dateStr == null) {
            row.put("competitor_date", LocalDate.now().format(DATE_FORMAT));
            return;
        }

        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
            LocalDate today = LocalDate.now();
            LocalDate minAllowedDate = today.minusDays(maxReportAgeDays - 1L);

            if (date.isBefore(minAllowedDate)) {
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
            log.trace("buildKey: null параметр - taskNumber={}, retailerCode={}", taskNumber, retailerCode);
            return null;
        }

        String taskStr = taskNumber.toString().trim();
        String codeStr = retailerCode.toString().trim();

        if (taskStr.isEmpty() || codeStr.isEmpty()) {
            log.trace("buildKey: пустой параметр - taskStr='{}', codeStr='{}'", taskStr, codeStr);
            return null;
        }

        String key = taskStr + "|" + codeStr.toUpperCase();
        log.trace("buildKey: сформирован ключ '{}'", key);
        return key;
    }

    /**
     * Загружает данные заданий по номерам
     */
    private Set<String> loadAllowedTaskKeys(Set<String> taskNumbers) {
        log.debug("=== Загрузка допустимых ключей для заданий ===");
        if (taskNumbers == null || taskNumbers.isEmpty()) {
            log.debug("Пустой список номеров заданий");
            return Collections.emptySet();
        }

        Set<String> allowed = new HashSet<>();
        List<String> numbers = new ArrayList<>(taskNumbers);
        int batchSize = 1000;

        for (int i = 0; i < numbers.size(); i += batchSize) {
            List<String> batch = numbers.subList(i, Math.min(i + batchSize, numbers.size()));
            String placeholders = String.join(", ", Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT product_additional1, competitor_additional FROM av_data WHERE data_source = 'TASK' " +
                    "AND product_additional1 IN (" + placeholders + ")";

            log.debug("SQL для поиска заданий: {}", sql);
            log.debug("Параметры поиска: {}", batch);

            jdbcTemplate.query(sql, batch.toArray(), rs -> {
                String taskNum = rs.getString("product_additional1");
                String networkCode = rs.getString("competitor_additional");
                log.debug("Найдена запись задания: taskNum='{}', networkCode='{}'", taskNum, networkCode);

                String key = buildKey(taskNum, networkCode);
                if (key != null) {
                    allowed.add(key);
                    log.debug("Добавлен допустимый ключ: '{}'", key);
                } else {
                    log.debug("Пропущена запись задания с null ключом: taskNum='{}', networkCode='{}'",
                            taskNum, networkCode);
                }
            });
        }

        log.debug("=== Итого загружено {} допустимых ключей ===", allowed.size());
        if (allowed.isEmpty()) {
            log.warn("ВНИМАНИЕ: Не найдено ни одного задания с data_source='TASK' для номеров: {}", taskNumbers);

            // Дополнительная диагностика - проверим что вообще есть в таблице
            String countSql = "SELECT COUNT(*) FROM av_data WHERE data_source = 'TASK'";
            Integer totalTasks = jdbcTemplate.queryForObject(countSql, Integer.class);
            log.debug("Всего записей с data_source='TASK' в таблице: {}", totalTasks);

            if (totalTasks != null && totalTasks > 0) {
                // Посмотрим какие номера заданий есть в таблице
                String sampleSql = "SELECT DISTINCT product_additional1 FROM av_data WHERE data_source = 'TASK' LIMIT 10";
                List<String> sampleTaskNumbers = jdbcTemplate.queryForList(sampleSql, String.class);
                log.debug("Примеры номеров заданий в таблице: {}", sampleTaskNumbers);
            }
        }

        return allowed;
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
        return trimmed.replaceFirst("^0+(?!$)", "");
    }
}