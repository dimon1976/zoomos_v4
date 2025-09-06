package com.java.model.utils;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
@Builder
public class RedirectExportTemplate {
    
    private final String fileFormat;
    private final String csvEncoding;
    private final String csvDelimiter;
    private final String csvQuoteChar;
    
    private final boolean includeId;
    private final boolean includeModel;
    private final String idColumnName;
    private final String modelColumnName;
    
    public static RedirectExportTemplate createDefault() {
        return RedirectExportTemplate.builder()
                .fileFormat("CSV")
                .csvEncoding("Windows-1251")
                .csvDelimiter(";")
                .csvQuoteChar("\"")
                .includeId(true)
                .includeModel(true)
                .idColumnName("ID")
                .modelColumnName("Модель")
                .build();
    }
    
    public static RedirectExportTemplate create(boolean includeId, boolean includeModel, 
                                               String idColumnName, String modelColumnName) {
        return RedirectExportTemplate.builder()
                .fileFormat("CSV")
                .csvEncoding("Windows-1251")
                .csvDelimiter(";")
                .csvQuoteChar("\"")
                .includeId(includeId)
                .includeModel(includeModel)
                .idColumnName(idColumnName != null ? idColumnName : "ID")
                .modelColumnName(modelColumnName != null ? modelColumnName : "Модель")
                .build();
    }
    
    /**
     * Конвертирует в ExportTemplate для использования с FileGeneratorService
     */
    public ExportTemplate toExportTemplate() {
        ExportTemplate template = new ExportTemplate();
        template.setFileFormat(fileFormat);
        template.setCsvEncoding(csvEncoding);
        template.setCsvDelimiter(csvDelimiter);
        template.setCsvQuoteChar(csvQuoteChar);
        template.setCsvIncludeHeader(true);  // Включаем заголовки
        
        List<ExportTemplateField> fields = new ArrayList<>();
        int order = 0;
        
        // ID колонка (опционально)
        if (includeId) {
            ExportTemplateField idField = new ExportTemplateField();
            idField.setEntityFieldName("id");
            idField.setExportColumnName(idColumnName);  // Заголовок колонки
            idField.setIsIncluded(true);
            idField.setFieldOrder(order++);
            fields.add(idField);
        }
        
        // Модель колонка (опционально) 
        if (includeModel) {
            ExportTemplateField modelField = new ExportTemplateField();
            modelField.setEntityFieldName("model");
            modelField.setExportColumnName(modelColumnName);  // Заголовок колонки
            modelField.setIsIncluded(true);
            modelField.setFieldOrder(order++);
            fields.add(modelField);
        }
        
        // Обязательные колонки для редиректов
        String[] requiredFields = {
            "originalUrl", "finalUrl", "redirectCount", "status", 
            "strategy", "timeMs", "httpCode", "errorMessage"
        };
        String[] requiredNames = {
            "Исходный URL", "Финальный URL", "Количество редиректов", "Статус",
            "Стратегия", "Время (мс)", "HTTP код", "Ошибка"
        };
        
        for (int i = 0; i < requiredFields.length; i++) {
            ExportTemplateField field = new ExportTemplateField();
            field.setEntityFieldName(requiredFields[i]);
            field.setExportColumnName(requiredNames[i]);  // Русский заголовок для CSV
            field.setIsIncluded(true);
            field.setFieldOrder(order++);
            fields.add(field);
        }
        
        template.setFields(fields);
        return template;
    }
}