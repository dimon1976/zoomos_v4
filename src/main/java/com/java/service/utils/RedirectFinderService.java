package com.java.service.utils;

import com.java.dto.utils.RedirectFinderDto;
import com.java.model.entity.FileMetadata;
import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;
import com.java.service.exports.FileGeneratorService;
import com.java.service.utils.redirect.RedirectStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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
    
    /**
     * Основной метод обработки файла с URL для поиска финальных ссылок
     */
    public byte[] processRedirectFinding(FileMetadata metadata, RedirectFinderDto dto) {
        log.info("=== НАЧАЛО ОБРАБОТКИ РЕДИРЕКТОВ ===");
        log.info("Файл: {}", metadata.getOriginalFilename());
        log.info("Параметры: urlColumn={}, maxRedirects={}, timeout={}ms", 
                dto.getUrlColumn(), dto.getMaxRedirects(), dto.getTimeoutMs());
        
        try {
            // Получаем данные из файла
            List<List<String>> rawData = readFullFileData(metadata);
            log.info("Прочитано строк из файла: {}", rawData.size());
            
            if (rawData.isEmpty()) {
                throw new IllegalArgumentException("Файл не содержит данных для обработки");
            }
            
            // Обрабатываем каждый URL
            List<RedirectResult> results = processUrls(rawData, dto, metadata);
            
            // Логируем статистику
            logProcessingStatistics(results);
            
            // Генерируем результат
            return generateResultFile(results, dto, metadata);
            
        } catch (Exception e) {
            log.error("Критическая ошибка обработки файла: {}", metadata.getOriginalFilename(), e);
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
        Path filePath = Path.of(metadata.getTempFilePath());
        
        try (Stream<String> lines = Files.lines(filePath)) {
            lines.forEach(line -> {
                // Простой парсинг CSV - для MVP достаточно
                String[] values = line.split(",");
                List<String> row = new ArrayList<>();
                for (String value : values) {
                    row.add(value.trim().replaceAll("^\"|\"$", "")); // убираем кавычки
                }
                data.add(row);
            });
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
    
    private List<RedirectResult> processUrls(List<List<String>> rawData, RedirectFinderDto dto, FileMetadata metadata) {
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
                
                // Логируем прогресс каждые 10%
                int totalRows = rawData.size() - startRow;
                if (processedCount % Math.max(1, totalRows / 10) == 0) {
                    log.info("Прогресс: {}% ({}/{})", 
                            (processedCount * 100 / totalRows), processedCount, totalRows);
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
        
        for (RedirectStrategy strategy : strategies) {
            PageStatus previousStatus = lastResult != null ? lastResult.getStatus() : null;
            
            if (!strategy.canHandle(url, previousStatus)) {
                log.debug("Стратегия {} не может обработать URL: {} (предыдущий статус: {})", 
                        strategy.getStrategyName(), url, previousStatus);
                continue;
            }
            
            // Принудительное использование Playwright если установлен флаг
            if (dto.getUsePlaywright() && !"playwright".equals(strategy.getStrategyName())) {
                continue;
            }
            
            log.debug("Пробуем стратегию: {} для URL: {}", strategy.getStrategyName(), url);
            
            RedirectResult result = strategy.followRedirects(url, dto.getMaxRedirects(), dto.getTimeoutMs());
            lastResult = result;
            
            // Если получили успешный результат или не можем улучшить - возвращаем
            if (result.getStatus() == PageStatus.OK || 
                result.getStatus() == PageStatus.REDIRECT ||
                result.getStatus() == PageStatus.NOT_FOUND) {
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
}