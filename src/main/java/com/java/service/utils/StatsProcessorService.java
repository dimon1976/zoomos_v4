package com.java.service.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.utils.StatsProcessDto;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сервис для обработки файлов статистики
 * Адаптирует логику из старой версии StatisticService для новой архитектуры
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsProcessorService {

    private final FileReaderUtils fileReaderUtils;
    private final FileGeneratorService fileGeneratorService;
    private final ObjectMapper objectMapper;
    
    // Индексы колонок для дополнительных данных
    private static final int COMPETITOR_URL_COLUMN_INDEX = 27;
    private static final int SOURCE_COLUMN_INDEX = 28;
    private static final int DATE_ADD_COLUMN_INDEX = 29;
    
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

        try {
            // Читаем ВСЕ данные из файла через централизованный утилитарный класс
            List<List<String>> data = fileReaderUtils.readFullFileData(metadata);

            if (data.isEmpty()) {
                throw new IllegalArgumentException("Файл не содержит данных для обработки");
            }

            log.info("Загружено {} строк для обработки", data.size());

            // Определяем колонки для экспорта
            List<Integer> columnsToExport = getColumnsToExport(dto);

            // Обрабатываем данные
            List<List<String>> processedData = processData(data, columnsToExport, dto.isSourceReplace());

            log.info("Обработано {} строк", processedData.size());

            // Создаем ExportTemplate
            ExportTemplate template = createExportTemplate(dto);

            // Преобразуем данные в Stream<Map<String, Object>>
            Stream<Map<String, Object>> dataStream = convertToMapStream(processedData, getHeaders(dto));

            // Используем FileGeneratorService для генерации файла
            String fileName = "stats-processed_" + System.currentTimeMillis();
            java.nio.file.Path generatedFile = fileGeneratorService.generateFile(
                    dataStream,
                    processedData.size(),
                    template,
                    fileName
            );

            // Читаем сгенерированный файл в массив байт
            byte[] fileData = java.nio.file.Files.readAllBytes(generatedFile);

            // Удаляем временный файл сразу после чтения
            try {
                java.nio.file.Files.delete(generatedFile);
                log.debug("Удалён временный файл: {}", generatedFile);
            } catch (IOException deleteEx) {
                log.warn("Не удалось удалить временный файл: {}", generatedFile, deleteEx);
            }

            return fileData;

        } catch (Exception e) {
            log.error("Ошибка при обработке файла статистики: {}", metadata.getOriginalFilename(), e);
            throw new IOException("Ошибка обработки файла: " + e.getMessage(), e);
        }
    }

    /**
     * Создание ExportTemplate на основе настроек из DTO
     */
    private ExportTemplate createExportTemplate(StatsProcessDto dto) {
        ExportTemplate template = ExportTemplate.builder()
                .name("Stats Export")
                .description("Экспорт обработанной статистики")
                .fileFormat("csv".equalsIgnoreCase(dto.getOutputFormat()) ? "CSV" : "XLSX")
                .csvDelimiter(dto.getCsvDelimiter())
                .csvEncoding(dto.getCsvEncoding())
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();

        // Создаем поля на основе заголовков
        List<String> headers = getHeaders(dto);
        List<ExportTemplateField> fields = new ArrayList<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            fields.add(ExportTemplateField.builder()
                    .template(template)
                    .entityFieldName(header)
                    .exportColumnName(header)
                    .fieldOrder(i + 1)
                    .isIncluded(true)
                    .build());
        }

        template.setFields(fields);
        return template;
    }

    /**
     * Преобразование данных в Stream<Map<String, Object>> для FileGeneratorService
     */
    private Stream<Map<String, Object>> convertToMapStream(List<List<String>> data, List<String> headers) {
        return data.stream().map(row -> {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < headers.size() && i < row.size(); i++) {
                map.put(headers.get(i), row.get(i));
            }
            return map;
        });
    }

    /**
     * Определение колонок для экспорта на основе настроек DTO
     * Логика из старой версии StatisticService.getColumnList()
     */
    private List<Integer> getColumnsToExport(StatsProcessDto dto) {
        List<Integer> columns = new ArrayList<>(BASE_COLUMNS);
        
        // Добавляем дополнительные колонки по аналогии со старой версией (строки 100-110)
        if (dto.isShowCompetitorUrl()) {
            columns.add(COMPETITOR_URL_COLUMN_INDEX); // URL конкурента
        }
        if (dto.isShowSource()) {
            columns.add(SOURCE_COLUMN_INDEX); // Кто добавил
        }
        if (dto.isShowDateAdd()) {
            columns.add(DATE_ADD_COLUMN_INDEX); // Дата добавления
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
            headers.add("Дата добавления");
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
                } else if (sourceReplace && columnIndex == SOURCE_COLUMN_INDEX && columnIndex < originalRow.size()) {
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


}