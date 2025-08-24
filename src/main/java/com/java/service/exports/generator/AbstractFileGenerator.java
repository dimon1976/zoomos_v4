package com.java.service.exports.generator;

import com.java.exception.ErrorMessages;
import com.java.exception.FileOperationException;
import com.java.exception.FileProcessingException;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFileGenerator implements FileGenerator {

    protected final PathResolver pathResolver;

    protected void validateInputs(Object data, ExportTemplate template, String fileName) {
        if (data == null) {
            throw new FileOperationException(
                    ErrorMessages.format("%s: data", ErrorMessages.REQUIRED_FIELD_EMPTY)
            );
        }

        if (template == null) {
            throw new FileOperationException(ErrorMessages.TEMPLATE_NOT_FOUND);
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new FileOperationException(
                    ErrorMessages.format("%s: fileName", ErrorMessages.REQUIRED_FIELD_EMPTY)
            );
        }

        if (template.getFields() == null || template.getFields().isEmpty()) {
            throw new FileOperationException(ErrorMessages.TEMPLATE_FIELDS_REQUIRED);
        }
    }

    protected List<ExportTemplateField> getOrderedFields(ExportTemplate template) {
        return template.getFields().stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsIncluded()))
                .sorted(Comparator.comparing(ExportTemplateField::getFieldOrder))
                .collect(Collectors.toList());
    }

    protected List<String> extractHeaders(List<ExportTemplateField> fields) {
        return fields.stream()
                .map(ExportTemplateField::getExportColumnName)
                .collect(Collectors.toList());
    }

    protected Path moveToExportDirectory(Path tempFile, String fileName, 
                                       ExportTemplate template) throws IOException {
        try {
            boolean hasTemplate = template.getFilenameTemplate() != null
                    && !template.getFilenameTemplate().trim().isEmpty();
            return pathResolver.moveFromTempToExport(tempFile, fileName, !hasTemplate);
        } catch (IOException e) {
            throw new FileProcessingException(
                    ErrorMessages.format("%s: %s", ErrorMessages.PERSISTENCE_FAILED, e.getMessage()),
                    fileName, e
            );
        }
    }
}