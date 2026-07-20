package com.java.dto.reportfetcher;

import com.java.model.enums.ReportOutputColumnType;
import lombok.Data;

@Data
public class ReportOutputColumnDto {
    private Long id;
    private ReportOutputColumnType type;
    private String outputHeaderName;
    private Boolean included = true;
    private String sourceColumnName;
    private String formula;
    private String keyColumnInReport;
    private String keyColumnInLookup;
    private String valueColumnInLookup;
}
