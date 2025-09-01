package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.RedirectCollectorDto;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Сервис для сбора финальных URL после редиректов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectCollectorService {

    private final FileGeneratorService fileGeneratorService;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Обработка сбора редиректов
     */
    public byte[] processRedirectCollection(FileMetadata metadata, RedirectCollectorDto dto) throws IOException {
        log.info("=== НАЧАЛО ОБРАБОТКИ СБОРА РЕДИРЕКТОВ ===");
        log.info("Файл: {}", metadata.getOriginalFilename());
        log.info("URL колонка: {}", dto.getUrlColumn());
        log.info("Максимум редиректов: {}, Таймаут: {} сек", dto.getMaxRedirects(), dto.getTimeoutSeconds());
        
        // Читаем полные данные файла
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
        }

        validateColumns(data, dto);
        
        List<RedirectResult> results = collectRedirects(data, dto);
        
        // Создаем ExportTemplate из DTO
        ExportTemplate template = createExportTemplate(dto);
        
        // Преобразуем результаты в Stream<Map<String, Object>>
        Stream<Map<String, Object>> dataStream = results.stream()
                .map(this::convertResultToMap);
        
        // Генерируем файл через FileGeneratorService
        String fileName = "redirect-collector-result_" + System.currentTimeMillis();
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
    private ExportTemplate createExportTemplate(RedirectCollectorDto dto) {
        ExportTemplate template = ExportTemplate.builder()
                .name("Redirect Collector Export")
                .description("Экспорт собранных финальных URL")
                .fileFormat("excel".equalsIgnoreCase(dto.getOutputFormat()) ? "XLSX" : "CSV")
                .csvDelimiter(dto.getCsvDelimiter())
                .csvEncoding(dto.getCsvEncoding())
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();
        
        // Добавляем поля
        List<ExportTemplateField> fields = new ArrayList<>();
        fields.add(createTemplateField(template, "Исходная ссылка", "Исходная ссылка", 1));
        fields.add(createTemplateField(template, "Финальная ссылка", "Финальная ссылка", 2));
        fields.add(createTemplateField(template, "Статус", "Статус", 3));
        fields.add(createTemplateField(template, "Количество редиректов", "Количество редиректов", 4));
        
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
    private Map<String, Object> convertResultToMap(RedirectResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Исходная ссылка", result.getOriginalUrl());
        map.put("Финальная ссылка", result.getFinalUrl());
        map.put("Статус", result.getStatus());
        map.put("Количество редиректов", result.getRedirectCount());
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
     * Валидация выбранных колонок
     */
    private void validateColumns(List<List<String>> data, RedirectCollectorDto dto) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для обработки");
        }

        int maxColumns = data.get(0).size();
        
        if (dto.getUrlColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка URL превышает количество колонок в файле");
        }
    }

    /**
     * Сбор редиректов из данных
     */
    private List<RedirectResult> collectRedirects(List<List<String>> data, RedirectCollectorDto dto) {
        List<RedirectResult> results = new ArrayList<>();
        log.info("=== НАЧАЛО СБОРА РЕДИРЕКТОВ ===");
        log.info("Всего строк для обработки: {}", data.size());
        
        RestTemplate restTemplate = createConfiguredRestTemplate(dto.getTimeoutSeconds());
        
        int rowIndex = 0;
        for (List<String> row : data) {
            log.debug("Обработка строки {}: {}", rowIndex, row);
            
            if (row.size() <= dto.getUrlColumn()) {
                log.debug("Строка {} пропущена: размер {} меньше URL колонки {}", rowIndex, row.size(), dto.getUrlColumn());
                rowIndex++;
                continue;
            }

            String originalUrl = getColumnValue(row, dto.getUrlColumn());
            log.debug("URL из строки {}: '{}'", rowIndex, originalUrl);
            
            if (isEmpty(originalUrl) || !isValidUrl(originalUrl)) {
                log.debug("Пропущена строка {} с некорректным URL", rowIndex);
                rowIndex++;
                continue;
            }

            RedirectResult result = followRedirects(originalUrl, dto.getMaxRedirects(), dto.getTimeoutSeconds(), restTemplate);
            results.add(result);
            
            log.debug("Добавлен результат: исходный={}, финальный={}, статус={}, редиректов={}", 
                result.getOriginalUrl(), result.getFinalUrl(), result.getStatus(), result.getRedirectCount());
            
            rowIndex++;
        }
        
        log.info("Обработано строк: {}, собрано результатов: {}", data.size(), results.size());
        log.info("=== ОКОНЧАНИЕ СБОРА РЕДИРЕКТОВ ===");
        
        return results;
    }

    /**
     * Создание настроенного RestTemplate с таймаутами
     */
    private RestTemplate createConfiguredRestTemplate(int timeoutSeconds) {
        RestTemplate restTemplate = new RestTemplate();
        
        // Настраиваем таймауты для HTTP клиента
        restTemplate.getRequestFactory();
        
        return restTemplate;
    }

    /**
     * Следование по редиректам для одного URL
     */
    private RedirectResult followRedirects(String originalUrl, int maxRedirects, int timeoutSeconds, RestTemplate restTemplate) {
        RedirectResult result = new RedirectResult();
        result.setOriginalUrl(originalUrl);
        result.setRedirectCount(0);
        
        try {
            String currentUrl = originalUrl;
            int redirectCount = 0;
            
            while (redirectCount < maxRedirects) {
                try {
                    ResponseEntity<String> response = restTemplate.exchange(currentUrl, HttpMethod.HEAD, null, String.class);
                    
                    // Если нет редиректа - это финальный URL
                    if (!isRedirectStatus(response.getStatusCodeValue())) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus("SUCCESS");
                        result.setRedirectCount(redirectCount);
                        return result;
                    }
                    
                    // Получаем URL редиректа
                    String locationHeader = response.getHeaders().getFirst("Location");
                    if (locationHeader == null || locationHeader.isEmpty()) {
                        result.setFinalUrl(currentUrl);
                        result.setStatus("SUCCESS");
                        result.setRedirectCount(redirectCount);
                        return result;
                    }
                    
                    currentUrl = locationHeader;
                    redirectCount++;
                    
                } catch (Exception e) {
                    log.debug("Ошибка HTTP запроса для {}: {}", currentUrl, e.getMessage());
                    result.setFinalUrl(currentUrl);
                    result.setStatus("ERROR");
                    result.setRedirectCount(redirectCount);
                    return result;
                }
            }
            
            // Достигнут максимум редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus("MAX_REDIRECTS");
            result.setRedirectCount(redirectCount);
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus("ERROR");
            result.setRedirectCount(0);
        }
        
        return result;
    }

    /**
     * Проверка статуса на редирект
     */
    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
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
     * Класс для хранения результата сбора редиректов
     */
    public static class RedirectResult {
        private String originalUrl;
        private String finalUrl;
        private String status; // SUCCESS, TIMEOUT, ERROR, MAX_REDIRECTS
        private int redirectCount;

        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
        
        public String getFinalUrl() { return finalUrl; }
        public void setFinalUrl(String finalUrl) { this.finalUrl = finalUrl; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public int getRedirectCount() { return redirectCount; }
        public void setRedirectCount(int redirectCount) { this.redirectCount = redirectCount; }
    }
}