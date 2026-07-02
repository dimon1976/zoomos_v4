package com.java.dto.reportfetcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ReportConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
public class ReportConfigDto {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long id;
    private String name;
    private Long clientId;
    private String sourceUrl;
    private String outputFormat = "XLSX";
    private String rowFilterExpression;
    private List<ReportOutputColumnDto> outputColumns = new ArrayList<>();
    // Заголовки, реально пришедшие в последнем успешном скачивании отчёта — подсказки
    // для полей "Исходная колонка"/"Ключ в отчёте" в билдере колонок. Пусто, если ни разу
    // не запускался.
    private List<String> detectedReportColumns = new ArrayList<>();
    // Заголовки загруженного файла-справочника — подсказки для полей "Ключ в справочнике"/
    // "Значение из справочника". Пусто, если справочник не загружен.
    private List<String> lookupFileColumns = new ArrayList<>();
    private String lookupFileOriginalName;
    private String lookupFileStoredPath;

    public static ReportConfigDto fromEntity(ReportConfig config) {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setId(config.getId());
        dto.setName(config.getName());
        dto.setClientId(config.getClient() != null ? config.getClient().getId() : null);
        dto.setSourceUrl(config.getSourceUrl());
        dto.setOutputFormat(config.getOutputFormat());
        dto.setRowFilterExpression(config.getRowFilterExpression());
        dto.setDetectedReportColumns(parseColumnList(config.getDetectedReportColumns()));
        dto.setLookupFileOriginalName(config.getLookupFileOriginalName());
        dto.setLookupFileStoredPath(config.getLookupFileStoredPath());
        dto.setOutputColumns(config.getOutputColumns().stream().map(column -> {
            ReportOutputColumnDto columnDto = new ReportOutputColumnDto();
            columnDto.setId(column.getId());
            columnDto.setType(column.getType());
            columnDto.setOutputHeaderName(column.getOutputHeaderName());
            columnDto.setIncluded(column.getIncluded());
            columnDto.setSourceColumnName(column.getSourceColumnName());
            columnDto.setFormula(column.getFormula());
            columnDto.setKeyColumnInReport(column.getKeyColumnInReport());
            columnDto.setKeyColumnInLookup(column.getKeyColumnInLookup());
            columnDto.setValueColumnInLookup(column.getValueColumnInLookup());
            return columnDto;
        }).collect(Collectors.toList()));
        return dto;
    }

    private static List<String> parseColumnList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Не удалось разобрать detectedReportColumns: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
