package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.BarcodeMatchDto;
import com.java.model.entity.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для сопоставления штрихкодов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BarcodeMatchService {

    private final ObjectMapper objectMapper;

    /**
     * Обработка сопоставления штрихкодов
     */
    public byte[] processBarcodeMatching(FileMetadata metadata, BarcodeMatchDto dto) throws IOException {

        // Читаем ВЕСЬ файл из tempFilePath, а не только sampleData
        List<List<String>> data = readFullFile(metadata);

        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для обработки");
        }

        // Валидация колонок
        validateColumns(data, dto);

        log.info("Processing barcode matching: total rows = {}, lookup index will be built", data.size());

        // Обработка данных
        List<BarcodeMatchResult> results = processData(data, dto);

        log.info("Barcode matching completed: {} matches found", results.size());

        // Генерация результирующего файла
        if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
            return generateCsvFile(results, dto);
        } else {
            return generateExcelFile(results);
        }
    }

    /**
     * Чтение всего файла из tempFilePath
     */
    private List<List<String>> readFullFile(FileMetadata metadata) throws IOException {
        String tempFilePath = metadata.getTempFilePath();
        String fileFormat = metadata.getFileFormat();

        if (tempFilePath == null || tempFilePath.isEmpty()) {
            throw new IllegalArgumentException("Временный файл не найден");
        }

        java.nio.file.Path path = java.nio.file.Paths.get(tempFilePath);
        if (!java.nio.file.Files.exists(path)) {
            throw new IllegalArgumentException("Файл не существует: " + tempFilePath);
        }

        if ("XLSX".equalsIgnoreCase(fileFormat) || "XLS".equalsIgnoreCase(fileFormat)) {
            return readExcelFile(path);
        } else if ("CSV".equalsIgnoreCase(fileFormat)) {
            return readCsvFile(path, metadata);
        } else {
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " + fileFormat);
        }
    }

    /**
     * Чтение Excel файла
     */
    private List<List<String>> readExcelFile(java.nio.file.Path path) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (java.io.FileInputStream fis = new java.io.FileInputStream(path.toFile());
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            boolean isFirstRow = true;

            for (Row row : sheet) {
                // Пропускаем первую строку если это заголовок
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                List<String> rowData = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    String value = (cell == null) ? "" : formatter.formatCellValue(cell);
                    rowData.add(value);
                }
                data.add(rowData);
            }
        }

        log.debug("Read {} rows from Excel file: {}", data.size(), path.getFileName());
        return data;
    }

    /**
     * Чтение CSV файла
     */
    private List<List<String>> readCsvFile(java.nio.file.Path path, FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        String encoding = metadata.getDetectedEncoding() != null ? metadata.getDetectedEncoding() : "UTF-8";
        String delimiter = metadata.getDetectedDelimiter() != null ? metadata.getDetectedDelimiter() : ";";

        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(path,
                java.nio.charset.Charset.forName(encoding))) {

            String line;
            boolean isFirstRow = true;

            while ((line = reader.readLine()) != null) {
                // Пропускаем первую строку если это заголовок
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                String[] values = line.split(delimiter);
                data.add(Arrays.asList(values));
            }
        }

        log.debug("Read {} rows from CSV file: {}", data.size(), path.getFileName());
        return data;
    }

    /**
     * Парсинг заголовков из JSON
     */
    private List<String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(headersJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга заголовков из JSON", e);
            return new ArrayList<>();
        }
    }

    /**
     * Парсинг данных из JSON
     */
    private List<List<String>> parseSampleData(String dataJson) {
        if (dataJson == null || dataJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(dataJson, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга данных из JSON", e);
            return new ArrayList<>();
        }
    }

    /**
     * Валидация выбранных колонок
     */
    private void validateColumns(List<List<String>> data, BarcodeMatchDto dto) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для обработки");
        }

        int maxColumns = data.get(0).size();
        
        if (dto.getSourceIdColumn() >= maxColumns || 
            dto.getSourceBarcodesColumn() >= maxColumns || 
            dto.getLookupBarcodesColumn() >= maxColumns || 
            dto.getLookupUrlColumn() >= maxColumns) {
            throw new IllegalArgumentException("Выбранные колонки превышают количество колонок в файле");
        }

        // Проверка на уникальность колонок
        Set<Integer> columns = Set.of(
            dto.getSourceIdColumn(), 
            dto.getSourceBarcodesColumn(), 
            dto.getLookupBarcodesColumn(), 
            dto.getLookupUrlColumn()
        );
        if (columns.size() != 4) {
            throw new IllegalArgumentException("Все колонки должны быть разными");
        }
    }

    /**
     * Обработка данных с cross-reference поиском
     */
    private List<BarcodeMatchResult> processData(List<List<String>> data, BarcodeMatchDto dto) {
        List<BarcodeMatchResult> results = new ArrayList<>();
        
        // Создаем индекс справочных данных: штрихкод -> URL
        Map<String, String> lookupIndex = buildLookupIndex(data, dto);
        
        // Обрабатываем каждую строку с исходными данными
        for (List<String> row : data) {
            if (row.size() <= Math.max(Math.max(dto.getSourceIdColumn(), dto.getSourceBarcodesColumn()), 
                                      Math.max(dto.getLookupBarcodesColumn(), dto.getLookupUrlColumn()))) {
                continue;
            }

            String sourceId = getColumnValue(row, dto.getSourceIdColumn());
            String sourceBarcodesStr = getColumnValue(row, dto.getSourceBarcodesColumn());

            if (isEmpty(sourceId) || isEmpty(sourceBarcodesStr)) {
                continue;
            }

            // Парсинг исходных штрихкодов
            List<String> sourceBarcodes = parseBarcodes(sourceBarcodesStr);
            
            for (String sourceBarcode : sourceBarcodes) {
                if (isValidBarcode(sourceBarcode)) {
                    String trimmedBarcode = sourceBarcode.trim();
                    
                    // Ищем совпадение в справочном индексе
                    String matchedUrl = lookupIndex.get(trimmedBarcode);
                    if (matchedUrl != null) {
                        results.add(new BarcodeMatchResult(sourceId, trimmedBarcode, matchedUrl));
                    } else {
                        log.debug("No match found for barcode: {} from ID: {}", trimmedBarcode, sourceId);
                    }
                } else {
                    log.debug("Invalid barcode skipped: {} for ID: {}", sourceBarcode, sourceId);
                }
            }
        }

        return results;
    }
    
    /**
     * Создание индекса справочных данных: штрихкод -> URL
     */
    private Map<String, String> buildLookupIndex(List<List<String>> data, BarcodeMatchDto dto) {
        Map<String, String> index = new HashMap<>();
        
        for (List<String> row : data) {
            if (row.size() <= Math.max(dto.getLookupBarcodesColumn(), dto.getLookupUrlColumn())) {
                continue;
            }
            
            String lookupBarcodesStr = getColumnValue(row, dto.getLookupBarcodesColumn());
            String lookupUrl = getColumnValue(row, dto.getLookupUrlColumn());
            
            if (isEmpty(lookupBarcodesStr) || isEmpty(lookupUrl)) {
                continue;
            }
            
            // Парсинг справочных штрихкодов
            List<String> lookupBarcodes = parseBarcodes(lookupBarcodesStr);
            
            for (String barcode : lookupBarcodes) {
                if (isValidBarcode(barcode)) {
                    index.put(barcode.trim(), lookupUrl);
                }
            }
        }
        
        log.debug("Built lookup index with {} barcode entries", index.size());
        return index;
    }

    /**
     * Получение значения колонки с защитой от IndexOutOfBounds
     */
    private String getColumnValue(List<String> row, int columnIndex) {
        if (columnIndex >= 0 && columnIndex < row.size()) {
            return row.get(columnIndex);
        }
        return "";
    }

    /**
     * Проверка на пустое значение
     */
    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Парсинг штрихкодов из строки (поддержка множественных через запятую)
     */
    private List<String> parseBarcodes(String barcodesStr) {
        if (isEmpty(barcodesStr)) {
            return Collections.emptyList();
        }

        return Arrays.stream(barcodesStr.split(","))
                .map(String::trim)
                .filter(barcode -> !barcode.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Валидация штрихкода (13 символов, только цифры)
     */
    private boolean isValidBarcode(String barcode) {
        if (isEmpty(barcode)) {
            return false;
        }

        String trimmed = barcode.trim();
        return trimmed.length() == 13 && trimmed.matches("\\d{13}");
    }

    /**
     * Генерация CSV файла
     */
    private byte[] generateCsvFile(List<BarcodeMatchResult> results, BarcodeMatchDto dto) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, 
                 "UTF-8".equalsIgnoreCase(dto.getCsvEncoding()) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1)) {
            
            String delimiter = dto.getCsvDelimiter();
            
            // Заголовки
            writer.write("ID" + delimiter + "Штрихкод" + delimiter + "URL\n");
            
            // Данные
            for (BarcodeMatchResult result : results) {
                writer.write(escapeCSV(result.getId(), delimiter) + delimiter + 
                           escapeCSV(result.getBarcode(), delimiter) + delimiter + 
                           escapeCSV(result.getUrl(), delimiter) + "\n");
            }
            
            writer.flush();
            return out.toByteArray();
        }
    }
    
    /**
     * Экранирование значений для CSV
     */
    private String escapeCSV(String value, String delimiter) {
        if (value == null) return "";
        
        if (value.contains(delimiter) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }

    /**
     * Генерация результирующего Excel файла
     */
    private byte[] generateExcelFile(List<BarcodeMatchResult> results) throws IOException {
        
        try (Workbook workbook = new XSSFWorkbook(); 
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Результат сопоставления");
            
            // Создание стилей
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // Заголовки
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "Штрихкод", "URL"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Данные
            int rowIndex = 1;
            for (BarcodeMatchResult result : results) {
                Row dataRow = sheet.createRow(rowIndex++);
                
                Cell idCell = dataRow.createCell(0);
                idCell.setCellValue(result.getId());
                idCell.setCellStyle(dataStyle);
                
                Cell barcodeCell = dataRow.createCell(1);
                barcodeCell.setCellValue(result.getBarcode());
                barcodeCell.setCellStyle(dataStyle);
                
                Cell urlCell = dataRow.createCell(2);
                urlCell.setCellValue(result.getUrl());
                urlCell.setCellStyle(dataStyle);
            }
            
            // Автоподбор ширины колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Создание стиля для заголовков
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Создание стиля для данных
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    /**
     * Класс для хранения результата сопоставления
     */
    public static class BarcodeMatchResult {
        private final String id;
        private final String barcode;
        private final String url;

        public BarcodeMatchResult(String id, String barcode, String url) {
            this.id = id;
            this.barcode = barcode;
            this.url = url;
        }

        public String getId() { return id; }
        public String getBarcode() { return barcode; }
        public String getUrl() { return url; }
    }
}