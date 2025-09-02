package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.RedirectCollectorDto;
import com.java.config.AntiBlockConfig;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Сервис для сбора финальных URL после редиректов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectCollectorService {

    private final FileGeneratorService fileGeneratorService;
    private final BrowserService browserService;
    private final AntiBlockConfig antiBlockConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Интерфейс для callback прогресса
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }
    
    /**
     * Обработка сбора редиректов с прогресс-уведомлениями
     */
    public String processRedirectCollectionWithProgress(RedirectCollectorDto dto, ProgressCallback progressCallback) throws Exception {
        log.info("=== НАЧАЛО АСИНХРОННОЙ ОБРАБОТКИ СБОРА РЕДИРЕКТОВ ===");
        log.info("URL колонка: {}", dto.getUrlColumn());
        log.info("Максимум редиректов: {}, Таймаут: {} сек", dto.getMaxRedirects(), dto.getTimeoutSeconds());
        
        progressCallback.onProgress(0, 100, "Читаем данные файла...");
        
        // Читаем данные из временного файла
        List<List<String>> data = readTempFileData(dto.getTempFilePath());
        log.info("Прочитано строк из файла: {}", data.size());
        
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для обработки");
        }

        progressCallback.onProgress(10, 100, "Валидация данных...");
        validateColumnsFromDto(data, dto);
        
        progressCallback.onProgress(20, 100, "Начинаем сбор редиректов...");
        List<RedirectResult> results = collectRedirectsWithProgress(data, dto, progressCallback);
        
        progressCallback.onProgress(90, 100, "Генерируем результирующий файл...");
        
        // Создаем ExportTemplate из DTO
        ExportTemplate template = createExportTemplate(dto);
        
        // Преобразуем результаты в Stream<Map<String, Object>>
        Stream<Map<String, Object>> dataStream = results.stream()
                .map(this::convertResultToMap);
        
        // Генерируем файл через FileGeneratorService
        String fileName = "redirect-collector-result_" + System.currentTimeMillis();
        Path filePath = fileGeneratorService.generateFile(dataStream, results.size(), template, fileName);
        
        log.info("=== ОКОНЧАНИЕ АСИНХРОННОЙ ОБРАБОТКИ СБОРА РЕДИРЕКТОВ ===");
        return filePath.toString();
    }

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
            log.info("Обработка строки {}: {}", rowIndex, row);
            
            if (row.size() <= dto.getUrlColumn()) {
                log.debug("Строка {} пропущена: размер {} меньше URL колонки {}", rowIndex, row.size(), dto.getUrlColumn());
                rowIndex++;
                continue;
            }

            String originalUrl = getColumnValue(row, dto.getUrlColumn());
            log.info("URL из строки {}: '{}'", rowIndex, originalUrl);
            
            if (isEmpty(originalUrl) || !isValidUrl(originalUrl)) {
                log.debug("Пропущена строка {} с некорректным URL", rowIndex);
                rowIndex++;
                continue;
            }

            // Пытаемся сначала стандартный HTTP подход
            RedirectResult result = followRedirects(originalUrl, dto.getMaxRedirects(), dto.getTimeoutSeconds(), restTemplate);
            
            // Если редиректов не найдено, пробуем браузерный подход
            if (result.getRedirectCount() == 0 && browserService.isBrowserAvailable()) {
                log.info("HTTP редиректы не найдены, используем браузер для URL: {}", originalUrl);
                result = followRedirectsWithBrowser(originalUrl, dto.getTimeoutSeconds());
            }
            
            results.add(result);
            
            log.info("Добавлен результат: исходный={}, финальный={}, статус={}, редиректов={}", 
                result.getOriginalUrl(), result.getFinalUrl(), result.getStatus(), result.getRedirectCount());
            
            rowIndex++;
        }
        
        log.info("Обработано строк: {}, собрано результатов: {}", data.size(), results.size());
        log.info("=== ОКОНЧАНИЕ СБОРА РЕДИРЕКТОВ ===");
        
        return results;
    }

    /**
     * Сбор редиректов из данных с прогресс-уведомлениями
     */
    private List<RedirectResult> collectRedirectsWithProgress(List<List<String>> data, RedirectCollectorDto dto, ProgressCallback progressCallback) {
        List<RedirectResult> results = new ArrayList<>();
        log.info("=== НАЧАЛО СБОРА РЕДИРЕКТОВ С ПРОГРЕССОМ ===");
        log.info("Всего строк для обработки: {}", data.size());
        
        RestTemplate restTemplate = createConfiguredRestTemplate(dto.getTimeoutSeconds());
        
        int rowIndex = 0;
        int totalRows = data.size();
        
        for (List<String> row : data) {
            // Обновляем прогресс (20% до 80% для обработки URL)
            int progress = 20 + (int) ((rowIndex * 60.0) / totalRows);
            progressCallback.onProgress(progress, 100, 
                String.format("Обрабатываем URL %d из %d...", rowIndex + 1, totalRows));
            
            log.info("Обработка строки {}: {}", rowIndex, row);
            
            if (row.size() <= dto.getUrlColumn()) {
                log.debug("Строка {} пропущена: размер {} меньше URL колонки {}", rowIndex, row.size(), dto.getUrlColumn());
                rowIndex++;
                continue;
            }

            String originalUrl = getColumnValue(row, dto.getUrlColumn());
            log.info("URL из строки {}: '{}'", rowIndex, originalUrl);
            
            if (isEmpty(originalUrl) || !isValidUrl(originalUrl)) {
                log.debug("Пропущена строка {} с некорректным URL", rowIndex);
                rowIndex++;
                continue;
            }

            // Пытаемся сначала стандартный HTTP подход
            RedirectResult result = followRedirects(originalUrl, dto.getMaxRedirects(), dto.getTimeoutSeconds(), restTemplate);
            
            // Если редиректов не найдено, пробуем браузерный подход
            if (result.getRedirectCount() == 0 && browserService.isBrowserAvailable()) {
                log.info("HTTP редиректы не найдены, используем браузер для URL: {}", originalUrl);
                progressCallback.onProgress(progress, 100, 
                    String.format("Запускаем браузер для URL %d из %d...", rowIndex + 1, totalRows));
                result = followRedirectsWithBrowser(originalUrl, dto.getTimeoutSeconds());
            }
            
            results.add(result);
            
            log.info("Добавлен результат: исходный={}, финальный={}, статус={}, редиректов={}", 
                result.getOriginalUrl(), result.getFinalUrl(), result.getStatus(), result.getRedirectCount());
            
            rowIndex++;
        }
        
        log.info("Обработано строк: {}, собрано результатов: {}", data.size(), results.size());
        log.info("=== ОКОНЧАНИЕ СБОРА РЕДИРЕКТОВ С ПРОГРЕССОМ ===");
        
        return results;
    }

    /**
     * Чтение данных из временного файла
     */
    private List<List<String>> readTempFileData(String tempFilePath) throws IOException {
        if (tempFilePath == null) {
            throw new IllegalArgumentException("Путь к временному файлу не указан");
        }
        
        Path path = Path.of(tempFilePath);
        if (!Files.exists(path)) {
            throw new IOException("Временный файл не найден: " + tempFilePath);
        }
        
        // Используем существующий метод чтения, но адаптируем его для временного файла
        try (InputStream inputStream = Files.newInputStream(path)) {
            if (tempFilePath.endsWith(".xlsx") || tempFilePath.endsWith(".xls")) {
                return readExcelData(inputStream);
            } else {
                return readCsvData(inputStream);
            }
        }
    }

    /**
     * Валидация колонок из DTO
     */
    private void validateColumnsFromDto(List<List<String>> data, RedirectCollectorDto dto) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных");
        }
        
        List<String> firstRow = data.get(0);
        if (firstRow.size() <= dto.getUrlColumn()) {
            throw new IllegalArgumentException(
                String.format("Указанная URL колонка (%d) больше количества колонок в файле (%d)", 
                    dto.getUrlColumn(), firstRow.size()));
        }
    }

    /**
     * Создание настроенного RestTemplate с таймаутами
     */
    private RestTemplate createConfiguredRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // Настраиваем таймауты в миллисекундах
        int timeoutMs = timeoutSeconds * 1000;
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        log.debug("RestTemplate настроен с таймаутом {} секунд", timeoutSeconds);
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
            log.info("Начинаем обработку URL: {}", originalUrl);
            
            String currentUrl = originalUrl;
            int redirectCount = 0;
            
            // Ручное следование редиректам для точного контроля (максимум 3 попытки)
            while (redirectCount <= Math.min(maxRedirects, 3)) {
                java.net.URL url = new java.net.URL(currentUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                
                // Настраиваем соединение для GET запроса
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeoutSeconds * 1000);
                connection.setReadTimeout(timeoutSeconds * 1000);
                connection.setInstanceFollowRedirects(false); // Отключаем автоматические редиректы
                
                // Ротация User-Agent для обхода блокировок
                String selectedUserAgent = getRandomUserAgent();
                connection.setRequestProperty("User-Agent", selectedUserAgent);
                
                // Добавляем Referer для имитации перехода с поисковика
                connection.setRequestProperty("Referer", getRandomReferer());
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
                connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                connection.setRequestProperty("DNT", "1");
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
                connection.setRequestProperty("Sec-Fetch-Dest", "document");
                connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
                connection.setRequestProperty("Sec-Fetch-Site", "none");
                connection.setRequestProperty("Sec-Fetch-User", "?1");
                connection.setRequestProperty("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
                connection.setRequestProperty("sec-ch-ua-mobile", "?0");
                connection.setRequestProperty("sec-ch-ua-platform", "\"Windows\"");
                
                long startTime = System.currentTimeMillis();
                int responseCode = connection.getResponseCode();
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                // Логирование согласно стандарту
                if (antiBlockConfig.isLogStrategies()) {
                    log.info("URL: {} | Strategy: SimpleHttp | Status: {} | Time: {}ms | UserAgent: {}", 
                             currentUrl, responseCode, elapsedTime, selectedUserAgent.substring(0, Math.min(50, selectedUserAgent.length())) + "...");
                }
                
                // Проверяем HTTP-редиректы
                if (isRedirectStatus(responseCode)) {
                    // Получаем Location header для редиректа
                    String locationHeader = connection.getHeaderField("Location");
                    log.info("HTTP редирект - Location header: {}", locationHeader);
                    
                    if (locationHeader != null && !locationHeader.isEmpty()) {
                        // Обрабатываем относительные URL
                        currentUrl = resolveUrl(currentUrl, locationHeader);
                        redirectCount++;
                        connection.disconnect();
                        continue;
                    }
                }
                
                // Если это не HTTP-редирект или нет Location header, проверяем содержимое страницы
                if (responseCode == 200) {
                    String htmlContent = readResponseContent(connection);
                    connection.disconnect();
                    
                    // Ищем JavaScript или meta-refresh редиректы
                    String redirectUrl = findJavaScriptRedirect(htmlContent, currentUrl);
                    
                    if (redirectUrl != null && !redirectUrl.equals(currentUrl)) {
                        log.info("JavaScript редирект найден: {}", redirectUrl);
                        currentUrl = redirectUrl;
                        redirectCount++;
                        continue;
                    } else {
                        // Нет редиректов - это финальный URL
                        result.setFinalUrl(currentUrl);
                        result.setStatus("SUCCESS");
                        result.setRedirectCount(redirectCount);
                        log.info("Финальный URL: {} после {} редиректов", currentUrl, redirectCount);
                        return result;
                    }
                } else {
                    // Нет Location header и не 200 статус - считаем текущий URL финальным
                    result.setFinalUrl(currentUrl);
                    result.setStatus("SUCCESS");  
                    result.setRedirectCount(redirectCount);
                    log.info("Статус {}, считаем финальным: {}", responseCode, currentUrl);
                    connection.disconnect();
                    return result;
                }
            }
            
            // Достигнут лимит редиректов
            result.setFinalUrl(currentUrl);
            result.setStatus("MAX_REDIRECTS");
            result.setRedirectCount(redirectCount);
            log.info("Достигнут лимит редиректов ({}): {}", maxRedirects, currentUrl);
            
        } catch (java.net.SocketTimeoutException e) {
            log.info("Таймаут для URL: {}", originalUrl);
            result.setFinalUrl(originalUrl);
            result.setStatus("TIMEOUT");
            result.setRedirectCount(0);
        } catch (java.net.UnknownHostException e) {
            log.info("Неизвестный хост для URL: {}", originalUrl);
            result.setFinalUrl(originalUrl);
            result.setStatus("UNKNOWN_HOST");
            result.setRedirectCount(0);
        } catch (java.io.IOException e) {
            log.info("IO ошибка для URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus("IO_ERROR");
            result.setRedirectCount(0);
        } catch (Exception e) {
            log.error("Ошибка при обработке URL {}: {}", originalUrl, e.getMessage());
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
     * Чтение содержимого HTTP-ответа с обработкой сжатия
     */
    private String readResponseContent(java.net.HttpURLConnection connection) throws IOException {
        java.io.InputStream inputStream;
        
        // Обрабатываем сжатие gzip/deflate
        String encoding = connection.getContentEncoding();
        if ("gzip".equalsIgnoreCase(encoding)) {
            inputStream = new java.util.zip.GZIPInputStream(connection.getInputStream());
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            inputStream = new java.util.zip.InflaterInputStream(connection.getInputStream());
        } else {
            inputStream = connection.getInputStream();
        }
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 500) { // Ограничиваем чтение для производительности
                content.append(line).append("\n");
                linesRead++;
            }
            return content.toString();
        }
    }

    /**
     * Поиск JavaScript или meta-refresh редиректов в HTML
     */
    private String findJavaScriptRedirect(String htmlContent, String currentUrl) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return null;
        }
        
        // Ищем meta refresh
        String metaRedirect = findMetaRefreshRedirect(htmlContent, currentUrl);
        if (metaRedirect != null) {
            return metaRedirect;
        }
        
        // Ищем JavaScript редиректы
        return findJavaScriptLocationRedirect(htmlContent, currentUrl);
    }

    /**
     * Поиск meta refresh редиректов
     */
    private String findMetaRefreshRedirect(String htmlContent, String currentUrl) {
        java.util.regex.Pattern metaPattern = java.util.regex.Pattern.compile(
            "(?i)<meta[^>]*http-equiv=[\"']refresh[\"'][^>]*content=[\"']\\d+;\\s*url=([^\"']+)[\"'][^>]*>",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = metaPattern.matcher(htmlContent);
        if (matcher.find()) {
            String redirectUrl = matcher.group(1);
            log.info("Meta refresh редирект найден: {}", redirectUrl);
            return resolveUrl(currentUrl, redirectUrl);
        }
        
        return null;
    }

    /**
     * Поиск JavaScript location редиректов
     */
    private String findJavaScriptLocationRedirect(String htmlContent, String currentUrl) {
        // Ищем различные варианты JavaScript редиректов
        String[] patterns = {
            "(?i)window\\.location\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)window\\.location\\.href\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)document\\.location\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)location\\.href\\s*=\\s*[\"']([^\"']+)[\"']",
            "(?i)location\\s*=\\s*[\"']([^\"']+)[\"']"
        };
        
        for (String patternStr : patterns) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
            java.util.regex.Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                String redirectUrl = matcher.group(1);
                log.info("JavaScript редирект найден: {}", redirectUrl);
                return resolveUrl(currentUrl, redirectUrl);
            }
        }
        
        return null;
    }

    /**
     * Разрешение относительных URL
     */
    private String resolveUrl(String baseUrl, String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url; // Абсолютный URL
            }
            
            java.net.URL base = new java.net.URL(baseUrl);
            
            if (url.startsWith("/")) {
                // Абсолютный путь относительно корня
                return base.getProtocol() + "://" + base.getHost() + 
                       (base.getPort() != -1 ? ":" + base.getPort() : "") + url;
            } else {
                // Относительный путь
                java.net.URL resolved = new java.net.URL(base, url);
                return resolved.toString();
            }
        } catch (Exception e) {
            log.warn("Ошибка при разрешении URL: base={}, url={}", baseUrl, url, e);
            return url; // Возвращаем как есть
        }
    }

    /**
     * Следование по редиректам с использованием браузера
     */
    private RedirectResult followRedirectsWithBrowser(String originalUrl, int timeoutSeconds) {
        RedirectResult result = new RedirectResult();
        result.setOriginalUrl(originalUrl);
        
        try {
            log.info("Используем браузер для обработки URL: {}", originalUrl);
            
            BrowserService.BrowserResult browserResult = browserService.getUrlWithBrowser(originalUrl, timeoutSeconds);
            
            result.setFinalUrl(browserResult.getFinalUrl());
            result.setStatus(browserResult.getStatus());
            result.setRedirectCount(browserResult.getRedirectCount());
            
            if ("ERROR".equals(browserResult.getStatus())) {
                log.warn("Ошибка браузера для URL {}: {}", originalUrl, browserResult.getErrorMessage());
            } else {
                log.info("Браузер обработал URL: {} -> {}", originalUrl, browserResult.getFinalUrl());
            }
            
        } catch (Exception e) {
            log.error("Критическая ошибка при использовании браузера для URL {}: {}", originalUrl, e.getMessage());
            result.setFinalUrl(originalUrl);
            result.setStatus("BROWSER_ERROR");
            result.setRedirectCount(0);
        }
        
        return result;
    }

    /**
     * Чтение Excel данных из InputStream
     */
    private List<List<String>> readExcelData(InputStream inputStream) throws IOException {
        List<List<String>> data = new ArrayList<>();
        
        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream)) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    rowData.add(getCellValueAsString(cell));
                }
                data.add(rowData);
            }
        }
        
        return data;
    }

    /**
     * Чтение CSV данных из InputStream
     */
    private List<List<String>> readCsvData(InputStream inputStream) throws IOException {
        List<List<String>> data = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Простая обработка CSV - разделение по запятым с учетом кавычек
                List<String> rowData = parseCsvLine(line);
                data.add(rowData);
            }
        }
        
        return data;
    }

    /**
     * Простой парсер CSV строки
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        result.add(currentField.toString().trim());
        return result;
    }

    /**
     * Получение значения ячейки как строки (используется существующим кодом)
     */
    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                } else {
                    // Форматируем числа без научной нотации
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
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
    
    /**
     * Получение случайного User-Agent для ротации
     */
    private String getRandomUserAgent() {
        List<String> userAgents = antiBlockConfig.getUserAgents();
        if (userAgents.isEmpty()) {
            // Fallback на стандартный User-Agent
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(userAgents.size());
        return userAgents.get(randomIndex);
    }
    
    /**
     * Получение случайного Referer для имитации перехода с поисковиков
     */
    private String getRandomReferer() {
        String[] referers = {
            "https://www.google.com/",
            "https://www.google.ru/",
            "https://yandex.ru/",
            "https://www.bing.com/",
            "https://duckduckgo.com/"
        };
        int randomIndex = ThreadLocalRandom.current().nextInt(referers.length);
        return referers[randomIndex];
    }
}