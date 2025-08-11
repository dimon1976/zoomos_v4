package com.java.service.exports.strategies;

import com.java.model.entity.ExportTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Стратегия экспорта "Simple report"
 * Очищает поле competitorWebCacheUrl для определенных конкурентов
 */
@Component("simpleReportExportStrategy")
@Slf4j
@RequiredArgsConstructor
public class SimpleReportExportStrategy implements ExportStrategy {

    private final DefaultExportStrategy defaultStrategy;

    private static final Set<String> BLOCKED_COMPETITORS = new HashSet<>(List.of(
            "auchan.ru",
            "lenta.com",
            "metro-cc.ru",
            "myspar.ru",
            "okeydostavka.ru",
            "perekrestok.ru",
            "winelab.ru"
    ));

    @Override
    public String getName() {
        return "SIMPLE_REPORT";
    }

    @Override
    public List<Map<String, Object>> processData(
            List<Map<String, Object>> data,
            ExportTemplate template,
            Map<String, Object> context) {

        log.info("Применение стратегии Simple report");

        for (Map<String, Object> row : data) {
            Object nameObj = row.get("competitorName");
            if (nameObj == null) {
                nameObj = row.get("competitor_name");
            }
            if (nameObj != null) {
                String competitorName = nameObj.toString().toLowerCase();
                if (BLOCKED_COMPETITORS.contains(competitorName)) {
                    row.put("competitorWebCacheUrl", null);
                    row.put("competitor_web_cache_url", null);
                }
            }
        }

        return defaultStrategy.processData(data, template, context);
    }
}