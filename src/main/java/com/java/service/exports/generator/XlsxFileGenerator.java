package com.java.service.exports.generator;

import com.java.exception.ErrorMessages;
import com.java.exception.FileOperationException;
import com.java.exception.FileProcessingException;
import com.java.exception.ImportQuotaExceededException;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.service.exports.formatter.ValueFormatter;
import com.java.service.exports.style.ExcelStyleFactory;
import com.java.service.exports.style.ExcelStyles;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@RequiredArgsConstructor
public class XlsxFileGenerator implements FileGenerator {

    private final PathResolver pathResolver;
    private final ValueFormatter valueFormatter;
    private final ExcelStyleFactory styleFactory;

    @Value("${export.batch-size:1000}")
    private int batchSize;

    @Value("${export.xlsx.max-rows:1048576}")
    private int xlsxMaxRows;

    @Override
    public Path generate(Stream<Map<String, Object>> data,
                         ExportTemplate template,
                         String fileName) throws FileOperationException {

        List<Map<String, Object>> dataList = data.toList();

        String operationId = UUID.randomUUID().toString();
        log.info("Starting XLSX generation. OperationId: {}, FileName: {}, Records: {}",
                operationId, fileName, dataList.size());

        try {
            validateInputs(dataList, template, fileName);
            checkDataSizeLimit(dataList.size(), fileName);

            Path tempFile = pathResolver.createTempFile("export_", ".xlsx");
            writeXlsxFile(tempFile, dataList, template, fileName, operationId);

            Path resultPath = moveToExportDirectory(tempFile, fileName, template);
            log.info("XLSX generation completed. OperationId: {}, Path: {}", operationId, resultPath);

            return resultPath;

        } catch (FileOperationException | FileProcessingException | ImportQuotaExceededException e) {
            log.error("Export failed. OperationId: {}", operationId, e);
            throw e;
        } catch (OutOfMemoryError e) {
            String message = ErrorMessages.OUT_OF_MEMORY;
            throw new FileOperationException(message, e, null, operationId);
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

    private void checkDataSizeLimit(int dataSize, String fileName) {
        if (dataSize > xlsxMaxRows) {
            throw new ImportQuotaExceededException(
                    ErrorMessages.format(
                            "Data size %d exceeds XLSX limit of %d rows. Please use CSV format for large exports.",
                            dataSize, xlsxMaxRows
                    ),
                    xlsxMaxRows,
                    dataSize
            );
        }
    }

    private void writeXlsxFile(Path tempFile,
                               List<Map<String, Object>> data,
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

        // SXSSFWorkbook(rowAccessWindowSize = batchSize)
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(batchSize);
             FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {

            // Сжимаем временные файлы SXSSF (рекомендуется для больших выгрузок)
            workbook.setCompressTempFiles(true);

            Sheet sheet = createSheet(workbook, template);
            ExcelStyles styles = styleFactory.createStyles(workbook);

            int headerRowIndex = writeHeaders(sheet, headers, styles);
            int lastDataRow = writeData(sheet, data, fields, styles, headerRowIndex + 1, fileName);

            // Автофильтр
            if (headerRowIndex >= 0 && !data.isEmpty()) {
                applyAutoFilter(sheet, headerRowIndex, headers.size(), lastDataRow);
            }

            // Подбор ширины колонок: в SXSSF лучше избегать полного autoSize
            if (shouldAutoSizeColumns(template, headers.size())) {
                // Варианты:
                // 1) ограничить анализ первыми N строками
                // 2) выставить ширину по заголовкам + небольшой запас
                // 3) полностью отключить при больших наборах
                adjustColumnWidthsSafely(sheet, headers);
            }

            workbook.write(fos);
            log.info("Successfully written {} rows to XLSX", data.size());

            // Явно удаляем временные файлы SXSSF
            workbook.dispose();

        } catch (IOException e) {
            throw new FileProcessingException(
                    ErrorMessages.format("%s: %s", ErrorMessages.PROCESSING_FAILED, e.getMessage()),
                    fileName, e
            );
        } catch (OutOfMemoryError e) {
            throw new FileOperationException(
                    ErrorMessages.OUT_OF_MEMORY, e, null, operationId
            );
        }
    }

    private void adjustColumnWidthsSafely(Sheet sheet, List<String> headers) {
        // Простая эвристика: ширина = длина заголовка + запас
        final int charWidth = 256;  // единица измерения ширины колонки в POI
        for (int i = 0; i < headers.size(); i++) {
            int len = Math.max(10, headers.get(i) != null ? headers.get(i).length() : 10);
            int width = Math.min(255, len + 5) * charWidth;
            sheet.setColumnWidth(i, width);
        }
    }

    private Sheet createSheet(SXSSFWorkbook workbook, ExportTemplate template) {
        String sheetName = template.getXlsxSheetName();
        if (sheetName == null || sheetName.trim().isEmpty()) {
            sheetName = "Export";
        }

        // Валидация имени листа (Excel ограничения)
        sheetName = validateSheetName(sheetName);

        try {
            return workbook.createSheet(sheetName);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sheet name '{}', using default", sheetName);
            return workbook.createSheet("Export");
        }
    }

    private String validateSheetName(String name) {
        // Excel не позволяет некоторые символы в названии листа
        String cleaned = name.replaceAll("[\\[\\]\\*\\?\\/\\\\:]", "_");

        // Максимальная длина - 31 символ
        if (cleaned.length() > 31) {
            cleaned = cleaned.substring(0, 31);
        }

        return cleaned;
    }

    private int writeHeaders(Sheet sheet, List<String> headers, ExcelStyles styles) {
        if (headers.isEmpty()) {
            return -1;
        }

        try {
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.getHeaderStyle());
            }

            // Закрепляем строку заголовков
            sheet.createFreezePane(0, 1);

            log.debug("Written {} headers", headers.size());
            return 0;

        } catch (Exception e) {
            log.error("Failed to write headers", e);
            throw new FileOperationException(
                    ErrorMessages.format("Failed to write headers: %s", e.getMessage())
            );
        }
    }

    private void applyAutoFilter(Sheet sheet, int headerRow, int columnCount, int lastDataRow) {
        try {
            if (lastDataRow > headerRow) {
                CellRangeAddress range = new CellRangeAddress(
                        headerRow,           // первая строка (заголовки)
                        lastDataRow,         // последняя строка с данными
                        0,                   // первая колонка
                        columnCount - 1      // последняя колонка
                );
                sheet.setAutoFilter(range);
                log.debug("Applied auto filter for range: rows {}-{}, columns 0-{}",
                        headerRow, lastDataRow, columnCount - 1);
            }
        } catch (Exception e) {
            // Не критичная ошибка - логируем и продолжаем
            log.warn("Failed to apply auto filter: {}", e.getMessage());
        }
    }

    private int writeData(Sheet sheet,
                          List<Map<String, Object>> data,
                          List<ExportTemplateField> fields,
                          ExcelStyles styles,
                          int startRow,
                          String fileName) {
        int rowIndex = startRow;
        long processedRows = 0;

        try {
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowIndex);
                writeRowData(row, rowData, fields, styles, rowIndex, fileName);

                rowIndex++;
                processedRows++;

                if (processedRows % batchSize == 0) {
                    log.debug("Processed {} rows", processedRows);

                    // Флашим именно лист (SXSSFSheet), оставляя в памяти последние 100 строк
                    if (processedRows % (batchSize * 10L) == 0 && sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet s) {
                        s.flushRows(100);
                    }
                }
            }
            return rowIndex - 1;
        } catch (Exception e) {
            throw new FileProcessingException(
                    ErrorMessages.format("Error at row %d: %s", processedRows + 1, e.getMessage()),
                    fileName,
                    processedRows + 1
            );
        }
    }

    private void writeRowData(Row row,
                              Map<String, Object> rowData,
                              List<ExportTemplateField> fields,
                              ExcelStyles styles,
                              int rowNumber,
                              String fileName) {
        int colIndex = 0;

        for (ExportTemplateField field : fields) {
            try {
                Cell cell = row.createCell(colIndex);
                Object value = rowData.get(field.getExportColumnName());
                setCellValue(cell, value, styles);
                colIndex++;
            } catch (Exception e) {
                log.warn("Failed to write cell at row {}, column {}: {}",
                        rowNumber, colIndex, e.getMessage());
                // Продолжаем обработку остальных ячеек
                colIndex++;
            }
        }
    }

    private void setCellValue(Cell cell, Object value, ExcelStyles styles) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(styles.getNumberStyle());
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(styles.getDateStyle());
        } else if (value instanceof java.time.LocalDateTime) {
            cell.setCellValue(java.sql.Timestamp.valueOf((java.time.LocalDateTime) value));
            cell.setCellStyle(styles.getDateStyle());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            String stringValue = valueFormatter.format(value);
            // Excel имеет лимит в 32767 символов на ячейку
            if (stringValue.length() > 32767) {
                stringValue = stringValue.substring(0, 32764) + "...";
                log.warn("Cell value truncated due to Excel limit");
            }
            cell.setCellValue(stringValue);
        }
    }

    private boolean shouldAutoSizeColumns(ExportTemplate template, int columnCount) {
        // Не делаем автоподбор для большого количества колонок (производительность)
        return Boolean.TRUE.equals(template.getXlsxAutoSizeColumns()) && columnCount < 50;
    }

    private void adjustColumnWidths(Sheet sheet,
                                    List<String> headers,
                                    List<Map<String, Object>> data,
                                    List<ExportTemplateField> fields) {
        try {
            Map<Integer, Integer> maxWidths = calculateMaxWidths(headers, data, fields);

            for (Map.Entry<Integer, Integer> entry : maxWidths.entrySet()) {
                int width = Math.min((entry.getValue() + 2) * 256, 255 * 256);
                sheet.setColumnWidth(entry.getKey(), width);
            }
        } catch (Exception e) {
            // Не критичная ошибка
            log.warn("Failed to adjust column widths: {}", e.getMessage());
        }
    }

    private Map<Integer, Integer> calculateMaxWidths(List<String> headers,
                                                     List<Map<String, Object>> data,
                                                     List<ExportTemplateField> fields) {
        Map<Integer, Integer> maxWidths = new HashMap<>();

        // Учитываем заголовки
        for (int i = 0; i < headers.size(); i++) {
            maxWidths.put(i, headers.get(i).length());
        }

        // Учитываем данные (берем sample для оптимизации)
        int sampleSize = Math.min(data.size(), 100);
        for (int i = 0; i < sampleSize; i++) {
            Map<String, Object> row = data.get(i);
            int colIndex = 0;

            for (ExportTemplateField field : fields) {
                Object value = row.get(field.getExportColumnName());
                String formatted = valueFormatter.format(value);
                int length = formatted != null ? Math.min(formatted.length(), 50) : 0;
                maxWidths.merge(colIndex, length, Math::max);
                colIndex++;
            }
        }

        return maxWidths;
    }

    @Override
    public boolean supports(String format) {
        return "XLSX".equalsIgnoreCase(format);
    }

    private void validateInputs(List<Map<String, Object>> data,
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

    // Остальные методы аналогичны CsvFileGenerator
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