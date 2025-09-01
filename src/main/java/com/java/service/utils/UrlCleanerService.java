package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.UrlCleanerDto;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Сервис для очистки URL от UTM и реферальных параметров
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCleanerService {

    private final FileGeneratorService fileGeneratorService;
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
        "cpc", "cpm", "cpa", "sponsored", "do-waremd5", "nid", "ogv", "clid",
        "yclid", "from", "track", "tracking", "tracker", "_ga", "_gl", "mc_cid", "mc_eid",
        "q", "query", "search", "keyword", "kw", "s", "term", "text", 
        "sessionid", "sid", "jsessionid", "phpsessid", "sessid",
        "_gid", "_gat", "__utma", "__utmb", "__utmc", "__utmt", "__utmz",
        "WT.mc_id", "wt_mc", "pk_campaign", "pk_kwd", "pk_medium", "pk_source"
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
        
        // Создаем ExportTemplate из DTO
        ExportTemplate template = createExportTemplate(dto);
        
        // Преобразуем результаты в Stream<Map<String, Object>>
        Stream<Map<String, Object>> dataStream = results.stream()
                .map(this::convertResultToMap);
        
        // Генерируем файл через FileGeneratorService
        String fileName = "url-cleaner-result_" + System.currentTimeMillis();
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
    private ExportTemplate createExportTemplate(UrlCleanerDto dto) {
        ExportTemplate template = ExportTemplate.builder()
                .name("URL Cleaner Export")
                .description("Экспорт очищенных URL")
                .fileFormat("excel".equalsIgnoreCase(dto.getOutputFormat()) ? "XLSX" : "CSV")
                .csvDelimiter(dto.getCsvDelimiter())
                .csvEncoding(dto.getCsvEncoding())
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();
        
        // Добавляем поля на основе выбранных колонок
        List<ExportTemplateField> fields = new ArrayList<>();
        int orderIndex = 1;
        
        // Добавляем дополнительные колонки если они выбраны
        if (dto.getIdColumn() != null) {
            fields.add(createTemplateField(template, "ID", "ID", orderIndex++));
        }
        if (dto.getModelColumn() != null) {
            fields.add(createTemplateField(template, "Model", "Model", orderIndex++));
        }
        if (dto.getBarcodeColumn() != null) {
            fields.add(createTemplateField(template, "Barcode", "Barcode", orderIndex++));
        }
        
        // Основные колонки URL (всегда присутствуют)
        fields.add(createTemplateField(template, "Исходная URL", "Исходная URL", orderIndex++));
        fields.add(createTemplateField(template, "Очищенная URL", "Очищенная URL", orderIndex++));
        
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
    private Map<String, Object> convertResultToMap(UrlCleanResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        
        if (result.getId() != null) {
            map.put("ID", result.getId());
        }
        if (result.getModel() != null) {
            map.put("Model", result.getModel());
        }
        if (result.getBarcode() != null) {
            map.put("Barcode", result.getBarcode());
        }
        
        map.put("Исходная URL", result.getOriginalUrl());
        map.put("Очищенная URL", result.getCleanedUrl());
        
        return map;
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
            
            log.debug("URL: {}, isYandexMarket: {}, preserveYandexSku: {}", urlString, isYandexMarket, dto.isPreserveYandexSku());
            
            for (Map.Entry<String, String> param : params.entrySet()) {
                String key = param.getKey();
                boolean shouldKeep = false;
                
                // Сохранить sku для Yandex Market ТОЛЬКО если включена опция
                if (isYandexMarket && dto.isPreserveYandexSku() && "sku".equals(key)) {
                    shouldKeep = true;
                    log.debug("Сохраняю sku параметр для Yandex Market: {}={}", key, param.getValue());
                }
                // Удалить sku для Yandex Market если НЕ включена опция сохранения
                else if (isYandexMarket && !dto.isPreserveYandexSku() && "sku".equals(key)) {
                    shouldKeep = false;
                    log.debug("Удаляю sku параметр для Yandex Market (опция отключена): {}={}", key, param.getValue());
                }
                // Проверить все остальные параметры на удаление
                else if (!shouldRemoveParam(key, dto)) {
                    shouldKeep = true;
                    log.debug("Сохраняю параметр: {}={}", key, param.getValue());
                } else {
                    log.debug("Удаляю параметр: {}={}", key, param.getValue());
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
        
        // Удаляем UTM параметры, если установлен флаг удаления
        if (dto.isRemoveUtmParams() && UTM_PARAMS.contains(lowerParam)) {
            return true;
        }
        
        // Удаляем реферальные параметры, если установлен флаг удаления
        if (dto.isRemoveReferralParams() && REFERRAL_PARAMS.contains(lowerParam)) {
            return true;
        }
        
        // Удаляем трекинговые параметры, если установлен флаг удаления
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