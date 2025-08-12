package com.java.service.exports;

import com.java.dto.ExportTemplateFilterDto;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateFilter;
import com.java.model.entity.ExportTemplateField;
import com.java.model.enums.EntityType;
import com.java.model.enums.ExportStrategy;
import com.java.model.enums.FilterType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для получения данных для экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportDataService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Ограничение на максимальное количество записей для предотвращения
     * переполнения памяти при экспорте больших данных
     */
    @Value("${export.max-rows:300000}")
    private int maxRows;

    /**
     * Размер чанка при потоковой выгрузке
     */
    @Value("${export.chunk-size:10000}")
    private int chunkSize;

    /**
     * Загружает данные для экспорта
     */
    public List<Map<String, Object>> loadData(
            List<Long> operationIds,
            ExportTemplate template,
            ZonedDateTime dateFrom,
            ZonedDateTime dateTo,
            List<ExportTemplateFilterDto> additionalFilters) {

        log.info("Загрузка данных для экспорта: операции {}, шаблон {}",
                operationIds, template.getName());

        // Определяем нужные колонки
        List<String> columns = template.getFields().stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsIncluded()))
                .map(ExportTemplateField::getEntityFieldName)
                .map(this::resolveColumnName)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!columns.contains("operation_id")) {
            columns.add("operation_id");
        }
        if (template.getExportStrategy() == ExportStrategy.TASK_REPORT
                && template.getEntityType() == EntityType.AV_DATA
                && !columns.contains("data_source")) {
            columns.add("data_source");
        }
        String columnList = columns.isEmpty() ? "*" : String.join(", ", columns);

        // Строим SQL запрос
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        switch (template.getEntityType()) {
            case AV_DATA:
                sql.append("SELECT ").append(columnList).append(" FROM av_data WHERE 1=1");
                break;
            case AV_HANDBOOK:
                sql.append("SELECT ").append(columnList).append(" FROM av_handbook WHERE 1=1");
                break;
            default:
                throw new IllegalArgumentException("Неподдерживаемый тип сущности: " +
                        template.getEntityType());
        }

        // Фильтр по операциям
        if (operationIds != null && !operationIds.isEmpty()) {
            sql.append(" AND operation_id IN (");
            sql.append(operationIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(", ")));
            sql.append(")");
            params.addAll(operationIds);
        }

        // Фильтр по датам
        if (dateFrom != null) {
            sql.append(" AND created_at >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND created_at <= ?");
            params.add(dateTo);
        }

        // Применяем фильтры из шаблона
        List<ExportTemplateFilter> activeFilters = template.getFilters().stream()
                .filter(ExportTemplateFilter::getIsActive)
                .toList();

        for (ExportTemplateFilter filter : activeFilters) {
            applyFilter(sql, params, filter.getFieldName(),
                    filter.getFilterType(), filter.getFilterValue());
        }

        // Применяем дополнительные фильтры (переопределяют шаблонные)
        if (additionalFilters != null) {
            Map<String, ExportTemplateFilterDto> filterMap = additionalFilters.stream()
                    .collect(Collectors.toMap(
                            ExportTemplateFilterDto::getFieldName,
                            f -> f,
                            (f1, f2) -> f2 // При дубликатах используем последний
                    ));

            // Переопределяем фильтры из шаблона
            for (ExportTemplateFilter templateFilter : activeFilters) {
                if (filterMap.containsKey(templateFilter.getFieldName())) {
                    ExportTemplateFilterDto overrideFilter = filterMap.get(templateFilter.getFieldName());
                    applyFilter(sql, params, overrideFilter.getFieldName(),
                            overrideFilter.getFilterType(), overrideFilter.getFilterValue());
                    filterMap.remove(templateFilter.getFieldName());
                }
            }

            // Добавляем новые фильтры
            for (ExportTemplateFilterDto filter : filterMap.values()) {
                applyFilter(sql, params, filter.getFieldName(),
                        filter.getFilterType(), filter.getFilterValue());
            }
        }

        // Добавляем сортировку
        if (template.getExportStrategy() == ExportStrategy.TASK_REPORT
                && template.getEntityType() == EntityType.AV_DATA) {
            sql.append(" AND data_source = 'REPORT'");
            log.debug("Применен фильтр data_source=REPORT для стратегии TASK_REPORT");
        }

        sql.append(" ORDER BY created_at DESC");

        String baseSql = sql.toString();
        log.debug("SQL запрос: {}", baseSql);
        log.debug("Параметры: {}", params);

        List<Map<String, Object>> data = new ArrayList<>();
        ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
        int offset = 0;
        boolean more = true;
        while (more && data.size() < maxRows) {
            int limit = Math.min(chunkSize, maxRows - data.size());
            String pagedSql = baseSql + " LIMIT ? OFFSET ?";
            List<Object> pagedParams = new ArrayList<>(params);
            pagedParams.add(limit);
            pagedParams.add(offset);

            int before = data.size();
            RowCallbackHandler handler = new RowCallbackHandler() {
                int rowNum = 0;

                @Override
                public void processRow(java.sql.ResultSet rs) throws java.sql.SQLException {
                    data.add(rowMapper.mapRow(rs, rowNum++));
                }
            };

            jdbcTemplate.query(pagedSql, pagedParams.toArray(), handler);

            int fetched = data.size() - before;
            more = fetched == limit;
            offset += limit;
        }

        log.info("Загружено {} записей (ограничение: {})", data.size(), maxRows);
        if (data.size() >= maxRows) {
            log.warn("Результат усечен до {} записей, добавьте фильтры для уменьшения объема данных", maxRows);
        }
        if (!data.isEmpty()) {
            log.debug("Пример строки: ключи={}, значения={}", data.get(0).keySet(), data.get(0));
        }

        return data;
    }

    /**
     * Применяет фильтр к SQL запросу
     */
    private void applyFilter(StringBuilder sql, List<Object> params,
                             String fieldName, FilterType filterType, String filterValue) {

        // Определяем название колонки в БД для указанного поля фильтра
        String columnName = resolveColumnName(fieldName);

        switch (filterType) {
            case EQUALS:
                if (filterValue.contains(",")) {
                    String[] values = filterValue.split(",");
                    sql.append(" AND ").append(columnName).append(" IN (");
                    sql.append(Arrays.stream(values)
                            .map(v -> "?")
                            .collect(Collectors.joining(", ")));
                    sql.append(")");
                    for (String value : values) {
                        params.add(parseFilterValue(value.trim()));
                    }
                } else {
                    sql.append(" AND ").append(columnName).append(" = ?");
                    params.add(parseFilterValue(filterValue));
                }
                break;

            case NOT_EQUALS:
                if (filterValue.contains(",")) {
                    String[] values = filterValue.split(",");
                    sql.append(" AND ").append(columnName).append(" NOT IN (");
                    sql.append(Arrays.stream(values)
                            .map(v -> "?")
                            .collect(Collectors.joining(", ")));
                    sql.append(")");
                    for (String value : values) {
                        params.add(parseFilterValue(value.trim()));
                    }
                } else {
                    sql.append(" AND ").append(columnName).append(" != ?");
                    params.add(parseFilterValue(filterValue));
                }
                break;

            case CONTAINS:
                if (filterValue.contains(",")) {
                    String[] values = filterValue.split(",");
                    sql.append(" AND (");
                    sql.append(Arrays.stream(values)
                            .map(v -> columnName + " LIKE ?")
                            .collect(Collectors.joining(" OR ")));
                    sql.append(")");
                    for (String value : values) {
                        params.add("%" + value.trim() + "%");
                    }
                } else {
                    sql.append(" AND ").append(columnName).append(" LIKE ?");
                    params.add("%" + filterValue + "%");
                }
                break;

            case STARTS_WITH:
                if (filterValue.contains(",")) {
                    String[] values = filterValue.split(",");
                    sql.append(" AND (");
                    sql.append(Arrays.stream(values)
                            .map(v -> columnName + " LIKE ?")
                            .collect(Collectors.joining(" OR ")));
                    sql.append(")");
                    for (String value : values) {
                        params.add(value.trim() + "%");
                    }
                } else {
                    sql.append(" AND ").append(columnName).append(" LIKE ?");
                    params.add(filterValue + "%");
                }
                break;

            case ENDS_WITH:
                if (filterValue.contains(",")) {
                    String[] values = filterValue.split(",");
                    sql.append(" AND (");
                    sql.append(Arrays.stream(values)
                            .map(v -> columnName + " LIKE ?")
                            .collect(Collectors.joining(" OR ")));
                    sql.append(")");
                    for (String value : values) {
                        params.add("%" + value.trim());
                    }
                } else {
                    sql.append(" AND ").append(columnName).append(" LIKE ?");
                    params.add("%" + filterValue);
                }
                break;

            case BETWEEN:
                // Ожидаем значение в формате "value1,value2"
                String[] values = filterValue.split(",");
                if (values.length == 2) {
                    sql.append(" AND ").append(columnName).append(" BETWEEN ? AND ?");
                    params.add(parseFilterValue(values[0].trim()));
                    params.add(parseFilterValue(values[1].trim()));
                }
                break;

            case IN:
                // Ожидаем значения через запятую
                String[] inValues = filterValue.split(",");
                sql.append(" AND ").append(columnName).append(" IN (");
                sql.append(Arrays.stream(inValues)
                        .map(v -> "?")
                        .collect(Collectors.joining(", ")));
                sql.append(")");
                for (String value : inValues) {
                    params.add(parseFilterValue(value.trim()));
                }
                break;

            case IS_NULL:
                sql.append(" AND ").append(columnName).append(" IS NULL");
                break;

            case IS_NOT_NULL:
                sql.append(" AND ").append(columnName).append(" IS NOT NULL");
                break;

            case GREATER_THAN:
                sql.append(" AND ").append(columnName).append(" > ?");
                params.add(parseFilterValue(filterValue));
                break;

            case LESS_THAN:
                sql.append(" AND ").append(columnName).append(" < ?");
                params.add(parseFilterValue(filterValue));
                break;

            default:
                log.warn("Неизвестный тип фильтра: {}", filterType);
        }
    }

    /**
     * Преобразует имя поля фильтра в соответствующее имя колонки БД
     */
    private String resolveColumnName(String fieldName) {
        return switch (fieldName) {
            case "taskNumber" -> "product_additional1";
            case "retailerCode" -> "product_additional4";
            case "competitorUrl" -> "competitor_url";
            default -> toSnakeCase(fieldName);
        };
    }

    /**
     * Парсит значение фильтра
     */
    private Object parseFilterValue(String value) {
        // Пытаемся определить тип значения
        try {
            // Проверяем на число
            if (value.matches("-?\\d+")) {
                return Long.parseLong(value);
            }
            if (value.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(value);
            }
            // Проверяем на дату
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return ZonedDateTime.parse(value + "T00:00:00Z");
            }
            // Булево значение
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
        } catch (Exception e) {
            log.debug("Не удалось распарсить значение как специальный тип: {}", value);
        }

        // По умолчанию возвращаем как строку
        return value;
    }

    /**
     * Преобразует camelCase в snake_case
     */
    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Получает общее количество записей для экспорта
     */
    public Long countData(
            List<Long> operationIds,
            ExportTemplate template,
            ZonedDateTime dateFrom,
            ZonedDateTime dateTo,
            List<ExportTemplateFilterDto> additionalFilters) {

        // Используем тот же запрос, но с COUNT
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        switch (template.getEntityType()) {
            case AV_DATA:
                sql.append("SELECT COUNT(*) FROM av_data WHERE 1=1");
                break;
            case AV_HANDBOOK:
                sql.append("SELECT COUNT(*) FROM av_handbook WHERE 1=1");
                break;
            default:
                return 0L;
        }

        // Применяем те же фильтры
        if (operationIds != null && !operationIds.isEmpty()) {
            sql.append(" AND operation_id IN (");
            sql.append(operationIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(", ")));
            sql.append(")");
            params.addAll(operationIds);
        }

        if (dateFrom != null) {
            sql.append(" AND created_at >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            sql.append(" AND created_at <= ?");
            params.add(dateTo);
        }

        // Фильтры из шаблона и дополнительные
        List<ExportTemplateFilter> activeFilters = template.getFilters().stream()
                .filter(ExportTemplateFilter::getIsActive)
                .toList();

        for (ExportTemplateFilter filter : activeFilters) {
            applyFilter(sql, params, filter.getFieldName(),
                    filter.getFilterType(), filter.getFilterValue());
        }

        if (additionalFilters != null) {
            Map<String, ExportTemplateFilterDto> filterMap = additionalFilters.stream()
                    .collect(Collectors.toMap(
                            ExportTemplateFilterDto::getFieldName,
                            f -> f,
                            (f1, f2) -> f2
                    ));

            for (ExportTemplateFilter templateFilter : activeFilters) {
                if (filterMap.containsKey(templateFilter.getFieldName())) {
                    ExportTemplateFilterDto overrideFilter = filterMap.get(templateFilter.getFieldName());
                    applyFilter(sql, params, overrideFilter.getFieldName(),
                            overrideFilter.getFilterType(), overrideFilter.getFilterValue());
                    filterMap.remove(templateFilter.getFieldName());
                }
            }

            for (ExportTemplateFilterDto filter : filterMap.values()) {
                applyFilter(sql, params, filter.getFieldName(),
                        filter.getFilterType(), filter.getFilterValue());
            }
        }

        if (template.getExportStrategy() == ExportStrategy.TASK_REPORT
                && template.getEntityType() == EntityType.AV_DATA) {
            sql.append(" AND data_source = 'REPORT'");
        }

        return jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
    }
}