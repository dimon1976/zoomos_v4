package com.java.mapper;

import com.java.dto.ExportTemplateDto;
import com.java.dto.ImportTemplateDto;
import com.java.dto.TemplateSummaryDto;

/**
 * Утилиты для преобразования шаблонов в краткий DTO.
 */
public final class TemplateMapper {

    private TemplateMapper() {
    }

    public static TemplateSummaryDto toSummary(ImportTemplateDto dto) {
        return new TemplateSummaryDto(dto.getId(), dto.getName(), dto.getEntityType().name());
    }

    public static TemplateSummaryDto toSummary(ExportTemplateDto dto) {
        return new TemplateSummaryDto(dto.getId(), dto.getName(), dto.getEntityType().name());
    }
}
