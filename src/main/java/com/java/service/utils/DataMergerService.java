package com.java.service.utils;

import com.java.dto.utils.DataMergerConfigDto;
import com.java.dto.utils.MergedProductDto;
import com.java.dto.utils.ProductLinksDto;
import com.java.dto.utils.SourceProductDto;
import com.java.exception.DataMergerException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Сервис для объединения данных товаров с аналогами и ссылками
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataMergerService {

    private final FileAnalyzerService fileAnalyzerService;
    private final ObjectMapper objectMapper;

    /**
     * Основной метод обработки файлов с использованием конфигурации маппинга
     */
    public List<MergedProductDto> processFiles(MultipartFile sourceFile, MultipartFile linksFile, DataMergerConfigDto config) {
        log.info("Starting data merge with custom mapping: source={} ({}), links={} ({})",
                sourceFile.getOriginalFilename(), sourceFile.getSize(),
                linksFile.getOriginalFilename(), linksFile.getSize());

        try {
            // 1. Парсим исходный файл с использованием конфигурации
            List<SourceProductDto> sourceData = parseSourceFileWithMapping(sourceFile, config);
            log.info("Parsed {} source records with custom mapping", sourceData.size());

            // 2. Парсим файл ссылок с использованием конфигурации
            List<ProductLinksDto> linksData = parseLinksFileWithMapping(linksFile, config);
            log.info("Parsed {} link records with custom mapping", linksData.size());

            // 3. Объединяем данные
            List<MergedProductDto> result = mergeData(sourceData, linksData);
            log.info("Generated {} merged records", result.size());

            return result;

        } catch (Exception e) {
            log.error("Error processing files with custom mapping: {}", e.getMessage(), e);
            throw new DataMergerException("Ошибка обработки файлов: " + e.getMessage(), e);
        }
    }

    /**
     * Основной метод обработки файлов (обратная совместимость)
     */
    public List<MergedProductDto> processFiles(MultipartFile sourceFile, MultipartFile linksFile) {
        log.info("Starting data merge: source={} ({}), links={} ({})",
                sourceFile.getOriginalFilename(), sourceFile.getSize(),
                linksFile.getOriginalFilename(), linksFile.getSize());

        try {
            // 1. Парсим исходный файл
            List<SourceProductDto> sourceData = parseSourceFile(sourceFile);
            log.info("Parsed {} source records", sourceData.size());

            // 2. Парсим файл ссылок
            List<ProductLinksDto> linksData = parseLinksFile(linksFile);
            log.info("Parsed {} link records", linksData.size());

            // 3. Объединяем данные
            List<MergedProductDto> result = mergeData(sourceData, linksData);
            log.info("Generated {} merged records", result.size());

            return result;

        } catch (Exception e) {
            log.error("Error processing files: {}", e.getMessage(), e);
            throw new DataMergerException("Ошибка обработки файлов: " + e.getMessage(), e);
        }
    }

    /**
     * Парсинг исходного файла с товарами-оригиналами и аналогами
     * Ожидаемые столбцы: ID, Модель оригинал, Модель аналог, Коэффициент
     */
    private List<SourceProductDto> parseSourceFile(MultipartFile file) throws IOException {
        log.debug("Parsing source file: {}", file.getOriginalFilename());

        List<SourceProductDto> result = new ArrayList<>();
        String fileName = file.getOriginalFilename().toLowerCase();

        if (fileName.endsWith(".csv")) {
            result = parseCsvSourceFile(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            result = parseExcelSourceFile(file);
        } else {
            throw new DataMergerException("Неподдерживаемый формат файла: " + fileName);
        }

        log.debug("Parsed {} source records", result.size());
        return result;
    }

    /**
     * Парсинг файла ссылок с аналогами и ссылками
     * Ожидаемые столбцы: Модель аналог, Ссылка
     */
    private List<ProductLinksDto> parseLinksFile(MultipartFile file) throws IOException {
        log.debug("Parsing links file: {}", file.getOriginalFilename());

        List<ProductLinksDto> result = new ArrayList<>();
        String fileName = file.getOriginalFilename().toLowerCase();

        if (fileName.endsWith(".csv")) {
            result = parseCsvLinksFile(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            result = parseExcelLinksFile(file);
        } else {
            throw new DataMergerException("Неподдерживаемый формат файла: " + fileName);
        }

        log.debug("Parsed {} link records", result.size());
        return result;
    }

    /**
     * Парсинг CSV файла с исходными данными
     */
    private List<SourceProductDto> parseCsvSourceFile(MultipartFile file) throws IOException {
        List<SourceProductDto> result = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(',')
                    .withIgnoreQuotations(false)
                    .build();

            try (CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .withSkipLines(1) // Пропускаем заголовок
                    .build()) {

                String[] line;
                int rowNum = 1;

                while ((line = csvReader.readNext()) != null) {
                    rowNum++;

                    if (line.length < 4) {
                        log.warn("Skipping row {} - insufficient columns: {}", rowNum, line.length);
                        continue;
                    }

                    try {
                        SourceProductDto dto = SourceProductDto.builder()
                                .id(line[0].trim())
                                .originalModel(line[1].trim())
                                .analogModel(line[2].trim())
                                .coefficient(parseDouble(line[3].trim()))
                                .build();

                        result.add(dto);
                    } catch (Exception e) {
                        log.warn("Error parsing row {}: {}", rowNum, e.getMessage());
                    }
                }
            } catch (CsvValidationException e) {
                throw new IOException("CSV validation error: " + e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Парсинг Excel файла с исходными данными
     */
    private List<SourceProductDto> parseExcelSourceFile(MultipartFile file) throws IOException {
        List<SourceProductDto> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Пропускаем заголовок
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    SourceProductDto dto = SourceProductDto.builder()
                            .id(getCellValueAsString(row.getCell(0)))
                            .originalModel(getCellValueAsString(row.getCell(1)))
                            .analogModel(getCellValueAsString(row.getCell(2)))
                            .coefficient(getCellValueAsDouble(row.getCell(3)))
                            .build();

                    result.add(dto);
                } catch (Exception e) {
                    log.warn("Error parsing Excel row {}: {}", i + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Парсинг CSV файла со ссылками
     */
    private List<ProductLinksDto> parseCsvLinksFile(MultipartFile file) throws IOException {
        List<ProductLinksDto> result = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(',')
                    .withIgnoreQuotations(false)
                    .build();

            try (CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .withSkipLines(1) // Пропускаем заголовок
                    .build()) {

                String[] line;
                int rowNum = 1;

                while ((line = csvReader.readNext()) != null) {
                    rowNum++;

                    if (line.length < 2) {
                        log.warn("Skipping row {} - insufficient columns: {}", rowNum, line.length);
                        continue;
                    }

                    try {
                        ProductLinksDto dto = ProductLinksDto.builder()
                                .analogModel(line[0].trim())
                                .link(line[1].trim())
                                .build();

                        result.add(dto);
                    } catch (Exception e) {
                        log.warn("Error parsing row {}: {}", rowNum, e.getMessage());
                    }
                }
            } catch (CsvValidationException e) {
                throw new IOException("CSV validation error: " + e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Парсинг Excel файла со ссылками
     */
    private List<ProductLinksDto> parseExcelLinksFile(MultipartFile file) throws IOException {
        List<ProductLinksDto> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Пропускаем заголовок
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ProductLinksDto dto = ProductLinksDto.builder()
                            .analogModel(getCellValueAsString(row.getCell(0)))
                            .link(getCellValueAsString(row.getCell(1)))
                            .build();

                    result.add(dto);
                } catch (Exception e) {
                    log.warn("Error parsing Excel row {}: {}", i + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Объединение данных по ключу (модель аналога)
     */
    private List<MergedProductDto> mergeData(List<SourceProductDto> sourceData, List<ProductLinksDto> linksData) {
        log.info("Merging data: {} source records with {} link records", sourceData.size(), linksData.size());

        // Логируем примеры данных для отладки
        if (!sourceData.isEmpty()) {
            SourceProductDto first = sourceData.get(0);
            log.info("Sample source record: id='{}', originalModel='{}', analogModel='{}', coefficient='{}'",
                first.getId(), first.getOriginalModel(), first.getAnalogModel(), first.getCoefficient());
        }

        if (!linksData.isEmpty()) {
            ProductLinksDto first = linksData.get(0);
            log.info("Sample links record: analogModel='{}', link='{}'",
                first.getAnalogModel(), first.getLink());
        }

        // Группируем ссылки по модели аналога
        Map<String, List<String>> linkMap = linksData.stream()
                .collect(groupingBy(ProductLinksDto::getAnalogModel,
                        mapping(ProductLinksDto::getLink, toList())));

        log.info("Created link map for {} unique analogs: {}", linkMap.size(), linkMap.keySet());

        // Создаем развернутый результат
        List<MergedProductDto> result = sourceData.stream()
                .flatMap(source -> createMergedRecords(source, linkMap))
                .collect(toList());

        return result;
    }

    /**
     * Создание записей результата для одного товара-оригинала
     */
    private Stream<MergedProductDto> createMergedRecords(SourceProductDto source, Map<String, List<String>> linkMap) {
        List<String> links = linkMap.get(source.getAnalogModel());

        if (links == null || links.isEmpty()) {
            log.warn("No links found for analog: '{}' (available analogs: {})",
                source.getAnalogModel(), linkMap.keySet());
            return Stream.empty();
        }

        log.info("✅ Found {} links for analog '{}': {}", links.size(), source.getAnalogModel(), links);

        return links.stream().map(link -> {
            MergedProductDto merged = MergedProductDto.builder()
                    .id(source.getId())
                    .originalModel(source.getOriginalModel())
                    .analogModel(source.getAnalogModel())
                    .coefficient(source.getCoefficient())
                    .link(link)
                    .build();

            log.info("🎯 Created merged record: id='{}', originalModel='{}', analogModel='{}', coefficient='{}', link='{}'",
                merged.getId(), merged.getOriginalModel(), merged.getAnalogModel(), merged.getCoefficient(), merged.getLink());

            return merged;
        });
    }

    /**
     * Получение значения ячейки Excel как строки
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Сохраняем дробную часть для коэффициентов с высокой точностью
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                } else {
                    // Используем DecimalFormat для сохранения точности
                    java.text.DecimalFormat df = new java.text.DecimalFormat("#.###");
                    df.setDecimalFormatSymbols(new java.text.DecimalFormatSymbols(java.util.Locale.US));
                    return df.format(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Получение числового значения ячейки Excel
     */
    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                return parseDouble(cell.getStringCellValue().trim());
            default:
                return 0.0;
        }
    }

    /**
     * Безопасный парсинг числа с поддержкой запятых
     */
    private Double parseDouble(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }

            // Нормализуем число: заменяем запятые на точки и убираем пробелы
            String normalized = value.trim().replace(",", ".");

            log.debug("📋 Parsing coefficient: '{}' -> normalized: '{}'", value, normalized);

            Double result = Double.parseDouble(normalized);
            log.debug("📋 Parsed coefficient result: {}", result);

            return result;
        } catch (NumberFormatException e) {
            log.warn("Cannot parse number: '{}', returning 0.0", value);
            return 0.0;
        }
    }

    /**
     * Генерация CSV файла из объединенных данных
     */
    public byte[] generateCsvFile(List<MergedProductDto> data) {
        log.debug("Generating CSV file for {} records using Windows-1251 encoding", data.size());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, Charset.forName("Windows-1251"));
             com.opencsv.CSVWriter csvWriter = new com.opencsv.CSVWriter(
                 writer,
                 ';',      // semicolon delimiter
                 '"',      // quote character
                 '"',      // escape character
                 "\r\n"    // line end
             )) {

            // Заголовки
            csvWriter.writeNext(new String[]{"ID", "Модель оригинал", "Модель аналог", "Коэффициент", "Ссылка"});

            // Данные
            for (MergedProductDto item : data) {
                csvWriter.writeNext(new String[]{
                    item.getId(),
                    item.getOriginalModel(),
                    item.getAnalogModel(),
                    String.format("%.3f", item.getCoefficient()), // 3 знака после запятой
                    item.getLink()
                });
            }

            csvWriter.flush();
            writer.flush();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new DataMergerException("Ошибка генерации CSV файла: " + e.getMessage(), e);
        }
    }

    /**
     * Генерация Excel файла из объединенных данных
     */
    public byte[] generateExcelFile(List<MergedProductDto> data) {
        log.debug("Generating Excel file for {} records", data.size());

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Merged Data");

            // Заголовки
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Модель оригинал");
            headerRow.createCell(2).setCellValue("Модель аналог");
            headerRow.createCell(3).setCellValue("Коэффициент");
            headerRow.createCell(4).setCellValue("Ссылка");

            // Данные
            for (int i = 0; i < data.size(); i++) {
                MergedProductDto item = data.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(item.getId());
                row.createCell(1).setCellValue(item.getOriginalModel());
                row.createCell(2).setCellValue(item.getAnalogModel());
                row.createCell(3).setCellValue(item.getCoefficient());
                row.createCell(4).setCellValue(item.getLink());
            }

            // Автоширина колонок
            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new DataMergerException("Ошибка генерации Excel файла: " + e.getMessage(), e);
        }
    }

    /**
     * Экранирование значений для CSV
     */
    private String escapeCSV(String value) {
        if (value == null) return "";

        // Экранируем запятые, кавычки и переносы строк
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Анализ заголовков файлов для настройки маппинга
     * Использует FileAnalyzerService для правильного анализа файлов
     */
    public DataMergerConfigDto analyzeHeaders(MultipartFile sourceFile, MultipartFile linksFile) {
        log.debug("Analyzing headers: source={}, links={}",
                sourceFile.getOriginalFilename(), linksFile.getOriginalFilename());

        try {
            // Используем FileAnalyzerService для анализа файлов
            FileMetadata sourceMetadata = fileAnalyzerService.analyzeFile(sourceFile, "data-merger-source");
            FileMetadata linksMetadata = fileAnalyzerService.analyzeFile(linksFile, "data-merger-links");

            // Извлекаем заголовки из метаданных
            List<String> sourceHeaders = extractHeadersFromMetadata(sourceMetadata);
            List<String> linksHeaders = extractHeadersFromMetadata(linksMetadata);

            // Создаем базовую конфигурацию с автоматическим маппингом
            Map<String, Integer> sourceMapping = createAutoSourceMapping(sourceHeaders);
            Map<String, Integer> linksMapping = createAutoLinksMapping(linksHeaders);

            // Определяем выходные поля по умолчанию
            List<String> outputFields = List.of("id", "originalModel", "analogModel", "coefficient", "link");

            return DataMergerConfigDto.builder()
                    .sourceFileMapping(sourceMapping)
                    .linksFileMapping(linksMapping)
                    .outputFields(outputFields)
                    .sourceHeaders(sourceHeaders)
                    .linksHeaders(linksHeaders)
                    .build();

        } catch (Exception e) {
            log.error("Error analyzing headers: {}", e.getMessage(), e);
            throw new DataMergerException("Ошибка анализа заголовков файлов: " + e.getMessage(), e);
        }
    }

    /**
     * Извлекает заголовки из метаданных файла
     */
    private List<String> extractHeadersFromMetadata(FileMetadata metadata) {
        try {
            if (metadata.getColumnHeaders() == null || metadata.getColumnHeaders().isEmpty()) {
                return new ArrayList<>();
            }

            // Парсим JSON заголовки
            return objectMapper.readValue(metadata.getColumnHeaders(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse headers from metadata: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Автоматический маппинг для исходного файла
     */
    private Map<String, Integer> createAutoSourceMapping(List<String> headers) {
        Map<String, Integer> mapping = new HashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase().trim();

            if (header.contains("id") || header.equals("№")) {
                mapping.put("id", i);
            } else if (header.contains("оригинал") || header.contains("original")) {
                mapping.put("originalModel", i);
            } else if (header.contains("аналог") || header.contains("analog")) {
                mapping.put("analogModel", i);
            } else if (header.contains("коэффициент") || header.contains("coefficient") || header.contains("коэф")) {
                mapping.put("coefficient", i);
            }
        }

        // Если автоматический маппинг не сработал, используем порядок по умолчанию
        if (mapping.size() < 4 && headers.size() >= 4) {
            mapping.put("id", 0);
            mapping.put("originalModel", 1);
            mapping.put("analogModel", 2);
            mapping.put("coefficient", 3);
        }

        return mapping;
    }

    /**
     * Автоматический маппинг для файла ссылок
     */
    private Map<String, Integer> createAutoLinksMapping(List<String> headers) {
        Map<String, Integer> mapping = new HashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase().trim();

            if (header.contains("аналог") || header.contains("analog") || header.contains("модель")) {
                mapping.put("analogModel", i);
            } else if (header.contains("ссылка") || header.contains("link") || header.contains("url")) {
                mapping.put("link", i);
            }
        }

        // Если автоматический маппинг не сработал, используем порядок по умолчанию
        if (mapping.size() < 2 && headers.size() >= 2) {
            mapping.put("analogModel", 0);
            mapping.put("link", 1);
        }

        return mapping;
    }

    /**
     * Парсинг исходного файла с использованием пользовательского маппинга
     */
    private List<SourceProductDto> parseSourceFileWithMapping(MultipartFile file, DataMergerConfigDto config) throws IOException {
        log.debug("Parsing source file with custom mapping: {}", file.getOriginalFilename());

        List<SourceProductDto> result = new ArrayList<>();
        String fileName = file.getOriginalFilename().toLowerCase();

        if (fileName.endsWith(".csv")) {
            result = parseCsvSourceFileWithMapping(file, config.getSourceFileMapping(), config.getSourceHasHeaders());
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            result = parseExcelSourceFileWithMapping(file, config.getSourceFileMapping());
        } else {
            throw new DataMergerException("Неподдерживаемый формат файла: " + fileName);
        }

        log.debug("Parsed {} source records with custom mapping", result.size());
        return result;
    }

    /**
     * Парсинг файла ссылок с использованием пользовательского маппинга
     */
    private List<ProductLinksDto> parseLinksFileWithMapping(MultipartFile file, DataMergerConfigDto config) throws IOException {
        log.debug("Parsing links file with custom mapping: {}", file.getOriginalFilename());

        List<ProductLinksDto> result = new ArrayList<>();
        String fileName = file.getOriginalFilename().toLowerCase();

        if (fileName.endsWith(".csv")) {
            result = parseCsvLinksFileWithMapping(file, config.getLinksFileMapping(), config.getLinksHasHeaders());
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            result = parseExcelLinksFileWithMapping(file, config.getLinksFileMapping());
        } else {
            throw new DataMergerException("Неподдерживаемый формат файла: " + fileName);
        }

        log.debug("Parsed {} link records with custom mapping", result.size());
        return result;
    }

    /**
     * Парсинг CSV исходного файла с пользовательским маппингом
     */
    private List<SourceProductDto> parseCsvSourceFileWithMapping(MultipartFile file, Map<String, Integer> mapping, Boolean hasHeaders) throws IOException {
        List<SourceProductDto> result = new ArrayList<>();

        // Используем фиксированные настройки для стабильной работы с Windows CSV
        Charset charset = Charset.forName("Windows-1251");
        char delimiter = ';';
        char quoteChar = '"';
        char escapeChar = CSVParser.NULL_CHARACTER;

        log.debug("Using hardcoded CSV settings: charset=Windows-1251, delimiter=;, quote=\"");

        log.debug("Using charset: {}, delimiter: {}, quote: {}", charset, delimiter, quoteChar);

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), charset)) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(delimiter)
                    .withQuoteChar(quoteChar)
                    .withEscapeChar(escapeChar)
                    .build();

            try (CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .withSkipLines(1) // Пропускаем заголовок
                    .build()) {

                String[] line;
                int rowNum = 1;

                while ((line = csvReader.readNext()) != null) {
                    rowNum++;

                    try {
                        String id = getValueByMapping(line, mapping, "id");
                        String originalModel = getValueByMapping(line, mapping, "originalModel");
                        String analogModel = getValueByMapping(line, mapping, "analogModel");
                        String coefficientStr = getValueByMapping(line, mapping, "coefficient");

                        SourceProductDto dto = SourceProductDto.builder()
                                .id(id)
                                .originalModel(originalModel)
                                .analogModel(analogModel)
                                .coefficient(parseDouble(coefficientStr))
                                .build();

                        log.info("📋 Source CSV row {}: id='{}', originalModel='{}', analogModel='{}', coefficient='{}'",
                            rowNum, id, originalModel, analogModel, coefficientStr);

                        result.add(dto);
                    } catch (Exception e) {
                        log.warn("Error parsing row {} with custom mapping: {}", rowNum, e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new IOException("CSV validation error: " + e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Парсинг Excel исходного файла с пользовательским маппингом
     */
    private List<SourceProductDto> parseExcelSourceFileWithMapping(MultipartFile file, Map<String, Integer> mapping) throws IOException {
        List<SourceProductDto> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Пропускаем заголовок
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String id = getCellValueByMapping(row, mapping, "id");
                    String originalModel = getCellValueByMapping(row, mapping, "originalModel");
                    String analogModel = getCellValueByMapping(row, mapping, "analogModel");
                    String coefficientStr = getCellValueByMapping(row, mapping, "coefficient");

                    SourceProductDto dto = SourceProductDto.builder()
                            .id(id)
                            .originalModel(originalModel)
                            .analogModel(analogModel)
                            .coefficient(parseDouble(coefficientStr))
                            .build();

                    log.info("📋 Source Excel row {}: id='{}', originalModel='{}', analogModel='{}', coefficient='{}'",
                        i + 1, id, originalModel, analogModel, coefficientStr);

                    result.add(dto);
                } catch (Exception e) {
                    log.warn("Error parsing Excel row {} with custom mapping: {}", i + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Парсинг CSV файла ссылок с пользовательским маппингом
     */
    private List<ProductLinksDto> parseCsvLinksFileWithMapping(MultipartFile file, Map<String, Integer> mapping, Boolean hasHeaders) throws IOException {
        List<ProductLinksDto> result = new ArrayList<>();

        // Используем фиксированные настройки для стабильной работы с Windows CSV
        Charset charset = Charset.forName("Windows-1251");
        char delimiter = ';';
        char quoteChar = '"';
        char escapeChar = CSVParser.NULL_CHARACTER;

        log.debug("Using hardcoded CSV settings: charset=Windows-1251, delimiter=;, quote=\"");

        log.debug("Using charset: {}, delimiter: {}, quote: {}", charset, delimiter, quoteChar);

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), charset)) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(delimiter)
                    .withQuoteChar(quoteChar)
                    .withEscapeChar(escapeChar)
                    .build();

            try (CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .withSkipLines(1) // Пропускаем заголовок
                    .build()) {

                String[] line;
                int rowNum = 1;

                while ((line = csvReader.readNext()) != null) {
                    rowNum++;

                    try {
                        String analogModel = getValueByMapping(line, mapping, "analogModel");
                        String link = getValueByMapping(line, mapping, "link");

                        ProductLinksDto dto = ProductLinksDto.builder()
                                .analogModel(analogModel)
                                .link(link)
                                .build();

                        log.info("🔗 Links CSV row {}: analogModel='{}', link='{}'",
                            rowNum, analogModel, link);

                        result.add(dto);
                    } catch (Exception e) {
                        log.warn("Error parsing row {} with custom mapping: {}", rowNum, e.getMessage());
                    }
                }
            } catch (Exception e) {
                throw new IOException("CSV validation error: " + e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Парсинг Excel файла ссылок с пользовательским маппингом
     */
    private List<ProductLinksDto> parseExcelLinksFileWithMapping(MultipartFile file, Map<String, Integer> mapping) throws IOException {
        List<ProductLinksDto> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Пропускаем заголовок
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String analogModel = getCellValueByMapping(row, mapping, "analogModel");
                    String link = getCellValueByMapping(row, mapping, "link");

                    ProductLinksDto dto = ProductLinksDto.builder()
                            .analogModel(analogModel)
                            .link(link)
                            .build();

                    log.info("🔗 Links Excel row {}: analogModel='{}', link='{}'",
                        i + 1, analogModel, link);

                    result.add(dto);
                } catch (Exception e) {
                    log.warn("Error parsing Excel row {} with custom mapping: {}", i + 1, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Получение значения из массива по маппингу
     */
    private String getValueByMapping(String[] line, Map<String, Integer> mapping, String field) {
        Integer index = mapping.get(field);
        if (index == null || index >= line.length) {
            log.debug("🔍 Mapping for '{}': index={}, line.length={}", field, index, line.length);
            return "";
        }
        String value = line[index].trim();
        log.debug("🔍 Mapping for '{}': index={} -> value='{}'", field, index, value);
        return value;
    }

    /**
     * Получение значения из Excel строки по маппингу
     */
    private String getCellValueByMapping(Row row, Map<String, Integer> mapping, String field) {
        Integer index = mapping.get(field);
        if (index == null) {
            log.debug("🔍 Excel mapping for '{}': index=null", field);
            return "";
        }
        Cell cell = row.getCell(index);
        String value = getCellValueAsString(cell);
        log.debug("🔍 Excel mapping for '{}': index={} -> value='{}'", field, index, value);
        return value;
    }

}