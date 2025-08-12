package com.java.service.exports.generator;

import com.java.exception.ErrorMessages;
import com.java.exception.FileOperationException;
import com.java.exception.FileProcessingException;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.service.exports.formatter.ValueFormatter;
import com.java.util.PathResolver;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class CsvFileGenerator implements FileGenerator {

    private final PathResolver pathResolver;
    private final ValueFormatter valueFormatter;

    @Value("${export.batch-size:1000}")
    private int batchSize;

    @Override
    public Path generate(Stream<Map<String, Object>> data,
                         ExportTemplate template,
                         String fileName) throws FileOperationException {

        String operationId = UUID.randomUUID().toString();
        log.info("Starting CSV generation. OperationId: {}, FileName: {}",
                operationId, fileName);

        try {
            validateInputs(data, template, fileName);

            Path tempFile = pathResolver.createTempFile("export_", ".csv");
            writeCsvFile(tempFile, data, template, fileName, operationId);

            Path resultPath = moveToExportDirectory(tempFile, fileName, template);
            log.info("CSV generation completed. OperationId: {}, Path: {}", operationId, resultPath);

            return resultPath;

        } catch (FileOperationException | FileProcessingException e) {
            log.error("Export failed. OperationId: {}", operationId, e);
            throw e;
        } catch (IOException e) {
            String message = ErrorMessages.format("%s: %s",
                    ErrorMessages.FILE_NOT_FOUND, e.getMessage());
            throw new FileOperationException(message, e, null, operationId);
        } catch (Exception e) {
            String message = ErrorMessages.format("%s: %s",
                    ErrorMessages.GENERAL_ERROR, e.getMessage());
            throw new FileOperationException(message, e, null, operationId);
        }
    }

    private void writeCsvFile(Path tempFile,
                              Stream<Map<String, Object>> data,
                              ExportTemplate template,
                              String fileName,
                              String operationId) throws FileOperationException {

        List<ExportTemplateField> fields = getOrderedFields(template);
        if (fields.isEmpty()) {
            throw new FileOperationException(
                    ErrorMessages.TEMPLATE_FIELDS_REQUIRED, null, operationId
            );
        }

        List<String> headers = extractHeaders(fields);
        Charset charset = getCharset(template, fileName);

        try (Writer writer = Files.newBufferedWriter(tempFile, charset);
             CSVWriter csvWriter = createCsvWriter(writer, template)) {

            writeHeaders(csvWriter, headers, template);
            writeDataBatch(csvWriter, data, fields, headers, fileName);

        } catch (IOException e) {
            throw new FileProcessingException(
                    ErrorMessages.format("%s: %s", ErrorMessages.PROCESSING_FAILED, e.getMessage()),
                    fileName, e
            );
        }
    }

    private Charset getCharset(ExportTemplate template, String fileName) {
        String encoding = template.getCsvEncoding();
        if (encoding == null || encoding.trim().isEmpty()) {
            encoding = "UTF-8";
        }

        try {
            return Charset.forName(encoding);
        } catch (UnsupportedCharsetException e) {
            log.warn("Unsupported charset: {}, falling back to UTF-8", encoding);
            // Используем UTF-8 как fallback, но логируем предупреждение
            return Charset.forName("UTF-8");
        }
    }

    private CSVWriter createCsvWriter(Writer writer, ExportTemplate template) {
        // Валидация параметров CSV
        String delimiter = template.getCsvDelimiter();
        String quoteChar = template.getCsvQuoteChar();

        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = ",";
        }
        if (quoteChar == null || quoteChar.isEmpty()) {
            quoteChar = "\"";
        }

        return new CSVWriter(
                writer,
                delimiter.charAt(0),
                quoteChar.charAt(0),
                quoteChar.charAt(0),
                CSVWriter.DEFAULT_LINE_END
        );
    }

    private void writeHeaders(CSVWriter csvWriter,
                              List<String> headers,
                              ExportTemplate template) throws IOException {
        if (Boolean.TRUE.equals(template.getCsvIncludeHeader()) && !headers.isEmpty()) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            log.debug("Written {} headers", headers.size());
        }
    }

    private void writeDataBatch(CSVWriter csvWriter,
                                Stream<Map<String, Object>> data,
                                List<ExportTemplateField> fields,
                                List<String> headers,
                                String fileName) throws IOException {
        Iterator<Map<String, Object>> iterator = data.iterator();
        if (!iterator.hasNext()) {
            log.warn("No data to write for file: {}", fileName);
            return;
        }

        List<String[]> batch = new ArrayList<>();
        int processed = 0;
        long rowNumber = 1; // 1 for header row

        try {
            while (iterator.hasNext()) {
                Map<String, Object> row = iterator.next();
                rowNumber++;
                String[] values = extractRowValues(row, fields, headers, rowNumber, fileName);
                batch.add(values);
                processed++;

                if (batch.size() >= batchSize) {
                    csvWriter.writeAll(batch);
                    batch.clear();
                    log.debug("Written batch of {} rows, total processed: {}", batchSize, processed);
                }
            }

            if (!batch.isEmpty()) {
                csvWriter.writeAll(batch);
                log.debug("Written final batch of {} rows", batch.size());
            }

            log.info("Successfully written {} rows to CSV", processed);

        } catch (Exception e) {
            throw new FileProcessingException(
                    ErrorMessages.format("Error at row %d: %s", rowNumber, e.getMessage()),
                    fileName,
                    rowNumber
            );
        }
    }

    private String[] extractRowValues(Map<String, Object> row,
                                      List<ExportTemplateField> fields,
                                      List<String> headers,
                                      long rowNumber,
                                      String fileName) {
        String[] values = new String[headers.size()];
        int i = 0;

        for (ExportTemplateField field : fields) {
            try {
                Object value = row.get(field.getExportColumnName());
                values[i] = valueFormatter.format(value);
            } catch (Exception e) {
                log.warn("Failed to format value for field {} at row {}: {}",
                        field.getExportColumnName(), rowNumber, e.getMessage());
                values[i] = "";
            }
            i++;
        }

        return values;
    }

    @Override
    public boolean supports(String format) {
        return "CSV".equalsIgnoreCase(format);
    }

    private void validateInputs(Stream<Map<String, Object>> data,
                                ExportTemplate template,
                                String fileName) {
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

    private List<ExportTemplateField> getOrderedFields(ExportTemplate template) {
        return template.getFields().stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsIncluded()))
                .sorted(Comparator.comparing(ExportTemplateField::getFieldOrder))
                .collect(Collectors.toList());
    }

    private List<String> extractHeaders(List<ExportTemplateField> fields) {
        return fields.stream()
                .map(ExportTemplateField::getExportColumnName)
                .collect(Collectors.toList());
    }

    private Path moveToExportDirectory(Path tempFile, String fileName,
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