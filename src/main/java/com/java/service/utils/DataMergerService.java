package com.java.service.utils;

import com.java.dto.utils.CollectedDataRow;
import com.java.dto.utils.MergedDataRow;
import com.java.dto.utils.SourceDataRow;
import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.service.exports.FileGeneratorService;
import com.java.service.file.FileAnalyzerService;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;
import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;
import java.io.Reader;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataMergerService {

    private final FileAnalyzerService fileAnalyzerService;
    private final FileReaderUtils fileReaderUtils;
    private final FileGeneratorService fileGeneratorService;

    public List<MergedDataRow> mergeData(MultipartFile sourceFile, MultipartFile collectedFile) throws IOException {
        return mergeData(sourceFile, collectedFile, Map.of());
    }

    public List<MergedDataRow> mergeData(MultipartFile sourceFile, MultipartFile collectedFile,
                                       Map<String, Integer> columnMapping) throws IOException {
        log.info("Начато объединение данных. Источник: {}, Собранные: {}",
                sourceFile.getOriginalFilename(), collectedFile.getOriginalFilename());

        // Анализируем и читаем исходные данные
        FileMetadata sourceMetadata = fileAnalyzerService.analyzeFile(sourceFile, "source");
        List<SourceDataRow> sourceData = parseSourceData(sourceMetadata, columnMapping);
        log.info("Загружено {} строк исходных данных", sourceData.size());

        // Анализируем и читаем собранные данные
        FileMetadata collectedMetadata = fileAnalyzerService.analyzeFile(collectedFile, "collected");
        List<CollectedDataRow> collectedData = parseCollectedData(collectedMetadata, columnMapping);
        log.info("Загружено {} строк собранных данных", collectedData.size());

        // Объединяем данные по модели аналога
        List<MergedDataRow> result = performMerge(sourceData, collectedData);

        log.info("Объединение завершено. Результат: {} строк", result.size());
        return result;
    }

    private List<SourceDataRow> parseSourceData(FileMetadata metadata) throws IOException {
        return parseSourceData(metadata, Map.of());
    }

    private List<SourceDataRow> parseSourceData(FileMetadata metadata, Map<String, Integer> columnMapping) throws IOException {
        List<List<String>> data = fileReaderUtils.readFullFileData(metadata);
        List<SourceDataRow> result = new ArrayList<>();

        // Получаем индексы колонок (по умолчанию или из маппинга)
        int idColumn = columnMapping.getOrDefault("sourceIdColumn", 0);
        int clientModelColumn = columnMapping.getOrDefault("sourceClientModelColumn", 1);
        int analogModelColumn = columnMapping.getOrDefault("sourceAnalogModelColumn", 2);
        int coefficientColumn = columnMapping.getOrDefault("sourceCoefficientColumn", 3);

        // Пропускаем заголовок (первая строка)
        for (int i = 1; i < data.size(); i++) {
            List<String> row = data.get(i);

            // Проверяем, что все нужные колонки существуют
            int maxIndex = Math.max(Math.max(idColumn, clientModelColumn),
                                  Math.max(analogModelColumn, coefficientColumn));

            if (row.size() > maxIndex) {
                SourceDataRow sourceRow = new SourceDataRow();
                sourceRow.setId(getColumnValue(row, idColumn));
                sourceRow.setClientModel(getColumnValue(row, clientModelColumn));
                sourceRow.setAnalogModel(getColumnValue(row, analogModelColumn));
                sourceRow.setCoefficient(getColumnValue(row, coefficientColumn));
                result.add(sourceRow);
            }
        }

        return result;
    }

    private List<CollectedDataRow> parseCollectedData(FileMetadata metadata) throws IOException {
        return parseCollectedData(metadata, Map.of());
    }

    private List<CollectedDataRow> parseCollectedData(FileMetadata metadata, Map<String, Integer> columnMapping) throws IOException {
        List<List<String>> data = fileReaderUtils.readFullFileData(metadata);
        List<CollectedDataRow> result = new ArrayList<>();

        // Получаем индексы колонок (по умолчанию или из маппинга)
        int analogModelColumn = columnMapping.getOrDefault("collectedAnalogModelColumn", 0);
        int linkColumn = columnMapping.getOrDefault("collectedLinkColumn", 1);

        // Пропускаем заголовок (первая строка)
        for (int i = 1; i < data.size(); i++) {
            List<String> row = data.get(i);

            // Проверяем, что все нужные колонки существуют
            int maxIndex = Math.max(analogModelColumn, linkColumn);

            if (row.size() > maxIndex) {
                CollectedDataRow collectedRow = new CollectedDataRow();
                collectedRow.setAnalogModel(getColumnValue(row, analogModelColumn));
                collectedRow.setLink(getColumnValue(row, linkColumn));
                result.add(collectedRow);
            }
        }

        return result;
    }

    private String getColumnValue(List<String> row, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return "";
        }
        String value = row.get(columnIndex);
        return value != null ? value.trim() : "";
    }

    private List<MergedDataRow> performMerge(List<SourceDataRow> sourceData, List<CollectedDataRow> collectedData) {
        List<MergedDataRow> result = new ArrayList<>();

        // Группируем собранные данные по модели аналога для быстрого поиска
        Map<String, List<CollectedDataRow>> collectedByModel = collectedData.stream()
                .collect(Collectors.groupingBy(CollectedDataRow::getAnalogModel));

        // Для каждой строки исходных данных ищем соответствующие ссылки
        for (SourceDataRow source : sourceData) {
            List<CollectedDataRow> matchingLinks = collectedByModel.get(source.getAnalogModel());

            if (matchingLinks != null && !matchingLinks.isEmpty()) {
                // Создаем отдельную строку для каждой ссылки (построчная выгрузка)
                for (CollectedDataRow collected : matchingLinks) {
                    MergedDataRow merged = new MergedDataRow();
                    merged.setId(source.getId());
                    merged.setClientModel(source.getClientModel());
                    merged.setAnalogModel(source.getAnalogModel());
                    merged.setCoefficient(source.getCoefficient());
                    merged.setLink(collected.getLink());
                    result.add(merged);
                }
            } else {
                // Если ссылок нет - добавляем строку с пустой ссылкой
                MergedDataRow merged = new MergedDataRow();
                merged.setId(source.getId());
                merged.setClientModel(source.getClientModel());
                merged.setAnalogModel(source.getAnalogModel());
                merged.setCoefficient(source.getCoefficient());
                merged.setLink("");
                result.add(merged);
            }
        }

        return result;
    }

    public Path mergeDataAndGenerateFile(MultipartFile sourceFile, MultipartFile collectedFile) throws IOException {
        return mergeDataAndGenerateFile(sourceFile, collectedFile, Map.of());
    }

    public Path mergeDataAndGenerateFile(MultipartFile sourceFile, MultipartFile collectedFile,
                                       Map<String, Integer> columnMapping) throws IOException {
        // Объединяем данные
        List<MergedDataRow> mergedData = mergeData(sourceFile, collectedFile, columnMapping);

        // Создаем ExportTemplate
        ExportTemplate template = createExportTemplate();

        // Генерируем файл
        String fileName = "data-merged_" + System.currentTimeMillis();
        Path resultFile = fileGeneratorService.generateFile(
                convertToMapStream(mergedData),
                mergedData.size(),
                template,
                fileName
        );

        log.info("Файл результата создан: {}", resultFile);
        return resultFile;
    }

    public List<String> analyzeFileHeaders(MultipartFile file) throws IOException {
        log.info("Анализ заголовков файла: {}", file.getOriginalFilename());

        // Анализируем файл через FileAnalyzerService
        FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "temp");

        // Читаем только заголовки напрямую из файла
        List<String> headers = readFileHeaders(metadata);

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой или заголовки не найдены");
        }

        log.info("Найдено {} заголовков: {}", headers.size(), headers);

        return headers;
    }

    private List<String> readFileHeaders(FileMetadata metadata) throws IOException {
        Path filePath = Path.of(metadata.getTempFilePath());

        if (!Files.exists(filePath)) {
            throw new IOException("Файл не найден: " + metadata.getTempFilePath());
        }

        // Определяем кодировку
        Charset charset = StandardCharsets.UTF_8;
        if (metadata.getDetectedEncoding() != null) {
            try {
                charset = Charset.forName(metadata.getDetectedEncoding());
            } catch (Exception e) {
                log.warn("Не удалось использовать кодировку {}, используем UTF-8", metadata.getDetectedEncoding());
            }
        }

        String fileType = metadata.getFileFormat().toLowerCase();

        if ("csv".equals(fileType)) {
            return readCsvHeaders(filePath, metadata, charset);
        } else if ("xlsx".equals(fileType) || "xls".equals(fileType)) {
            return readExcelHeaders(filePath);
        } else {
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " + fileType);
        }
    }

    private List<String> readCsvHeaders(Path filePath, FileMetadata metadata, Charset charset) throws IOException {
        char delimiter = metadata.getDetectedDelimiter() != null ? metadata.getDetectedDelimiter().charAt(0) : ',';
        char quoteChar = metadata.getDetectedQuoteChar() != null ? metadata.getDetectedQuoteChar().charAt(0) : '"';

        try (Reader reader = Files.newBufferedReader(filePath, charset);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder()
                    .withSeparator(delimiter)
                    .withQuoteChar(quoteChar)
                    .build())
                .build()) {

            String[] firstRow = csvReader.readNext();
            if (firstRow == null) {
                return new ArrayList<>();
            }

            List<String> headers = new ArrayList<>();

            for (int i = 0; i < firstRow.length; i++) {
                String value = firstRow[i] != null ? firstRow[i].trim() : "";

                // Если значение пустое или выглядит как данные, создаем автоматический заголовок
                if (value.isEmpty() || isDataValue(value)) {
                    value = "Колонка " + (char)('A' + i);
                }

                headers.add(value);
            }

            return headers;
        } catch (Exception e) {
            log.error("Ошибка чтения CSV заголовков из файла: {}", filePath, e);
            throw new IOException("Ошибка чтения CSV заголовков: " + e.getMessage(), e);
        }
    }

    private List<String> readExcelHeaders(Path filePath) throws IOException {
        try (InputStream fis = Files.newInputStream(filePath)) {
            Workbook workbook = WorkbookFactory.create(fis);

            // Ищем первый видимый лист (не скрытый)
            Sheet sheet = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet currentSheet = workbook.getSheetAt(i);
                if (!workbook.isSheetHidden(i) && !workbook.isSheetVeryHidden(i)) {
                    sheet = currentSheet;
                    break;
                }
            }

            if (sheet == null) {
                // Если все листы скрыты, берем первый
                sheet = workbook.getSheetAt(0);
                log.warn("Все листы в файле {} скрыты, используем первый лист: {}", filePath, sheet.getSheetName());
            } else {
                log.info("Используем видимый лист: {}", sheet.getSheetName());
            }

            if (sheet.getLastRowNum() < 0) {
                workbook.close();
                return new ArrayList<>();
            }

            // Найдем максимальное количество колонок, проверив больше строк для точности
            int maxColumns = 0;
            int rowsToCheck = Math.min(20, sheet.getLastRowNum() + 1); // Увеличиваем количество проверяемых строк

            for (int rowNum = 0; rowNum < rowsToCheck; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null) {
                    // Используем getLastCellNum() который возвращает индекс + 1 последней колонки
                    maxColumns = Math.max(maxColumns, row.getLastCellNum());

                    // Также проверяем физически заполненные ячейки
                    for (Cell cell : row) {
                        if (cell != null) {
                            maxColumns = Math.max(maxColumns, cell.getColumnIndex() + 1);
                        }
                    }
                }
            }

            List<String> headers = new ArrayList<>();
            Row firstRow = sheet.getRow(0);

            for (int i = 0; i < maxColumns; i++) {
                String value = "";

                // Пытаемся получить значение из первой строки
                if (firstRow != null) {
                    Cell cell = firstRow.getCell(i);
                    if (cell != null) {
                        switch (cell.getCellType()) {
                            case STRING:
                                value = cell.getStringCellValue().trim();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    value = cell.getDateCellValue().toString();
                                } else {
                                    // Проверяем, является ли число целым
                                    double numValue = cell.getNumericCellValue();
                                    if (numValue == (long) numValue) {
                                        value = String.valueOf((long) numValue);
                                    } else {
                                        value = String.valueOf(numValue);
                                    }
                                }
                                break;
                            case BOOLEAN:
                                value = String.valueOf(cell.getBooleanCellValue());
                                break;
                            case FORMULA:
                                try {
                                    // Пытаемся получить вычисленное значение формулы
                                    switch (cell.getCachedFormulaResultType()) {
                                        case STRING:
                                            value = cell.getStringCellValue().trim();
                                            break;
                                        case NUMERIC:
                                            double numVal = cell.getNumericCellValue();
                                            value = (numVal == (long) numVal) ? String.valueOf((long) numVal) : String.valueOf(numVal);
                                            break;
                                        default:
                                            value = "";
                                    }
                                } catch (Exception e) {
                                    value = "";
                                }
                                break;
                            case BLANK:
                            default:
                                value = "";
                                break;
                        }
                    }
                }

                // Если значение пустое или выглядит как данные, создаем автоматический заголовок
                if (value.isEmpty() || isDataValue(value)) {
                    value = "Колонка " + (char)('A' + i);
                }

                headers.add(value);
            }

            workbook.close();
            return headers;
        } catch (Exception e) {
            log.error("Ошибка чтения Excel заголовков из файла: {}", filePath, e);
            throw new IOException("Ошибка чтения Excel заголовков: " + e.getMessage(), e);
        }
    }

    private boolean isDataValue(String value) {
        // Проверяем, выглядит ли значение как данные, а не как заголовок
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        // Если это число
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            // Не число, продолжаем проверки
        }

        // Если содержит много цифр и мало букв (возможно ID или артикул)
        long digitCount = value.chars().filter(Character::isDigit).count();
        long letterCount = value.chars().filter(Character::isLetter).count();

        return digitCount > letterCount && digitCount > 3;
    }

    private ExportTemplate createExportTemplate() {
        ExportTemplate template = ExportTemplate.builder()
                .name("Data Merger Export")
                .description("Объединенные данные по модели аналогов")
                .fileFormat("CSV")
                .csvDelimiter(",")
                .csvEncoding("UTF-8")
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();

        List<ExportTemplateField> fields = new ArrayList<>();
        fields.add(createTemplateField(template, "id", "ID", 1));
        fields.add(createTemplateField(template, "clientModel", "Модель клиента", 2));
        fields.add(createTemplateField(template, "analogModel", "Модель аналога", 3));
        fields.add(createTemplateField(template, "coefficient", "Коэффициент", 4));
        fields.add(createTemplateField(template, "link", "Ссылка", 5));

        template.setFields(fields);
        return template;
    }

    private ExportTemplateField createTemplateField(ExportTemplate template, String entityFieldName, String exportColumnName, int order) {
        return ExportTemplateField.builder()
                .template(template)
                .entityFieldName(entityFieldName)
                .exportColumnName(exportColumnName)
                .fieldOrder(order)
                .isIncluded(true)
                .build();
    }

    private Stream<Map<String, Object>> convertToMapStream(List<MergedDataRow> data) {
        return data.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row.getId());
            map.put("clientModel", row.getClientModel());
            map.put("analogModel", row.getAnalogModel());
            map.put("coefficient", row.getCoefficient());
            map.put("link", row.getLink());
            return map;
        });
    }
}