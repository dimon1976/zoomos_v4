package com.java.dto.reportfetcher;

import com.java.model.entity.ReportConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ReportConfigDto {
    private Long id;
    private String name;
    private Long clientId;
    private String sourceUrl;
    private String outputFormat = "XLSX";
    private String rowFilterExpression;
    private List<ReportOutputColumnDto> outputColumns = new ArrayList<>();

    public static ReportConfigDto fromEntity(ReportConfig config) {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setId(config.getId());
        dto.setName(config.getName());
        dto.setClientId(config.getClient() != null ? config.getClient().getId() : null);
        dto.setSourceUrl(config.getSourceUrl());
        dto.setOutputFormat(config.getOutputFormat());
        dto.setRowFilterExpression(config.getRowFilterExpression());
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
}
