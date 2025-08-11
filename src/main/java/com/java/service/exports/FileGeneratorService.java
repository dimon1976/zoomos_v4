package com.java.service.exports;

import com.java.exception.ErrorMessages;
import com.java.exception.FileOperationException;
import com.java.exception.ImportQuotaExceededException;
import com.java.model.entity.ExportTemplate;
import com.java.service.exports.generator.FileGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileGeneratorService {

    private final List<FileGenerator> generators;

    @Value("${export.xlsx.max-rows:1048576}")
    private int xlsxMaxRows;

    @Value("${export.enable-auto-format-switch:true}")
    private boolean enableAutoFormatSwitch;

    @Transactional(readOnly = true)
    public Path generateFile(List<Map<String, Object>> data,
                             ExportTemplate template,
                             String fileName) throws FileOperationException {

        String operationId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        log.info("Starting file generation. OperationId: {}, Format: {}, Records: {}, FileName: {}",
                operationId, template.getFileFormat(), data.size(), fileName);

        try {
            // Валидация входных данных
            validateInputs(data, template, fileName, operationId);

            // Определение формата с учетом ограничений
            String format = determineFormat(data, template, operationId);

            // Поиск подходящего генератора
            FileGenerator generator = findGenerator(format, operationId);

            // Генерация файла
            Path result = generator.generate(data, template, fileName);

            long duration = System.currentTimeMillis() - startTime;
            log.info("File generation completed. OperationId: {}, Duration: {}ms, Path: {}",
                    operationId, duration, result);

            return result;

        } catch (FileOperationException e) {
            // Пробрасываем как есть, уже содержит operationId
            throw e;
        } catch (ImportQuotaExceededException e) {
            // Превышение квоты
            log.error("Quota exceeded. OperationId: {}", operationId, e);
            throw e;
        } catch (Exception e) {
            // Неожиданная ошибка
            log.error("Unexpected error during file generation. OperationId: {}", operationId, e);
            throw new FileOperationException(
                    ErrorMessages.format("%s: %s", ErrorMessages.GENERAL_ERROR, e.getMessage()),
                    e, null, operationId
            );
        }
    }

    private void validateInputs(List<Map<String, Object>> data,
                                ExportTemplate template,
                                String fileName,
                                String operationId) {

        if (data == null) {
            throw new FileOperationException(
                    ErrorMessages.format("%s: data", ErrorMessages.REQUIRED_FIELD_EMPTY),
                    null, operationId
            );
        }

        if (template == null) {
            throw new FileOperationException(
                    ErrorMessages.TEMPLATE_NOT_FOUND,
                    null, operationId
            );
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new FileOperationException(
                    ErrorMessages.format("%s: fileName", ErrorMessages.REQUIRED_FIELD_EMPTY),
                    null, operationId
            );
        }

        if (template.getFileFormat() == null || template.getFileFormat().trim().isEmpty()) {
            throw new FileOperationException(
                    ErrorMessages.format("%s: fileFormat", ErrorMessages.REQUIRED_FIELD_EMPTY),
                    null, operationId
            );
        }

        // Проверка наличия полей в шаблоне
        if (template.getFields() == null || template.getFields().isEmpty()) {
            throw new FileOperationException(
                    ErrorMessages.TEMPLATE_FIELDS_REQUIRED,
                    null, operationId
            );
        }

        // Проверка, что есть хотя бы одно включенное поле
        boolean hasIncludedFields = template.getFields().stream()
                .anyMatch(f -> Boolean.TRUE.equals(f.getIsIncluded()));

        if (!hasIncludedFields) {
            throw new FileOperationException(
                    ErrorMessages.TEMPLATE_FIELDS_REQUIRED,
                    null, operationId
            );
        }

        log.debug("Input validation passed. OperationId: {}", operationId);
    }

    private String determineFormat(List<Map<String, Object>> data,
                                   ExportTemplate template,
                                   String operationId) {
        String format = template.getFileFormat().toUpperCase();

        // Автоматическое переключение на CSV при превышении лимита XLSX
        if ("XLSX".equals(format) && data.size() > xlsxMaxRows) {
            if (enableAutoFormatSwitch) {
                log.warn("Data size {} exceeds XLSX limit {}, switching to CSV. OperationId: {}",
                        data.size(), xlsxMaxRows, operationId);
                template.setFileFormat("CSV");
                return "CSV";
            } else {
                // Если автопереключение отключено, выбрасываем исключение
                throw new ImportQuotaExceededException(
                        ErrorMessages.format(
                                "Data size %d exceeds XLSX maximum of %d rows",
                                data.size(), xlsxMaxRows
                        ),
                        xlsxMaxRows,
                        data.size()
                );
            }
        }

        return format;
    }

    private FileGenerator findGenerator(String format, String operationId) {
        return generators.stream()
                .filter(g -> g.supports(format))
                .findFirst()
                .orElseThrow(() -> new FileOperationException(
                        ErrorMessages.format("%s: %s", ErrorMessages.INVALID_FILE_FORMAT, format),
                        null, operationId
                ));
    }
}