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
import com.java.util.FileReaderUtils;
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
    private final FileReaderUtils fileReaderUtils;
    
    /**
     * Подготовка данных для асинхронной обработки
     */
    public RedirectProcessingRequest prepareAsyncRequest(FileMetadata metadata, RedirectFinderDto dto) {
        try {
            // Получаем данные из файла
            List<List<String>> rawData = fileReaderUtils.readAllRows(metadata);
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
                    .usePlaywright(dto.getUsePlaywright() != null ? dto.getUsePlaywright() : false)
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
            List<List<String>> rawData = fileReaderUtils.readAllRows(metadata);
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
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".csv";
            sendCompletionNotification(operationId, fileName, results.size());
            
            return resultData;
            
        } catch (Exception e) {
            log.error("Критическая ошибка обработки файла: {}", metadata.getOriginalFilename(), e);
            sendErrorNotification(operationId, "Ошибка обработки: " + e.getMessage());
            throw new RuntimeException("Ошибка обработки файла: " + e.getMessage(), e);
        }
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

        return applyStrategies(url, strategies, dto.getMaxRedirects(), dto.getTimeoutMs());
    }

    /**
     * Единая fallback-логика перебора стратегий.
     * REDIRECT, NOT_FOUND → возвращаем сразу.
     * OK + URL изменился → возвращаем (HTTP-редирект найден).
     * OK + URL не изменился + non-browser → продолжаем (возможен JS/meta-refresh).
     * OK + URL не изменился + browser + HTTP 3xx → продолжаем (аномалия: сайт детектировал браузер).
     * OK + URL не изменился + browser + HTTP 2xx → возвращаем (редиректов точно нет).
     * BLOCKED или ERROR → пробуем следующую стратегию.
     */
    private RedirectResult applyStrategies(String url, List<RedirectStrategy> sortedStrategies,
                                           int maxRedirects, int timeoutMs) {
        RedirectResult lastResult = null;
        PageStatus previousStatus = null;

        for (RedirectStrategy strategy : sortedStrategies) {
            if (!strategy.canHandle(url, previousStatus)) {
                log.debug("Стратегия {} не может обработать URL: {} (предыдущий статус: {})",
                        strategy.getStrategyName(), url, previousStatus);
                continue;
            }

            log.debug("Пробуем стратегию: {} для URL: {}", strategy.getStrategyName(), url);

            try {
                RedirectResult result = strategy.followRedirects(url, maxRedirects, timeoutMs);
                lastResult = result;
                previousStatus = result.getStatus();

                if (result.getStatus() == PageStatus.REDIRECT || result.getStatus() == PageStatus.NOT_FOUND) {
                    return result;
                }

                if (result.getStatus() == PageStatus.OK) {
                    // URL изменился → редирект найден, возвращаем
                    if (!url.equals(result.getFinalUrl())) {
                        return result;
                    }
                    // Non-browser стратегия (curl, webclient, httpclient, jsoup) вернула OK без изменения URL →
                    // возможен JS-редирект или meta-refresh, пробуем следующую стратегию
                    if (!strategy.isBrowserBased()) {
                        log.debug("{} вернул OK без изменения URL, пробуем следующую стратегию (возможен JS/meta-refresh)",
                                strategy.getStrategyName());
                        continue;
                    }
                    // Browser стратегия: URL не изменился, но HTTP код — редирект (3xx) →
                    // сайт обнаружил браузер и вернул его обратно; пробуем следующий браузер
                    Integer httpCode = result.getHttpCode();
                    if (httpCode != null && httpCode >= 300 && httpCode < 400) {
                        log.debug("{} вернул HTTP {} без изменения URL — аномальный редирект, пробуем следующую стратегию",
                                strategy.getStrategyName(), httpCode);
                        continue;
                    }
                    // Browser подтвердил: URL не изменился, HTTP 2xx → редиректов нет
                    return result;
                }

                // BLOCKED или ERROR → пробуем следующую стратегию
                log.debug("Стратегия {} вернула статус {}, пробуем следующую",
                        strategy.getStrategyName(), result.getStatus());
            } catch (Exception e) {
                log.warn("Стратегия {} не смогла обработать {}: {}",
                        strategy.getStrategyName(), url, e.getMessage());
            }
        }

        return lastResult != null ? lastResult : buildErrorResult(url, "Все стратегии не сработали");
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
            return generateCsvFile(results);
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
                                               int delayMs, boolean usePlaywright, ProgressCallback progressCallback) {
        List<RedirectResult> results = new ArrayList<>();

        // Получаем стратегии
        List<RedirectStrategy> sortedStrategies;

        if (usePlaywright) {
            // Если принудительно выбран Playwright, используем только его
            log.debug("Принудительное использование Playwright для всех URL");
            RedirectStrategy playwrightStrategy = strategies.stream()
                    .filter(s -> "playwright".equals(s.getStrategyName()))
                    .findFirst()
                    .orElse(null);

            if (playwrightStrategy != null) {
                sortedStrategies = List.of(playwrightStrategy);
            } else {
                log.warn("Playwright стратегия не найдена, используем стандартный алгоритм");
                sortedStrategies = strategies.stream()
                        .sorted(Comparator.comparingInt(RedirectStrategy::getPriority))
                        .collect(Collectors.toList());
            }
        } else {
            // Обычный порядок приоритетов
            sortedStrategies = strategies.stream()
                    .sorted(Comparator.comparingInt(RedirectStrategy::getPriority))
                    .collect(Collectors.toList());
        }

        log.info("Начинаем асинхронную обработку {} URLs с задержкой {}мс, usePlaywright={}",
                urls.size(), delayMs, usePlaywright);
        
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
                RedirectResult errorResult = buildErrorResult(urlData.getUrl(), e.getMessage());
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
     * Обработка одного URL с параметрами (async-путь) — делегирует в applyStrategies
     */
    private RedirectResult processUrlWithStrategies(String url, List<RedirectStrategy> strategies, int maxRedirects, int timeoutMs) {
        return applyStrategies(url, strategies, maxRedirects, timeoutMs);
    }

    private RedirectResult buildErrorResult(String url, String errorMessage) {
        return RedirectResult.builder()
                .originalUrl(url)
                .finalUrl(url)
                .redirectCount(0)
                .status(PageStatus.ERROR)
                .errorMessage(errorMessage)
                .startTime(System.currentTimeMillis())
                .endTime(System.currentTimeMillis())
                .strategy("none")
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