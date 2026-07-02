package com.java.service.reportfetcher;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.enums.ReportOutputColumnType;
import com.java.service.exports.FileGeneratorService;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTransformService {

    private final FileReaderUtils fileReaderUtils;
    private final FileGeneratorService fileGeneratorService;
    private final ReportRowExpressionEvaluator expressionEvaluator;

    public ReportTransformResult transform(Path downloadedFilePath, String downloadedFileName,
                                            String downloadedFileFormat, ReportConfig config)
            throws IOException {
        FileMetadata reportMetadata = FileMetadata.builder()
                .originalFilename(downloadedFileName)
                .fileFormat(downloadedFileFormat)
                .tempFilePath(downloadedFilePath.toString())
                .detectedDelimiter(";")
                .detectedEncoding("UTF-8")
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();

        List<List<String>> reportRows = fileReaderUtils.readAllRows(reportMetadata);
        if (reportRows.isEmpty()) {
            throw new IllegalStateException("Скачанный отчёт пуст");
        }
        List<String> reportHeaders = reportRows.get(0);

        ReportLookupIndex lookupIndex = buildLookupIndex(config);

        List<ReportOutputColumn> lookupColumns = filterSortedByType(config, ReportOutputColumnType.LOOKUP);
        List<ReportOutputColumn> computedColumns = filterSortedByType(config, ReportOutputColumnType.COMPUTED);
        // No columns configured at all — download the report as-is, with every source column
        // included unchanged, instead of failing with "no fields configured".
        List<ReportOutputColumn> includedColumns = config.getOutputColumns().isEmpty()
                ? buildPassthroughColumns(reportHeaders)
                : config.getOutputColumns().stream()
                        .filter(ReportOutputColumn::getIncluded)
                        .sorted(Comparator.comparing(ReportOutputColumn::getPosition))
                        .toList();

        int warnings = 0;
        List<Map<String, Object>> outputRows = new ArrayList<>();
        for (int i = 1; i < reportRows.size(); i++) {
            List<String> sourceRow = reportRows.get(i);
            Map<String, Object> rowValues = new LinkedHashMap<>();
            for (int col = 0; col < reportHeaders.size() && col < sourceRow.size(); col++) {
                rowValues.put(reportHeaders.get(col), sourceRow.get(col));
            }

            for (ReportOutputColumn lookupColumn : lookupColumns) {
                Object keyValue = rowValues.get(lookupColumn.getKeyColumnInReport());
                String value = lookupIndex
                        .getValue(keyValue == null ? null : keyValue.toString(), lookupColumn.getValueColumnInLookup())
                        .orElse(null);
                rowValues.put(lookupColumn.getOutputHeaderName(), value);
            }

            // Separate map for SpEL evaluation only — numeric-looking strings are coerced here so
            // arithmetic/comparison operators work, but rowValues itself (used for lookup keys, SOURCE
            // passthrough, and final output) is never mutated, so leading zeros and exact string form
            // are preserved for lookup matching and output rendering.
            Map<String, Object> evaluationValues = new LinkedHashMap<>();
            rowValues.forEach((key, value) -> evaluationValues.put(key, coerceForEvaluation(value)));

            for (ReportOutputColumn computedColumn : computedColumns) {
                Object value = expressionEvaluator.evaluateFormula(computedColumn.getFormula(), evaluationValues);
                if (value == null && computedColumn.getFormula() != null) {
                    log.warn("Формула '{}' не вычислена для строки {} рана — пустая ячейка",
                            computedColumn.getFormula(), i);
                    warnings++;
                }
                rowValues.put(computedColumn.getOutputHeaderName(), value);
                evaluationValues.put(computedColumn.getOutputHeaderName(), value);
            }

            String filterExpression = config.getRowFilterExpression();
            if (filterExpression != null && !filterExpression.isBlank()) {
                boolean include = expressionEvaluator.evaluateFilter(filterExpression, evaluationValues);
                if (!include) {
                    continue;
                }
            }

            Map<String, Object> outputRow = new LinkedHashMap<>();
            for (ReportOutputColumn column : includedColumns) {
                String sourceKey = column.getType() == ReportOutputColumnType.SOURCE
                        ? column.getSourceColumnName()
                        : column.getOutputHeaderName();
                outputRow.put(column.getOutputHeaderName(), rowValues.get(sourceKey));
            }
            outputRows.add(outputRow);
        }

        ExportTemplate template = buildTemplate(config, includedColumns);
        String fileName = "report_" + config.getId() + "_" + System.currentTimeMillis();
        Path resultPath = fileGeneratorService.generateFile(outputRows.stream(), outputRows.size(), template, fileName);

        return new ReportTransformResult(resultPath, reportHeaders, warnings);
    }

    /**
     * Converts a raw CSV/XLSX cell string to a Long/Double when it looks numeric, so that
     * SpEL arithmetic in COMPUTED formulas works (SpEL cannot subtract two Strings).
     * Blank/non-numeric values are left as-is (e.g. an empty cell must stay "" for
     * rowFilterExpression comparisons like ['ОГРН'] != ''). Non-String values (e.g. an Integer
     * already produced by a prior formula) are passed through unchanged. Used only to build the
     * separate {@code evaluationValues} map — never applied to the canonical {@code rowValues}.
     */
    private Object coerceForEvaluation(Object raw) {
        if (!(raw instanceof String)) {
            return raw;
        }
        String value = (String) raw;
        if (value.isEmpty()) {
            return value;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            // not a plain integer, try decimal below
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // not a plain dot-decimal either, try normalizing русскую-locale number formatting below
        }

        // Русскоязычные выгрузки часто пишут числа как "1 200,50" (пробел/неразрывный пробел —
        // разделитель разрядов, запятая — десятичный разделитель). Пробуем распознать это ПОСЛЕ
        // обычного парсинга — если после нормализации получилось число, используем его для
        // формул/фильтра; если нет (это реальный текст с пробелами/запятыми, а не число) —
        // возвращаем исходную строку без изменений, чтобы не испортить её.
        String normalized = value.replace(" ", "").replace(" ", "").replace(",", ".");
        if (!normalized.equals(value)) {
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                // fall through
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                // not numeric even normalized — fall through to returning the original string
            }
        }
        return value;
    }

    private List<ReportOutputColumn> buildPassthroughColumns(List<String> reportHeaders) {
        List<ReportOutputColumn> columns = new ArrayList<>();
        int position = 0;
        for (String header : reportHeaders) {
            columns.add(ReportOutputColumn.builder()
                    .type(ReportOutputColumnType.SOURCE)
                    .sourceColumnName(header)
                    .outputHeaderName(header)
                    .position(position++)
                    .included(true)
                    .build());
        }
        return columns;
    }

    private List<ReportOutputColumn> filterSortedByType(ReportConfig config, ReportOutputColumnType type) {
        return config.getOutputColumns().stream()
                .filter(c -> c.getType() == type)
                .sorted(Comparator.comparing(ReportOutputColumn::getPosition))
                .toList();
    }

    private ReportLookupIndex buildLookupIndex(ReportConfig config) throws IOException {
        boolean hasLookupColumns = config.getOutputColumns().stream()
                .anyMatch(c -> c.getType() == ReportOutputColumnType.LOOKUP);
        if (!hasLookupColumns) {
            return ReportLookupIndex.build(List.of(), null);
        }
        if (config.getLookupFileStoredPath() == null) {
            throw new IllegalStateException(
                    "В конфиге есть LOOKUP-колонки, но файл-справочник не загружен");
        }

        FileMetadata lookupMetadata = FileMetadata.builder()
                .originalFilename(config.getLookupFileOriginalName())
                .fileFormat(config.getLookupFileFormat())
                .tempFilePath(config.getLookupFileStoredPath())
                .detectedDelimiter(config.getLookupFileDelimiter())
                .detectedEncoding(config.getLookupFileEncoding())
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();
        List<List<String>> lookupRows = fileReaderUtils.readAllRows(lookupMetadata);

        // MVP-ограничение (по спеке): все LOOKUP-колонки конфига используют один и тот же файл-
        // справочник и одну колонку-ключ — берём keyColumnInLookup первой LOOKUP-колонки.
        String keyColumn = config.getOutputColumns().stream()
                .filter(c -> c.getType() == ReportOutputColumnType.LOOKUP)
                .map(ReportOutputColumn::getKeyColumnInLookup)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Не задана колонка-ключ справочника"));

        return ReportLookupIndex.build(lookupRows, keyColumn);
    }

    private ExportTemplate buildTemplate(ReportConfig config, List<ReportOutputColumn> includedColumns) {
        ExportTemplate template = ExportTemplate.builder()
                .name(config.getName())
                .fileFormat("CSV".equalsIgnoreCase(config.getOutputFormat()) ? "CSV" : "XLSX")
                .csvDelimiter(";")
                .csvEncoding("UTF-8")
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();

        List<ExportTemplateField> fields = new ArrayList<>();
        int order = 1;
        for (ReportOutputColumn column : includedColumns) {
            fields.add(ExportTemplateField.builder()
                    .template(template)
                    .entityFieldName(column.getOutputHeaderName())
                    .exportColumnName(column.getOutputHeaderName())
                    .fieldOrder(order++)
                    .isIncluded(true)
                    .build());
        }
        template.setFields(fields);
        return template;
    }

    public record ReportTransformResult(Path resultFilePath, List<String> reportHeaders, int warningCount) {}
}
