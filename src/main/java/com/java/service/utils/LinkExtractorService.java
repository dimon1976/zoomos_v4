package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.LinkExtractorDto;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Сервис для извлечения ссылок из файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkExtractorService {

    private final FileGeneratorService fileGeneratorService;
    private final ObjectMapper objectMapper;
    
    // Регулярное выражение для поиска URL
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Обработка извлечения ссылок
     */
    public byte[] processLinkExtraction(FileMetadata metadata, LinkExtractorDto dto) throws IOException {
        log.info("=== НАЧАЛО ОБРАБОТКИ ИЗВЛЕЧЕНИЯ ССЫЛОК ===");
        log.info("Файл: {}", metadata.getOriginalFilename());
        log.info("ID колонка: {}", dto.getIdColumn());
        log.info("Формат вывода: {}", dto.getOutputFormat());
        
        // Читаем полные данные файла, а не только sampleData
        List<List<String>> data = readFullFileData(metadata);
        log.info("Прочитано строк из файла: {}", data.size());
        
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для обработки");
        }

        // Логируем первые несколько строк для проверки
        if (data.size() > 0) {
            log.info("Первая строка (заголовки): {}", data.get(0));
            if (data.size() > 1) {
                log.info("Вторая строка (данные): {}", data.get(1));
            }
            if (data.size() > 2) {
                log.info("Третья строка (данные): {}", data.get(2));
            }
        }

        validateColumns(data, dto);
        
        List<LinkExtractResult> results = extractLinks(data, dto);
        
        // Создаем ExportTemplate из DTO
        ExportTemplate template = createExportTemplate(dto);
        
        // Преобразуем результаты в Stream<Map<String, Object>>
        Stream<Map<String, Object>> dataStream = results.stream()
                .map(this::convertResultToMap);
        
        // Генерируем файл через FileGeneratorService
        String fileName = "link-extractor-result_" + System.currentTimeMillis();
        try {
            Path filePath = fileGeneratorService.generateFile(dataStream, results.size(), template, fileName);
            return Files.readAllBytes(filePath);
        } catch (Exception e) {
            throw new IOException("Ошибка при генерации файла: " + e.getMessage(), e);
        }
    }

    /**
     * Создание ExportTemplate из DTO
     */
    private ExportTemplate createExportTemplate(LinkExtractorDto dto) {
        ExportTemplate template = ExportTemplate.builder()
                .name("Link Extractor Export")
                .description("Экспорт извлеченных ссылок")
                .fileFormat("excel".equalsIgnoreCase(dto.getOutputFormat()) ? "XLSX" : "CSV")
                .csvDelimiter(dto.getCsvDelimiter())
                .csvEncoding(dto.getCsvEncoding())
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();
        
        // Добавляем поля
        List<ExportTemplateField> fields = new ArrayList<>();
        fields.add(createTemplateField(template, "ID", "ID", 1));
        fields.add(createTemplateField(template, "URL", "Найденная ссылка", 2));
        fields.add(createTemplateField(template, "Источник", "Колонка источника", 3));
        
        template.setFields(fields);
        return template;
    }
    
    /**
     * Создание поля для ExportTemplate
     */
    private ExportTemplateField createTemplateField(ExportTemplate template, String entityFieldName, String exportColumnName, int order) {
        return ExportTemplateField.builder()
                .template(template)
                .entityFieldName(entityFieldName)
                .exportColumnName(exportColumnName)
                .fieldOrder(order)
                .isIncluded(true)
                .build();
    }

    /**
     * Конвертация результата в Map для FileGenerator
     */
    private Map<String, Object> convertResultToMap(LinkExtractResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ID", result.getId());
        map.put("Найденная ссылка", result.getUrl());
        map.put("Колонка источника", result.getSourceColumn());
        return map;
    }

    /**
     * Чтение полных данных файла
     */
    private List<List<String>> readFullFileData(FileMetadata metadata) throws IOException {
        if (metadata.getTempFilePath() == null) {
            throw new IllegalArgumentException("Файл не найден на диске");
        }

        List<List<String>> data = new ArrayList<>();
        String filename = metadata.getOriginalFilename().toLowerCase();
        
        if (filename.endsWith(".csv")) {
            data = readCsvFile(metadata);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            data = readExcelFile(metadata);
        } else {
            throw new IllegalArgumentException("Неподдерживаемый формат файла");
        }

        log.info("Прочитано {} строк из файла {}", data.size(), metadata.getOriginalFilename());
        return data;
    }

    /**
     * Чтение CSV файла
     */
    private List<List<String>> readCsvFile(FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        
        try (Reader reader = Files.newBufferedReader(java.nio.file.Paths.get(metadata.getTempFilePath()), 
                java.nio.charset.Charset.forName(metadata.getDetectedEncoding() != null ? metadata.getDetectedEncoding() : "UTF-8"));
             com.opencsv.CSVReader csvReader = new com.opencsv.CSVReaderBuilder(reader)
                .withCSVParser(new com.opencsv.CSVParserBuilder()
                    .withSeparator(metadata.getDetectedDelimiter() != null ? metadata.getDetectedDelimiter().charAt(0) : ';')
                    .withQuoteChar(metadata.getDetectedQuoteChar() != null ? metadata.getDetectedQuoteChar().charAt(0) : '"')
                    .build())
                .build()) {

            String[] line;
            boolean isFirstLine = true;
            try {
                while ((line = csvReader.readNext()) != null) {
                    if (isFirstLine && line.length == 1 && (line[0] == null || line[0].trim().isEmpty())) {
                        isFirstLine = false;
                        continue; // Пропускаем пустую первую строку
                    }
                    isFirstLine = false;
                    data.add(Arrays.asList(line));
                }
            } catch (com.opencsv.exceptions.CsvValidationException e) {
                throw new IOException("Ошибка чтения CSV файла: " + e.getMessage(), e);
            }
        }
        
        return data;
    }

    /**
     * Чтение Excel файла
     */
    private List<List<String>> readExcelFile(FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(metadata.getTempFilePath());
             org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(fis)) {
            
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0); // Читаем первый лист
            
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    String cellValue = "";
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                cellValue = cell.getStringCellValue();
                                break;
                            case NUMERIC:
                                cellValue = String.valueOf(cell.getNumericCellValue());
                                break;
                            case BOOLEAN:
                                cellValue = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                cellValue = cell.getCellFormula();
                                break;
                            default:
                                cellValue = "";
                        }
                    }
                    rowData.add(cellValue);
                }
                data.add(rowData);
            }
        }
        
        return data;
    }

    /**
     * Парсинг данных из JSON (для preview)
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
    private void validateColumns(List<List<String>> data, LinkExtractorDto dto) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для обработки");
        }

        int maxColumns = data.get(0).size();
        
        if (dto.getIdColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка ID превышает количество колонок в файле");
        }
    }

    /**
     * Извлечение ссылок из данных
     */
    private List<LinkExtractResult> extractLinks(List<List<String>> data, LinkExtractorDto dto) {
        List<LinkExtractResult> results = new ArrayList<>();
        log.info("=== НАЧАЛО ИЗВЛЕЧЕНИЯ ССЫЛОК ===");
        log.info("Всего строк для обработки: {}", data.size());
        
        int rowIndex = 0;
        for (List<String> row : data) {
            log.debug("Обработка строки {}: {}", rowIndex, row);
            
            if (row.size() <= dto.getIdColumn()) {
                log.debug("Строка {} пропущена: размер {} меньше ID колонки {}", rowIndex, row.size(), dto.getIdColumn());
                rowIndex++;
                continue;
            }

            String id = getColumnValue(row, dto.getIdColumn());
            log.debug("ID из строки {}: '{}'", rowIndex, id);
            
            if (isEmpty(id)) {
                log.debug("Пропущена строка {} с пустым ID", rowIndex);
                rowIndex++;
                continue;
            }

            // Поиск URL во всех колонках строки (кроме колонки ID)
            int urlsFoundInRow = 0;
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                if (columnIndex == dto.getIdColumn()) {
                    log.debug("Пропускаем ID колонку {}", columnIndex);
                    continue; // Пропускаем колонку ID
                }
                
                String cellValue = getColumnValue(row, columnIndex);
                log.debug("Значение колонки {} в строке {}: '{}'", columnIndex, rowIndex, cellValue);
                
                if (!isEmpty(cellValue)) {
                    List<String> urls = extractUrlsFromText(cellValue);
                    log.debug("Найдено {} URL в колонке {}: {}", urls.size(), columnIndex, urls);
                    
                    for (String url : urls) {
                        LinkExtractResult result = new LinkExtractResult();
                        result.setId(id);
                        result.setUrl(url);
                        result.setSourceColumn("Колонка " + (columnIndex + 1));
                        results.add(result);
                        urlsFoundInRow++;
                        log.debug("Добавлен результат: ID={}, URL={}, Колонка={}", id, url, columnIndex + 1);
                    }
                }
            }
            log.debug("В строке {} найдено {} URL", rowIndex, urlsFoundInRow);
            rowIndex++;
        }
        
        log.info("Обработано строк: {}, найдено ссылок: {}", data.size(), results.size());
        log.info("=== ОКОНЧАНИЕ ИЗВЛЕЧЕНИЯ ССЫЛОК ===");
        
        return results;
    }

    /**
     * Извлечение URL из текста с помощью регулярных выражений
     */
    private List<String> extractUrlsFromText(String text) {
        List<String> urls = new ArrayList<>();
        
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group().trim();
            // Удаляем возможные символы в конце URL
            url = cleanUrl(url);
            if (isValidUrl(url)) {
                urls.add(url);
            }
        }
        
        return urls;
    }

    /**
     * Очистка URL от лишних символов в конце
     */
    private String cleanUrl(String url) {
        // Убираем точки, запятые, скобки в конце
        while (!url.isEmpty() && ".,;!?)]}\"".indexOf(url.charAt(url.length() - 1)) != -1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Базовая валидация URL
     */
    private boolean isValidUrl(String url) {
        try {
            return url.length() > 10 && (url.startsWith("http://") || url.startsWith("https://"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получение значения колонки с защитой от IndexOutOfBounds
     */
    private String getColumnValue(List<String> row, Integer columnIndex) {
        if (columnIndex != null && columnIndex >= 0 && columnIndex < row.size()) {
            return row.get(columnIndex);
        }
        return null;
    }

    /**
     * Проверка на пустое значение
     */
    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Класс для хранения результата извлечения ссылок
     */
    public static class LinkExtractResult {
        private String id;
        private String url;
        private String sourceColumn;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
    }
}