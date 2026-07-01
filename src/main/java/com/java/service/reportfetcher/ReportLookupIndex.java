package com.java.service.reportfetcher;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory index of a lookup file, keyed by one column's value.
 * Built once per ReportRun and reused for every LOOKUP output column that shares the same key.
 */
public final class ReportLookupIndex {

    private final Map<String, Map<String, String>> rowsByKey;

    private ReportLookupIndex(Map<String, Map<String, String>> rowsByKey) {
        this.rowsByKey = rowsByKey;
    }

    public static ReportLookupIndex build(List<List<String>> allRows, String keyColumnName) {
        if (allRows.isEmpty()) {
            return new ReportLookupIndex(Map.of());
        }

        List<String> headers = allRows.get(0);
        int keyIndex = headers.indexOf(keyColumnName);
        if (keyIndex < 0) {
            throw new IllegalArgumentException(
                    "Колонка-ключ '" + keyColumnName + "' не найдена в справочнике");
        }

        Map<String, Map<String, String>> index = new HashMap<>();
        for (int i = 1; i < allRows.size(); i++) {
            List<String> row = allRows.get(i);
            if (keyIndex >= row.size()) {
                continue;
            }
            String key = row.get(keyIndex);
            if (key == null || key.isBlank()) {
                continue;
            }
            index.computeIfAbsent(key, k -> {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int col = 0; col < headers.size() && col < row.size(); col++) {
                    rowMap.put(headers.get(col), row.get(col));
                }
                return rowMap;
            });
        }
        return new ReportLookupIndex(index);
    }

    public Optional<String> getValue(String keyValue, String valueColumnName) {
        if (keyValue == null) {
            return Optional.empty();
        }
        Map<String, String> row = rowsByKey.get(keyValue);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(row.get(valueColumnName));
    }
}
