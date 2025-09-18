package com.java.service.utils;

import com.java.dto.utils.RedirectFinderDto;
import com.java.model.entity.FileMetadata;
import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.java.model.utils.RedirectExportTemplate;
import com.java.model.utils.RedirectUrlData;
import com.java.model.utils.RedirectProcessingRequest;
import com.java.service.exports.FileGeneratorService;
import com.java.service.notification.NotificationService;
import com.java.service.utils.redirect.RedirectStrategy;
import com.java.service.utils.redirect.RedirectProgressDto;
import com.java.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

/**
 * Основной сервис для обработки HTTP редиректов с использованием Strategy Pattern
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedirectFinderService {
    
    private final List<RedirectStrategy> strategies;
    private final FileGeneratorService fileGeneratorService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Подготовка данных для асинхронной обработки
     */
    public RedirectProcessingRequest prepareAsyncRequest(FileMetadata metadata, RedirectFinderDto dto) {
        try {
            // Получаем данные из файла
            List<List<String>> rawData = readFullFileData(metadata);
            log.info("Прочитано строк из файла для асинхронной обработки: {}", rawData.size());
            
            if (rawData.isEmpty()) {
                throw new IllegalArgumentException("Файл не содержит данных для обработки");
            }
            
            // Конвертируем в RedirectUrlData
            List<RedirectUrlData> urls = convertToRedirectUrlData(rawData, dto, metadata);
            
            return RedirectProcessingRequest.builder()
                    .urls(urls)
                    .maxRedirects(dto.getMaxRedirects())
                    .timeoutMs(dto.getTimeoutMs())
                    .delayMs(dto.getDelayMs() != null ? dto.getDelayMs() : 0)
                    .includeId(dto.getIdColumn() != null && dto.getIdColumn() >= 0)
                    .includeModel(dto.getModelColumn() != null && dto.getModelColumn() >= 0)
                    .idColumnName("ID")
                    .modelColumnName("Модель")
                    .build();
                    
        } catch (Exception e) {
            log.error("Ошибка подготовки данных для асинхронной обработки: {}", metadata.getOriginalFilename(), e);
            throw new RuntimeException("Ошибка подготовки данных: " + e.getMessage(), e);
        }
    }
    
    /**
     * Основной метод обработки файла с URL для поиска финальных ссылок (синхронный)
     */
    public byte[] processRedirectFinding(FileMetadata metadata, RedirectFinderDto dto) {
        String operationId = UUID.randomUUID().toString();
        
        log.info("=== НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===");
        log.info("Файл: {}", metadata.getOriginalFilename());
        log.info("Параметры: urlColumn={}, maxRedirects={}, timeout={}ms", 
                dto.getUrlColumn(), dto.getMaxRedirects(), dto.getTimeoutMs());
        log.info("Operation ID: {}", operationId);
        
        try {
            // Получаем данные из файла
            List<List<String>> rawData = readFullFileData(metadata);
            log.info("Прочитано строк из файла: {}", rawData.size());
            
            int totalUrls = calculateTotalUrls(rawData, metadata);
            sendProgressUpdate(operationId, "Начинаем обработку редиректов...", 0, 0, totalUrls);
            
            if (rawData.isEmpty()) {
                throw new IllegalArgumentException("Файл не содержит данных для обработки");
            }
            
            // Обрабатываем каждый URL
            List<RedirectResult> results = processUrls(rawData, dto, metadata, operationId);
            
            // Логируем статистику
            logProcessingStatistics(results);
            
            // Генерируем результат
            sendProgressUpdate(operationId, "Генерируем файл результатов...", 95, results.size(), results.size());
            byte[] resultData = generateResultFile(results, dto, metadata);
            
            // Уведомляем о завершении
            String fileName = "redirect-finder-result_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + 
                (".csv".equalsIgnoreCase(dto.getOutputFormat()) ? ".csv" : ".xlsx");
            sendCompletionNotification(operationId, fileName, results.size());
            
            return resultData;
            
        } catch (Exception e) {
            log.error("Критическая ошибка обработки файла: {}", metadata.getOriginalFilename(), e);
            sendErrorNotification(operationId, "Ошибка обработки: " + e.getMessage());
            throw new RuntimeException("Ошибка обработки файла: " + e.getMessage(), e);
        }
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
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " + filename);
        }
        
        return data;
    }
    
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

                    // Пропускаем полностью пустые строки
                    if (line.length == 0 || Arrays.stream(line).allMatch(cell -> cell == null || cell.trim().isEmpty())) {
                        continue;
                    }

                    data.add(Arrays.asList(line));
                }
            } catch (com.opencsv.exceptions.CsvValidationException e) {
                throw new IOException("Ошибка парсинга CSV файла: " + e.getMessage(), e);
            }
        }
        
        return data;
    }
    
    private List<List<String>> readExcelFile(FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        Path filePath = Path.of(metadata.getTempFilePath());
        
        try (InputStream fis = Files.newInputStream(filePath)) {
            Workbook workbook;
            
            // Определяем тип файла по расширению
            String filename = metadata.getOriginalFilename().toLowerCase();
            if (filename.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (filename.endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IllegalArgumentException("Неподдерживаемый формат Excel файла: " + filename);
            }
            
            // Работаем с первым листом
            Sheet sheet = workbook.getSheetAt(0);
            
            for (Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                
                // Проходим по всем ячейкам в строке
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    String cellValue = "";
                    
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                cellValue = cell.getStringCellValue();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    cellValue = cell.getDateCellValue().toString();
                                } else {
                                    // Преобразуем число в строку без десятичных знаков
                                    double numericValue = cell.getNumericCellValue();
                                    if (numericValue == (long) numericValue) {
                                        cellValue = String.valueOf((long) numericValue);
                                    } else {
                                        cellValue = String.valueOf(numericValue);
                                    }
                                }
                                break;
                            case BOOLEAN:
                                cellValue = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                cellValue = cell.getCellFormula();
                                break;
                            case BLANK:
                            default:
                                cellValue = "";
                                break;
                        }
                    }
                    
                    rowData.add(cellValue.trim());
                }
                
                // Добавляем строку только если она не пустая
                if (!rowData.isEmpty() && rowData.stream().anyMatch(s -> !s.isEmpty())) {
                    data.add(rowData);
                }
            }
            
            workbook.close();
            
        } catch (Exception e) {
            log.error("Ошибка чтения Excel файла: {}", metadata.getOriginalFilename(), e);
            throw new IOException("Ошибка чтения Excel файла: " + e.getMessage(), e);
        }
        
        return data;
    }
    
    private List<RedirectResult> processUrls(List<List<String>> rawData, RedirectFinderDto dto, FileMetadata metadata, String operationId) {
        List<RedirectResult> results = new ArrayList<>();
        
        // Получаем стратегии в порядке приоритета
        List<RedirectStrategy> sortedStrategies = strategies.stream()
                .sorted(Comparator.comparingInt(RedirectStrategy::getPriority))
                .collect(Collectors.toList());
        
        log.debug("Доступные стратегии: {}", 
                sortedStrategies.stream().map(RedirectStrategy::getStrategyName).collect(Collectors.toList()));
        
        int processedCount = 0;
        int startRow = metadata.getHasHeader() != null && metadata.getHasHeader() ? 1 : 0;
        
        for (int i = startRow; i < rawData.size(); i++) {
            List<String> row = rawData.get(i);
            processedCount++;
            
            try {
                String url = getColumnValue(row, dto.getUrlColumn());
                if (url == null || url.trim().isEmpty()) {
                    log.warn("Пропускаем строку {} - пустой URL", processedCount + 1);
                    continue;
                }
                
                String id = getColumnValue(row, dto.getIdColumn());
                String model = getColumnValue(row, dto.getModelColumn());
                
                log.debug("Обрабатываем URL {} из {}: {}", processedCount + 1, rawData.size() - startRow, url);
                
                RedirectResult result = processUrl(url, sortedStrategies, dto);
                
                // Добавляем дополнительные поля из исходной строки
                enhanceResultWithRowData(result, id, model);
                
                results.add(result);
                
                // Отправляем прогресс каждые 10% или каждые 10 URL
                int totalRows = rawData.size() - startRow;
                if (processedCount % Math.max(1, Math.min(totalRows / 10, 10)) == 0) {
                    int percentage = (processedCount * 100 / totalRows);
                    sendProgressUpdate(operationId, 
                        String.format("Обработано %d из %d URLs", processedCount, totalRows), 
                        percentage, processedCount, totalRows);
                    log.info("Прогресс: {}% ({}/{})", percentage, processedCount, totalRows);
                }
                
            } catch (Exception e) {
                log.error("Ошибка обработки строки {}: {}", processedCount + 1, e.getMessage(), e);
                // Продолжаем обработку даже при ошибках в отдельных строках
            }
        }
        
        return results;
    }
    
    private RedirectResult processUrl(String url, List<RedirectStrategy> strategies, RedirectFinderDto dto) {
        RedirectResult lastResult = null;

        // Если принудительно выбран Playwright, используем только его
        if (Boolean.TRUE.equals(dto.getUsePlaywright())) {
            log.debug("Принудительное использование Playwright для URL: {}", url);
            RedirectStrategy playwrightStrategy = strategies.stream()
                    .filter(s -> "playwright".equals(s.getStrategyName()))
                    .findFirst()
                    .orElse(null);

            if (playwrightStrategy != null) {
                return playwrightStrategy.followRedirects(url, dto.getMaxRedirects(), dto.getTimeoutMs());
            } else {
                log.warn("Playwright стратегия не найдена, используем стандартный алгоритм");
            }
        }

        for (RedirectStrategy strategy : strategies) {
            PageStatus previousStatus = lastResult != null ? lastResult.getStatus() : null;

            if (!strategy.canHandle(url, previousStatus)) {
                log.debug("Стратегия {} не может обработать URL: {} (предыдущий статус: {})",
                        strategy.getStrategyName(), url, previousStatus);
                continue;
            }

            log.debug("Пробуем стратегию: {} для URL: {}", strategy.getStrategyName(), url);

            RedirectResult result = strategy.followRedirects(url, dto.getMaxRedirects(), dto.getTimeoutMs());
            lastResult = result;

            // Если получили успешный результат или не можем улучшить - возвращаем
            if (result.getStatus() == PageStatus.REDIRECT ||
                result.getStatus() == PageStatus.NOT_FOUND) {
                return result;
            }

            // Для статуса OK проверяем: если это curl и URL не изменился,
            // возможно есть JavaScript-редирект - пробуем Playwright
            if (result.getStatus() == PageStatus.OK) {
                if ("curl".equals(strategy.getStrategyName()) &&
                    url.equals(result.getFinalUrl())) {
                    log.debug("Curl вернул OK без изменения URL, возможен JS-редирект. Пробуем Playwright.");
                    continue; // Продолжаем к следующей стратегии (Playwright)
                }
                return result;
            }

            // Если заблокированы, пробуем следующую стратегию
            if (result.getStatus() == PageStatus.BLOCKED) {
                log.debug("URL заблокирован стратегией {}, пробуем следующую", strategy.getStrategyName());
                continue;
            }

            // При других ошибках тоже пробуем следующую стратегию
            log.debug("Стратегия {} вернула статус {}, пробуем следующую",
                    strategy.getStrategyName(), result.getStatus());
        }

        // Если все стратегии не сработали, возвращаем последний результат
        return lastResult != null ? lastResult :
                RedirectResult.builder()
                        .originalUrl(url)
                        .finalUrl(url)
                        .redirectCount(0)
                        .status(PageStatus.ERROR)
                        .errorMessage("Все стратегии обработки не сработали")
                        .startTime(System.currentTimeMillis())
                        .endTime(System.currentTimeMillis())
                        .strategy("none")
                        .build();
    }
    
    String getColumnValue(List<String> row, Integer columnIndex) {
        if (columnIndex == null || columnIndex < 0 || columnIndex >= row.size()) {
            return null;
        }
        
        String value = row.get(columnIndex);
        return value != null ? value.trim() : null;
    }
    
    private void enhanceResultWithRowData(RedirectResult result, String id, String model) {
        result.setId(id);
        result.setModel(model);
        log.debug("Дополнительные данные: id={}, model={} для URL: {}", id, model, result.getOriginalUrl());
    }
    
    private void logProcessingStatistics(List<RedirectResult> results) {
        Map<PageStatus, Long> statusStats = results.stream()
                .collect(Collectors.groupingBy(RedirectResult::getStatus, Collectors.counting()));
        
        Map<String, Long> strategyStats = results.stream()
                .collect(Collectors.groupingBy(RedirectResult::getStrategy, Collectors.counting()));
        
        OptionalDouble avgTime = results.stream()
                .filter(r -> r.getProcessingTimeMs() != null)
                .mapToLong(RedirectResult::getProcessingTimeMs)
                .average();
        
        log.info("=== РЕЗУЛЬТАТЫ ОБРАБОТКИ ===");
        log.info("Всего обработано: {}", results.size());
        statusStats.forEach((status, count) -> 
                log.info("{}: {}", status.getDescription(), count));
        
        log.info("Статистика по стратегиям: {}", strategyStats);
        log.info("Среднее время обработки: {:.2f}ms", avgTime.orElse(0.0));
    }
    
    private byte[] generateResultFile(List<RedirectResult> results, RedirectFinderDto dto, FileMetadata metadata) {
        try {
            log.debug("Генерируем результирующий файл в формате: {}", dto.getOutputFormat());
            
            // Простое создание CSV для MVP
            if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
                return generateCsvFile(results);
            } else {
                // Для Excel пока возвращаем CSV
                log.warn("Excel формат пока не реализован, возвращаем CSV");
                return generateCsvFile(results);
            }
            
        } catch (Exception e) {
            log.error("Ошибка генерации результирующего файла", e);
            throw new RuntimeException("Не удалось сгенерировать результат", e);
        }
    }
    
    private byte[] generateCsvFile(List<RedirectResult> results) {
        try {
            // Определяем какие колонки включать на основе наличия данных
            boolean hasId = results.stream().anyMatch(r -> r.getId() != null && !r.getId().trim().isEmpty());
            boolean hasModel = results.stream().anyMatch(r -> r.getModel() != null && !r.getModel().trim().isEmpty());
            
            // Создаем шаблон экспорта с правильными настройками
            RedirectExportTemplate exportTemplate = RedirectExportTemplate.create(hasId, hasModel, "ID", "Модель");
            
            // Конвертируем результаты в Stream<Map<String, Object>>
            Stream<Map<String, Object>> dataStream = results.stream().map(this::convertToMap);
            
            // Генерируем файл с помощью FileGeneratorService
            String fileName = "redirect-finder-result_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".csv";
            
            Path filePath = fileGeneratorService.generateFile(
                dataStream, 
                results.size(), 
                exportTemplate.toExportTemplate(), 
                fileName
            );
            
            // Читаем файл в byte[] 
            return Files.readAllBytes(filePath);
            
        } catch (Exception e) {
            log.error("Ошибка генерации CSV файла через FileGeneratorService", e);
            // Fallback на старый метод в случае ошибки
            return generateCsvFileOld(results);
        }
    }
    
    private Map<String, Object> convertToMap(RedirectResult result) {
        Map<String, Object> map = new HashMap<>();
        // Ключи Map должны соответствовать exportColumnName в ExportTemplateField
        map.put("ID", result.getId() != null ? result.getId() : "");
        map.put("Модель", result.getModel() != null ? result.getModel() : "");
        map.put("Исходный URL", result.getOriginalUrl());
        map.put("Финальный URL", result.getFinalUrl());
        map.put("Количество редиректов", result.getRedirectCount() != null ? result.getRedirectCount() : 0);
        map.put("Статус", result.getStatus().getDescription());
        map.put("Стратегия", result.getStrategy());
        map.put("Время (мс)", result.getProcessingTimeMs() != null ? result.getProcessingTimeMs() : 0);
        map.put("HTTP код", result.getHttpCode() != null ? result.getHttpCode() : 0);
        map.put("Ошибка", result.getErrorMessage() != null ? result.getErrorMessage() : "");
        return map;
    }
    
    private byte[] generateCsvFileOld(List<RedirectResult> results) {
        StringBuilder csv = new StringBuilder();
        
        // Заголовки
        csv.append("ID,Модель,Исходный URL,Финальный URL,Количество редиректов,Статус,Стратегия,Время (мс),HTTP код,Ошибка\n");
        
        // Данные
        for (RedirectResult result : results) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",%d,%d,\"%s\"\n",
                    escapeCsv(result.getId() != null ? result.getId() : ""),
                    escapeCsv(result.getModel() != null ? result.getModel() : ""),
                    escapeCsv(result.getOriginalUrl()),
                    escapeCsv(result.getFinalUrl()),
                    result.getRedirectCount() != null ? result.getRedirectCount() : 0,
                    escapeCsv(result.getStatus().getDescription()),
                    escapeCsv(result.getStrategy()),
                    result.getProcessingTimeMs() != null ? result.getProcessingTimeMs() : 0,
                    result.getHttpCode() != null ? result.getHttpCode() : 0,
                    escapeCsv(result.getErrorMessage() != null ? result.getErrorMessage() : "")
            ));
        }
        
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Экранируем кавычки в CSV
        return value.replace("\"", "\"\"");
    }

    /**
     * Функциональный интерфейс для колбэка прогресса
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int processed, int total);
    }

    /**
     * Обработка списка URL с поддержкой прогресса и задержки
     */
    public List<RedirectResult> processRedirects(List<RedirectUrlData> urls, int maxRedirects, int timeoutMs, 
                                               int delayMs, ProgressCallback progressCallback) {
        List<RedirectResult> results = new ArrayList<>();
        
        // Получаем стратегии в порядке приоритета
        List<RedirectStrategy> sortedStrategies = strategies.stream()
                .sorted(Comparator.comparingInt(RedirectStrategy::getPriority))
                .collect(Collectors.toList());
        
        log.info("Начинаем асинхронную обработку {} URLs с задержкой {}мс", urls.size(), delayMs);
        
        for (int i = 0; i < urls.size(); i++) {
            RedirectUrlData urlData = urls.get(i);
            
            try {
                if (urlData.getUrl() == null || urlData.getUrl().trim().isEmpty()) {
                    log.warn("Пропускаем URL {} - пустое значение", i + 1);
                    continue;
                }
                
                log.debug("Обрабатываем URL {} из {}: {}", i + 1, urls.size(), urlData.getUrl());
                
                RedirectResult result = processUrlWithStrategies(urlData.getUrl(), sortedStrategies, maxRedirects, timeoutMs);
                
                // Добавляем дополнительные поля
                result.setId(urlData.getId());
                result.setModel(urlData.getModel());
                
                results.add(result);
                
                // Уведомляем о прогрессе
                if (progressCallback != null) {
                    progressCallback.onProgress(i + 1, urls.size());
                }
                
                // Задержка между запросами (кроме последнего)
                if (delayMs > 0 && i < urls.size() - 1) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Прервана обработка URL на элементе {}", i + 1);
                        break;
                    }
                }
                
            } catch (Exception e) {
                log.error("Ошибка обработки URL {}: {}", i + 1, e.getMessage(), e);
                
                // Добавляем результат с ошибкой
                RedirectResult errorResult = buildAsyncErrorResult(urlData.getUrl(), e.getMessage());
                errorResult.setId(urlData.getId());
                errorResult.setModel(urlData.getModel());
                results.add(errorResult);
                
                if (progressCallback != null) {
                    progressCallback.onProgress(i + 1, urls.size());
                }
            }
        }
        
        log.info("Завершена асинхронная обработка {} URLs, получено {} результатов", urls.size(), results.size());
        return results;
    }

    /**
     * Обработка одного URL с параметрами
     */
    private RedirectResult processUrlWithStrategies(String url, List<RedirectStrategy> strategies, int maxRedirects, int timeoutMs) {
        for (RedirectStrategy strategy : strategies) {
            if (!strategy.canHandle(url, null)) {
                continue;
            }
            
            try {
                return strategy.followRedirects(url, maxRedirects, timeoutMs);
            } catch (Exception e) {
                log.warn("Стратегия {} не смогла обработать URL {}: {}", 
                        strategy.getStrategyName(), url, e.getMessage());
            }
        }
        
        return buildAsyncErrorResult(url, "Все стратегии не смогли обработать URL");
    }

    private RedirectResult buildAsyncErrorResult(String url, String errorMessage) {
        return RedirectResult.builder()
                .originalUrl(url)
                .finalUrl(url)
                .redirectCount(0)
                .status(PageStatus.ERROR)
                .errorMessage(errorMessage)
                .startTime(System.currentTimeMillis())
                .endTime(System.currentTimeMillis())
                .strategy("error")
                .build();
    }

    /**
     * Генерация файла результатов для асинхронной обработки
     */
    public byte[] generateResultFile(List<RedirectResult> results, RedirectProcessingRequest request, String fileName) {
        try {
            // Создаем шаблон экспорта
            RedirectExportTemplate exportTemplate = RedirectExportTemplate.create(
                    request.isIncludeId(), 
                    request.isIncludeModel(),
                    request.getIdColumnName(), 
                    request.getModelColumnName()
            );
            
            // Конвертируем результаты в Stream<Map<String, Object>>
            Stream<Map<String, Object>> dataStream = results.stream().map(this::convertToMap);
            
            // Генерируем файл
            Path filePath = fileGeneratorService.generateFile(
                    dataStream, 
                    results.size(), 
                    exportTemplate.toExportTemplate(), 
                    fileName
            );
            
            return Files.readAllBytes(filePath);
            
        } catch (Exception e) {
            log.error("Ошибка генерации файла результатов", e);
            throw new RuntimeException("Ошибка генерации файла: " + e.getMessage(), e);
        }
    }
    
    private int calculateTotalUrls(List<List<String>> rawData, FileMetadata metadata) {
        int startRow = metadata.getHasHeader() != null && metadata.getHasHeader() ? 1 : 0;
        return rawData.size() - startRow;
    }
    
    private void sendProgressUpdate(String operationId, String message, int percentage, int processed, int total) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage(message);
            progress.setPercentage(percentage);
            progress.setProcessed(processed);
            progress.setTotal(total);
            progress.setStatus("IN_PROGRESS");
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
        } catch (Exception e) {
            log.error("Ошибка отправки прогресса для операции {}", operationId, e);
        }
    }
    
    private void sendCompletionNotification(String operationId, String fileName, int processedCount) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage("Обработка завершена успешно");
            progress.setPercentage(100);
            progress.setProcessed(processedCount);
            progress.setTotal(processedCount);
            progress.setStatus("COMPLETED");
            progress.setFileName(fileName);
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
            // Общее уведомление
            notificationService.sendGeneralNotification(
                String.format("Обработка редиректов завершена. Обработано %d URLs. Файл: %s", 
                    processedCount, fileName), 
                NotificationDto.NotificationType.SUCCESS
            );
            
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления о завершении для операции {}", operationId, e);
        }
    }
    
    private void sendErrorNotification(String operationId, String errorMessage) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage(errorMessage);
            progress.setStatus("ERROR");
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
            // Общее уведомление
            notificationService.sendGeneralNotification(
                "Ошибка обработки редиректов: " + errorMessage, 
                NotificationDto.NotificationType.ERROR
            );
            
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления об ошибке для операции {}", operationId, e);
        }
    }
    
    /**
     * Конвертация данных из файла в RedirectUrlData
     */
    private List<RedirectUrlData> convertToRedirectUrlData(List<List<String>> rawData, RedirectFinderDto dto, FileMetadata metadata) {
        List<RedirectUrlData> urls = new ArrayList<>();
        
        int startRow = metadata.getHasHeader() != null && metadata.getHasHeader() ? 1 : 0;
        
        for (int i = startRow; i < rawData.size(); i++) {
            List<String> row = rawData.get(i);
            
            String url = getColumnValue(row, dto.getUrlColumn());
            if (url == null || url.trim().isEmpty()) {
                log.warn("Пропускаем строку {} - пустой URL", i + 1);
                continue;
            }
            
            String id = getColumnValue(row, dto.getIdColumn());
            String model = getColumnValue(row, dto.getModelColumn());
            
            urls.add(RedirectUrlData.builder()
                    .url(url.trim())
                    .id(id)
                    .model(model)
                    .build());
        }
        
        return urls;
    }
}