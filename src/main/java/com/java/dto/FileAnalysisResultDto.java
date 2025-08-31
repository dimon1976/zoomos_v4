package com.java.dto;

import com.java.model.enums.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAnalysisResultDto {

    private String filename;
    private Long fileSize;
    private String fileFormat;
    private String encoding;
    private String delimiter;
    private Integer totalColumns;
    private Integer totalRows;
    private List<String> columnHeaders;
    private List<Map<String, String>> sampleData;
    private Boolean hasHeader;

    // Рекомендации для маппинга
    private List<FieldMappingSuggestion> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMappingSuggestion {
        private String columnName;
        private Integer columnIndex;
        private String suggestedFieldName;
        private FieldType suggestedFieldType;
        private String sampleValue;
    }
}