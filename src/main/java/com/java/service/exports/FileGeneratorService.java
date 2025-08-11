package com.java.service.exports;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.util.PathResolver;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для генерации файлов экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileGeneratorService {

    private final PathResolver pathResolver;
    private static final String DEFAULT_FONT_NAME = "Calibri";

    @Value("${export.xlsx.max-rows:1048576}")
    private int xlsxMaxRows;

    @Value("${export.batch-size:1000}")
    private int batchSize;

    /**
     * Генерирует файл экспорта
     */
    public Path generateFile(
            List<Map<String, Object>> data,
            ExportTemplate template,
            String fileName) throws IOException {

        log.info("Генерация файла {}, формат: {}, записей: {}",
                fileName, template.getFileFormat(), data.size());
        if (!data.isEmpty()) {
            log.debug("Первая строка для записи: {}", data.get(0));
        }

        // Проверяем, не превышает ли количество строк лимит для XLSX
        if ("XLSX".equalsIgnoreCase(template.getFileFormat()) &&
                data.size() > xlsxMaxRows) {
            log.warn("Количество строк {} превышает лимит XLSX {}, переключаемся на CSV",
                    data.size(), xlsxMaxRows);
            template.setFileFormat("CSV");
        }

        Path filePath;

        if ("CSV".equalsIgnoreCase(template.getFileFormat())) {
            filePath = generateCsvFile(data, template, fileName);
        } else if ("XLSX".equalsIgnoreCase(template.getFileFormat())) {
            filePath = generateXlsxFile(data, template, fileName);
        } else {
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " +
                    template.getFileFormat());
        }

        log.info("Файл сгенерирован: {}", filePath);
        return filePath;
    }

    /**
     * Получает список полей, включенных в экспорт, отсортированных по fieldOrder
     */
    private List<ExportTemplateField> getOrderedFields(ExportTemplate template) {
        return template.getFields().stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsIncluded()))
                .sorted(Comparator.comparing(ExportTemplateField::getFieldOrder))
                .collect(Collectors.toList());
    }

    /**
     * Генерирует CSV файл
     */
    private Path generateCsvFile(
            List<Map<String, Object>> data,
            ExportTemplate template,
            String fileName) throws IOException {

        Path tempFile = pathResolver.createTempFile("export_", ".csv");

        List<ExportTemplateField> fields = getOrderedFields(template);
        List<String> headers = fields.stream()
                .map(ExportTemplateField::getExportColumnName)
                .toList();

        try (Writer writer = Files.newBufferedWriter(tempFile,
                Charset.forName(template.getCsvEncoding()));
             CSVWriter csvWriter = new CSVWriter(writer,
                     template.getCsvDelimiter().charAt(0),
                     template.getCsvQuoteChar().charAt(0),
                     template.getCsvQuoteChar().charAt(0),  // escape char
                     CSVWriter.DEFAULT_LINE_END)) {

            if (template.getCsvIncludeHeader() && !headers.isEmpty()) {
                csvWriter.writeNext(headers.toArray(new String[0]));
            }
            if (!data.isEmpty()) {
                // Записываем данные батчами
                int processed = 0;
                List<String[]> batch = new ArrayList<>();

                for (Map<String, Object> row : data) {
                    String[] values = new String[headers.size()];
                    int i = 0;

                    for (ExportTemplateField field : fields) {
                        Object value = row.get(field.getExportColumnName());
                        values[i++] = formatValue(value);
                    }

                    batch.add(values);
                    processed++;

                    // Записываем батч
                    if (batch.size() >= batchSize) {
                        csvWriter.writeAll(batch);
                        batch.clear();
                        log.debug("Записано {} строк", processed);
                    }
                }

                // Записываем остаток
                if (!batch.isEmpty()) {
                    csvWriter.writeAll(batch);
                }
            }
        }

        // Перемещаем во временную директорию экспорта
        boolean hasTemplate = template.getFilenameTemplate() != null
                && !template.getFilenameTemplate().trim().isEmpty();
        return pathResolver.moveFromTempToExport(tempFile, fileName, !hasTemplate);
    }

    /**
     * Генерирует XLSX файл
     */
    private Path generateXlsxFile(
            List<Map<String, Object>> data,
            ExportTemplate template,
            String fileName) throws IOException {

        Path tempFile = pathResolver.createTempFile("export_", ".xlsx");
        List<ExportTemplateField> fields = getOrderedFields(template);
        List<String> headers = fields.stream()
                .map(ExportTemplateField::getExportColumnName)
                .toList();

        // Карта максимальных длин по колонкам для ручного расчета ширины
        Map<Integer, Integer> maxColumnWidths = new HashMap<>();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(batchSize);
             FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {

            Sheet sheet = workbook.createSheet(template.getXlsxSheetName());

            // Стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;
            for (String header : headers) {
                Cell cell = headerRow.createCell(colIndex);
                cell.setCellValue(header);
                cell.setCellStyle(headerStyle);
                maxColumnWidths.put(colIndex, header.length());
                colIndex++;
            }

            // Данные
            int rowIndex = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowIndex++);
                colIndex = 0;

                for (ExportTemplateField field : fields) {
                    Cell cell = row.createCell(colIndex);
                    Object value = rowData.get(field.getExportColumnName());
                    String stringValue = formatValue(value);
                    cell.setCellValue(stringValue);
                    applyCellStyle(cell, value, dateStyle, numberStyle);

                    int len = stringValue != null ? stringValue.length() : 0;
                    maxColumnWidths.merge(colIndex, len, Math::max);

                    colIndex++;
                }

                if (rowIndex % batchSize == 0) {
                    log.debug("Обработано {} строк", rowIndex);
                }
            }

            // Автоподбор ширины колонок вручную
            if (template.getXlsxAutoSizeColumns() && headers.size() < 50) {
                for (Map.Entry<Integer, Integer> entry : maxColumnWidths.entrySet()) {
                    int width = (entry.getValue() + 2) * 256; // запас 2 символа
                    sheet.setColumnWidth(entry.getKey(), Math.min(width, 255 * 256));
                }
            }

            workbook.write(fos);
        }

        boolean hasTemplate = template.getFilenameTemplate() != null
                && !template.getFilenameTemplate().trim().isEmpty();
        return pathResolver.moveFromTempToExport(tempFile, fileName, !hasTemplate);
    }


    private void applyCellStyle(Cell cell, Object value, CellStyle dateStyle, CellStyle numberStyle) {
        if (value instanceof Date) {
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Number) {
            cell.setCellStyle(numberStyle);
        }
    }

    /**
     * Форматирует значение для CSV
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }
        return value.toString();
    }

    /**
     * Устанавливает значение ячейки Excel
     */
    private void setCellValue(Cell cell, Object value,
                              CellStyle dateStyle, CellStyle numberStyle) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(numberStyle);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(java.sql.Timestamp.valueOf((LocalDateTime) value));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Создает стиль для заголовков
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName(DEFAULT_FONT_NAME);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Создает стиль для дат
     */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat("dd.mm.yyyy"));
        return style;
    }

    /**
     * Создает стиль для чисел
     */
    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));
        return style;
    }
}