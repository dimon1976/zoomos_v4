package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.StatsProcessDto;
import com.java.model.entity.FileMetadata;
import com.java.service.file.FileAnalyzerService;
import com.java.service.exports.style.ExcelStyleFactory;
import com.java.service.exports.style.ExcelStyles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для обработки файлов статистики
 * Адаптирует логику из старой версии StatisticService для новой архитектуры
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsProcessorService {

    private final FileAnalyzerService fileAnalyzerService;
    private final ExcelStyleFactory excelStyleFactory;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Базовые колонки (из старой версии StatisticService, строка 46)
    private static final List<Integer> BASE_COLUMNS = Arrays.asList(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 19, 20, 22, 23, 24
    );
    
    // Базовые заголовки (из старой версии StatisticService, строки 36-38)
    private static final List<String> BASE_HEADERS = Arrays.asList("Клиент", "ID связи", "ID клиента", "Верхняя категория", "Категория клиента", "Бренд клиента",
            "Модель клиента", "Код производителя", "Штрих-код клиента", "Статус клиента", "Цена конкурента",
            "Модель конкурента", "Код производителя", "ID конкурента", "Конкурент", "Конкурент вкл.");
    
    // Список пользователей для замены (из старой версии MappingUtils.listUsers)
    private static final Set<String> KNOWN_USERS = Set.of(
        "zms-cron", "zms-mappings-import", "maudau.com.ua", "detmir.ru-2"
    );

    /**
     * Обработка файла статистики с настройками из DTO
     */
    public byte[] processStatsFile(FileMetadata metadata, StatsProcessDto dto) throws IOException {
        log.info("Начинаем обработку файла статистики: {}", metadata.getOriginalFilename());
        
        // Извлекаем данные из JSON полей FileMetadata
        List<List<String>> data = parseSampleData(metadata.getSampleData());
        
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Файл не содержит данных для обработки");
        }
        
        // Определяем колонки для экспорта
        List<Integer> columnsToExport = getColumnsToExport(dto);
        
        // Обрабатываем данные
        List<List<String>> processedData = processData(data, columnsToExport, dto.isSourceReplace());
        
        // Формируем заголовки
        List<String> headers = getHeaders(dto);
        
        // Генерируем файл результата
        if ("csv".equalsIgnoreCase(dto.getOutputFormat())) {
            return generateCsvFile(processedData, headers, dto);
        } else {
            return generateExcelFile(processedData, headers);
        }
    }

    /**
     * Определение колонок для экспорта на основе настроек DTO
     * Логика из старой версии StatisticService.getColumnList()
     */
    private List<Integer> getColumnsToExport(StatsProcessDto dto) {
        List<Integer> columns = new ArrayList<>(BASE_COLUMNS);
        
        // Добавляем дополнительные колонки по аналогии со старой версией (строки 100-110)
        if (dto.isShowCompetitorUrl()) {
            columns.add(27); // URL конкурента
        }
        if (dto.isShowSource()) {
            columns.add(28); // Кто добавил
        }
        if (dto.isShowDateAdd()) {
            columns.add(29); // Дата добавления
        }
        
        return columns;
    }

    /**
     * Формирование заголовков на основе выбранных опций
     * Логика из старой версии StatisticService.addAdditionalColumnsToString()
     */
    private List<String> getHeaders(StatsProcessDto dto) {
        List<String> headers = new ArrayList<>(BASE_HEADERS);
        
        if (dto.isShowCompetitorUrl()) {
            headers.add("URL");
        }
        if (dto.isShowSource()) {
            headers.add("Добавил");
        }
        if (dto.isShowDateAdd()) {
            headers.add("Добавил");
        }
        
        return headers;
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
     * Обработка данных файла
     * Адаптирует логику из старой версии StatisticService.getResultList()
     */
    private List<List<String>> processData(List<List<String>> fileData, List<Integer> columnsToExport, boolean sourceReplace) {
        return fileData.stream()
            .map(row -> processRow(row, columnsToExport, sourceReplace))
            .collect(Collectors.toList());
    }

    /**
     * Обработка одной строки данных
     * Адаптирует логику из старой версии StatisticService.getRowList()
     */
    private List<String> processRow(List<String> originalRow, List<Integer> columnsToExport, boolean sourceReplace) {
        List<String> processedRow = new ArrayList<>();
        
        for (int columnIndex = 0; columnIndex < originalRow.size(); columnIndex++) {
            if (columnsToExport.contains(columnIndex)) {
                String value = getColumnValue(originalRow, columnIndex);
                
                if (value.isEmpty()) {
                    processedRow.add("");
                } else if (sourceReplace && columnIndex == 28) {
                    // Замена источника для клиента (логика из строк 143-144 старой версии)
                    if (!KNOWN_USERS.contains(value.toLowerCase())) {
                        processedRow.add("manager");
                    } else {
                        processedRow.add(value);
                    }
                } else {
                    processedRow.add(value);
                }
            }
        }
        
        return processedRow;
    }
    
    /**
     * Получение значения колонки с защитой от IndexOutOfBounds
     */
    private String getColumnValue(List<String> row, int columnIndex) {
        if (columnIndex >= 0 && columnIndex < row.size()) {
            String value = row.get(columnIndex);
            return value != null ? value : "";
        }
        return "";
    }

    /**
     * Генерация CSV файла
     */
    private byte[] generateCsvFile(List<List<String>> data, List<String> headers, StatsProcessDto dto) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, 
                 "UTF-8".equalsIgnoreCase(dto.getCsvEncoding()) ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1)) {
            
            String delimiter = dto.getCsvDelimiter();
            
            // Заголовки
            writer.write(String.join(delimiter, headers) + "\n");
            
            // Данные
            for (List<String> row : data) {
                List<String> escapedRow = row.stream()
                    .map(value -> escapeCSV(value, delimiter))
                    .collect(Collectors.toList());
                writer.write(String.join(delimiter, escapedRow) + "\n");
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
    private byte[] generateExcelFile(List<List<String>> data, List<String> headers) throws IOException {
        
        try (Workbook workbook = new XSSFWorkbook(); 
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Статистика");
            
            // Создание стилей через ExcelStyleFactory
            ExcelStyles styles = excelStyleFactory.createStyles(workbook);
            
            // Заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.getHeaderStyle());
            }
            
            // Данные
            int rowIndex = 1;
            for (List<String> rowData : data) {
                Row dataRow = sheet.createRow(rowIndex++);
                
                for (int i = 0; i < rowData.size(); i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(rowData.get(i));
                    // Используем стандартный стиль из ExcelStyleFactory
                }
            }
            
            // Автоподбор ширины колонок
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

}