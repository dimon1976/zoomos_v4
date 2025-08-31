package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.UrlCleanerDto;
import com.java.model.entity.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Сервис для очистки URL от UTM и реферальных параметров
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCleanerService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Список параметров для удаления
    private static final Set<String> UTM_PARAMS = Set.of(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id"
    );
    
    private static final Set<String> REFERRAL_PARAMS = Set.of(
        "ref", "referer", "referrer", "affiliate_id", "partner_id", "campaign_id",
        "source", "medium", "campaign", "gclid", "fbclid", "msclkid", "dclid"
    );
    
    private static final Set<String> TRACKING_PARAMS = Set.of(
        "cpc", "cpm", "cpa", "sponsored", "do-waremd5", "nid", "ogV", "clid",
        "yclid", "from", "track", "tracking", "tracker", "_ga", "_gl", "mc_cid", "mc_eid"
    );

    /**
     * Обработка очистки URL
     */
    public byte[] processUrlCleaning(FileMetadata metadata, UrlCleanerDto dto) throws IOException {
        
        List<List<String>> data = parseSampleData(metadata.getSampleData());
        List<String> headers = parseHeaders(metadata.getColumnHeaders());
        
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для обработки");
        }

        validateColumns(data, dto);
        
        List<UrlCleanResult> results = processData(data, dto);
        
        if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
            return generateCsvFile(results, dto);
        } else {
            return generateExcelFile(results);
        }
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
    private void validateColumns(List<List<String>> data, UrlCleanerDto dto) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для обработки");
        }

        int maxColumns = data.get(0).size();
        
        if (dto.getUrlColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка URL превышает количество колонок в файле");
        }
        
        // Проверка необязательных колонок
        if (dto.getIdColumn() != null && dto.getIdColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка ID превышает количество колонок в файле");
        }
        
        if (dto.getModelColumn() != null && dto.getModelColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка Model превышает количество колонок в файле");
        }
        
        if (dto.getBarcodeColumn() != null && dto.getBarcodeColumn() >= maxColumns) {
            throw new IllegalArgumentException("Колонка Barcode превышает количество колонок в файле");
        }
    }

    /**
     * Обработка данных с очисткой URL
     */
    private List<UrlCleanResult> processData(List<List<String>> data, UrlCleanerDto dto) {
        List<UrlCleanResult> results = new ArrayList<>();
        
        for (List<String> row : data) {
            if (row.size() <= dto.getUrlColumn()) {
                continue;
            }

            String originalUrl = getColumnValue(row, dto.getUrlColumn());
            
            if (isEmpty(originalUrl) || !isValidUrl(originalUrl)) {
                log.debug("Пропущена невалидная URL: {}", originalUrl);
                continue;
            }

            String cleanedUrl = cleanUrl(originalUrl, dto);
            
            UrlCleanResult result = new UrlCleanResult();
            result.setOriginalUrl(originalUrl);
            result.setCleanedUrl(cleanedUrl);
            result.setId(getColumnValue(row, dto.getIdColumn()));
            result.setModel(getColumnValue(row, dto.getModelColumn()));
            result.setBarcode(getColumnValue(row, dto.getBarcodeColumn()));
            
            results.add(result);
        }
        
        log.info("Обработано {} URL, очищено успешно: {}", 
                 results.size(), results.stream().mapToInt(r -> r.getOriginalUrl().equals(r.getCleanedUrl()) ? 0 : 1).sum());
        
        return results;
    }

    /**
     * Очистка URL согласно настройкам
     */
    private String cleanUrl(String urlString, UrlCleanerDto dto) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            String path = url.getPath();
            String query = url.getQuery();
            
            if (query == null) {
                return urlString; // Нет параметров для очистки
            }
            
            Map<String, String> params = parseQueryParams(query);
            Map<String, String> filteredParams = new LinkedHashMap<>();
            
            // Специальная логика для market.yandex.ru
            boolean isYandexMarket = host != null && host.contains("market.yandex.ru");
            
            for (Map.Entry<String, String> param : params.entrySet()) {
                String key = param.getKey();
                boolean shouldKeep = false;
                
                // Сохранить sku для Yandex Market
                if (isYandexMarket && dto.isPreserveYandexSku() && "sku".equals(key)) {
                    shouldKeep = true;
                }
                // Проверить все остальные параметры на удаление
                else if (!shouldRemoveParam(key, dto)) {
                    shouldKeep = true;
                }
                
                if (shouldKeep) {
                    filteredParams.put(key, param.getValue());
                }
            }
            
            // Собрать очищенную URL
            StringBuilder cleanedUrl = new StringBuilder();
            cleanedUrl.append(url.getProtocol()).append("://").append(host);
            if (url.getPort() != -1) {
                cleanedUrl.append(":").append(url.getPort());
            }
            cleanedUrl.append(path);
            
            if (!filteredParams.isEmpty()) {
                cleanedUrl.append("?");
                boolean first = true;
                for (Map.Entry<String, String> param : filteredParams.entrySet()) {
                    if (!first) cleanedUrl.append("&");
                    cleanedUrl.append(param.getKey()).append("=").append(param.getValue());
                    first = false;
                }
            }
            
            return cleanedUrl.toString();
            
        } catch (MalformedURLException e) {
            log.warn("Невозможно распарсить URL: {}", urlString, e);
            return urlString; // Возвращаем исходную URL при ошибке
        }
    }

    /**
     * Парсинг параметров запроса
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length >= 1) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                params.put(key, value);
            }
        }
        
        return params;
    }

    /**
     * Определение, нужно ли удалять параметр
     */
    private boolean shouldRemoveParam(String paramName, UrlCleanerDto dto) {
        String lowerParam = paramName.toLowerCase();
        
        if (dto.isRemoveUtmParams() && UTM_PARAMS.contains(lowerParam)) {
            return true;
        }
        
        if (dto.isRemoveReferralParams() && REFERRAL_PARAMS.contains(lowerParam)) {
            return true;
        }
        
        if (dto.isRemoveTrackingParams() && TRACKING_PARAMS.contains(lowerParam)) {
            return true;
        }
        
        return false;
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
     * Базовая валидация URL
     */
    private boolean isValidUrl(String urlString) {
        try {
            new URL(urlString);
            return urlString.toLowerCase().startsWith("http");
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Генерация CSV файла
     */
    private byte[] generateCsvFile(List<UrlCleanResult> results, UrlCleanerDto dto) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, 
                "UTF-8".equalsIgnoreCase(dto.getCsvEncoding()) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1)) {
            
            String delimiter = dto.getCsvDelimiter();
            
            // Заголовки
            List<String> headers = new ArrayList<>();
            if (dto.getIdColumn() != null) headers.add("ID");
            if (dto.getModelColumn() != null) headers.add("Model");
            if (dto.getBarcodeColumn() != null) headers.add("Barcode");
            headers.add("Исходная URL");
            headers.add("Очищенная URL");
            
            writer.write(String.join(delimiter, headers) + "\n");
            
            // Данные
            for (UrlCleanResult result : results) {
                List<String> values = new ArrayList<>();
                
                if (dto.getIdColumn() != null) {
                    values.add(escapeCSV(result.getId(), delimiter));
                }
                if (dto.getModelColumn() != null) {
                    values.add(escapeCSV(result.getModel(), delimiter));
                }
                if (dto.getBarcodeColumn() != null) {
                    values.add(escapeCSV(result.getBarcode(), delimiter));
                }
                values.add(escapeCSV(result.getOriginalUrl(), delimiter));
                values.add(escapeCSV(result.getCleanedUrl(), delimiter));
                
                writer.write(String.join(delimiter, values) + "\n");
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
     * Генерация Excel файла
     */
    private byte[] generateExcelFile(List<UrlCleanResult> results) throws IOException {
        
        try (Workbook workbook = new XSSFWorkbook(); 
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Результат очистки URL");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // Определяем заголовки
            List<String> headers = new ArrayList<>();
            boolean hasId = results.stream().anyMatch(r -> r.getId() != null);
            boolean hasModel = results.stream().anyMatch(r -> r.getModel() != null);
            boolean hasBarcode = results.stream().anyMatch(r -> r.getBarcode() != null);
            
            if (hasId) headers.add("ID");
            if (hasModel) headers.add("Model");
            if (hasBarcode) headers.add("Barcode");
            headers.add("Исходная URL");
            headers.add("Очищенная URL");
            
            // Заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            
            // Данные
            int rowIndex = 1;
            for (UrlCleanResult result : results) {
                Row dataRow = sheet.createRow(rowIndex++);
                int colIndex = 0;
                
                if (hasId) {
                    Cell cell = dataRow.createCell(colIndex++);
                    cell.setCellValue(result.getId() != null ? result.getId() : "");
                    cell.setCellStyle(dataStyle);
                }
                
                if (hasModel) {
                    Cell cell = dataRow.createCell(colIndex++);
                    cell.setCellValue(result.getModel() != null ? result.getModel() : "");
                    cell.setCellStyle(dataStyle);
                }
                
                if (hasBarcode) {
                    Cell cell = dataRow.createCell(colIndex++);
                    cell.setCellValue(result.getBarcode() != null ? result.getBarcode() : "");
                    cell.setCellStyle(dataStyle);
                }
                
                Cell originalUrlCell = dataRow.createCell(colIndex++);
                originalUrlCell.setCellValue(result.getOriginalUrl());
                originalUrlCell.setCellStyle(dataStyle);
                
                Cell cleanedUrlCell = dataRow.createCell(colIndex);
                cleanedUrlCell.setCellValue(result.getCleanedUrl());
                cleanedUrlCell.setCellStyle(dataStyle);
            }
            
            // Автоподбор ширины колонок
            for (int i = 0; i < headers.size(); i++) {
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
     * Класс для хранения результата очистки URL
     */
    public static class UrlCleanResult {
        private String id;
        private String model;
        private String barcode;
        private String originalUrl;
        private String cleanedUrl;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        
        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }
        
        public String getOriginalUrl() { return originalUrl; }
        public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
        
        public String getCleanedUrl() { return cleanedUrl; }
        public void setCleanedUrl(String cleanedUrl) { this.cleanedUrl = cleanedUrl; }
    }
}