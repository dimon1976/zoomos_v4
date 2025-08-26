package com.java.service.file;

import com.java.config.ImportConfig;
import com.java.model.entity.FileMetadata;
import com.java.util.PathResolver;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Сервис для анализа файлов и определения их метаданных
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileAnalyzerService {

    private final PathResolver pathResolver;
    private final ImportConfig.ImportSettings importSettings;

    private static final List<Character> COMMON_DELIMITERS = Arrays.asList(',', ';', '\t', '|');
    private static final List<Character> COMMON_QUOTES = Arrays.asList('"', '\'');

    // Константы для улучшения читаемости
    private static final int ENCODING_BUFFER_SIZE = 4096;
    private static final int CSV_SAMPLE_LINES = 10;
    private static final int HASH_BUFFER_SIZE = 8192;
    private static final char DEFAULT_ESCAPE_CHAR = CSVParser.NULL_CHARACTER;

    /**
     * Анализирует файл и извлекает метаданные, сохраняя его под указанным префиксом
     */
    public FileMetadata analyzeFile(MultipartFile file, String prefix) throws IOException {
        log.info("Начало анализа файла: {}", file.getOriginalFilename());

        // Сохраняем файл во временную директорию
        Path tempFile = pathResolver.saveToTempFile(file, prefix);

        try {
            return performFileAnalysis(tempFile, file.getOriginalFilename(), file.getSize());
        } catch (Exception e) {
            // Удаляем временный файл при ошибке
            pathResolver.deleteFile(tempFile);
            throw new IOException("Ошибка анализа файла: " + e.getMessage(), e);
        }
    }

    /**
     * Анализирует файл и извлекает метаданные (используется префикс по умолчанию)
     */
    public FileMetadata analyzeFile(MultipartFile file) throws IOException {
        return analyzeFile(file, "analyze");
    }

    /**
     * Анализирует уже сохраненный файл по указанному пути
     *
     * @param filePath         путь к файлу
     * @param originalFilename исходное имя файла для отображения
     */
    public FileMetadata analyzeFile(Path filePath, String originalFilename) throws IOException {
        log.info("Начало анализа файла по пути: {}", filePath);

        try {
            long fileSize = pathResolver.getFileSize(filePath);
            return performFileAnalysis(filePath, originalFilename, fileSize);
        } catch (Exception e) {
            throw new IOException("Ошибка анализа файла: " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет основной анализ файла
     */
    private FileMetadata performFileAnalysis(Path filePath, String originalFilename, long fileSize)
            throws IOException {
        FileMetadata metadata = new FileMetadata();
        metadata.setOriginalFilename(originalFilename);
        metadata.setFileSize(fileSize);
        metadata.setTempFilePath(filePath.toString());

        String fileFormat = detectFileFormat(originalFilename);
        metadata.setFileFormat(fileFormat);
        metadata.setFileHash(calculateFileHash(filePath));

        if ("CSV".equalsIgnoreCase(fileFormat) || "TXT".equalsIgnoreCase(fileFormat)) {
            analyzeCsvFile(filePath, metadata);
        } else if ("XLSX".equalsIgnoreCase(fileFormat) || "XLS".equalsIgnoreCase(fileFormat)) {
            analyzeExcelFile(filePath, metadata);
        } else {
            throw new UnsupportedOperationException("Неподдерживаемый формат файла: " + fileFormat);
        }

        log.info("Анализ файла завершен. Кодировка: {}, Разделитель: {}, Колонок: {}",
                metadata.getDetectedEncoding(),
                metadata.getDetectedDelimiter(),
                metadata.getTotalColumns());

        return metadata;
    }

    /**
     * Анализирует CSV файл
     */
    private void analyzeCsvFile(Path filePath, FileMetadata metadata) throws IOException {
        // Определяем кодировку
        String encoding = detectEncoding(filePath);
        metadata.setDetectedEncoding(encoding);

        // Определяем разделитель и кавычки
        CsvFormat format = detectCsvFormat(filePath, encoding);
        metadata.setDetectedDelimiter(String.valueOf(format.delimiter));
        metadata.setDetectedQuoteChar(String.valueOf(format.quoteChar));
        // Сохраняем escape символ, если он не NULL_CHARACTER
        if (format.escapeChar != CSVParser.NULL_CHARACTER) {
            metadata.setDetectedEscapeChar(String.valueOf(format.escapeChar));
        } else {
            metadata.setDetectedEscapeChar(null); // NULL означает отсутствие escape символа
        }

        // Читаем заголовки и примеры данных
        try (Reader reader = Files.newBufferedReader(filePath, Charset.forName(encoding))) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(format.delimiter)
                    .withQuoteChar(format.quoteChar)
                    .withEscapeChar(format.escapeChar)
                    .build();

            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .build();

            // Читаем первую строку - предполагаем что это заголовки
            String[] headers = csvReader.readNext();
            if (headers != null) {
                metadata.setHasHeader(true);
                metadata.setTotalColumns(headers.length);
                metadata.setColumnHeaders(convertToJson(Arrays.asList(headers)));

                // Читаем примеры данных
                List<String[]> sampleData = new ArrayList<>();
                String[] row;
                int count = 0;
                while ((row = csvReader.readNext()) != null && count < importSettings.getSampleRows()) {
                    sampleData.add(row);
                    count++;
                }

                if (!sampleData.isEmpty()) {
                    metadata.setSampleData(convertToJson(sampleData));
                }
            } else {
                metadata.setHasHeader(false);
                metadata.setTotalColumns(0);
            }
        } catch (CsvValidationException e) {
            log.error("Ошибка валидации CSV", e);
            throw new IOException("Ошибка чтения CSV файла: " + e.getMessage(), e);
        }
    }

    /**
     * Анализирует Excel файл
     */
    private void analyzeExcelFile(Path filePath, FileMetadata metadata) throws IOException {
        metadata.setDetectedEncoding("UTF-8"); // Excel внутренне использует UTF-8

        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    metadata.setHasHeader(true);

                    // Читаем заголовки
                    List<String> headers = new ArrayList<>();
                    int totalColumns = 0;
                    for (Cell cell : headerRow) {
                        headers.add(getCellValueAsString(cell));
                        totalColumns = Math.max(totalColumns, cell.getColumnIndex() + 1);
                    }
                    metadata.setTotalColumns(totalColumns);
                    metadata.setColumnHeaders(convertToJson(headers));

                    // Читаем примеры данных
                    List<List<String>> sampleData = new ArrayList<>();
                    int rowCount = Math.min(sheet.getLastRowNum(), importSettings.getSampleRows());

                    for (int i = 1; i <= rowCount; i++) {
                        Row row = sheet.getRow(i);
                        if (row != null) {
                            List<String> rowData = new ArrayList<>();
                            for (int j = 0; j < totalColumns; j++) {
                                Cell cell = row.getCell(j);
                                rowData.add(cell != null ? getCellValueAsString(cell) : "");
                            }
                            sampleData.add(rowData);
                        }
                    }

                    if (!sampleData.isEmpty()) {
                        metadata.setSampleData(convertToJson(sampleData));
                    }
                } else {
                    metadata.setHasHeader(false);
                    metadata.setTotalColumns(0);
                }
            }
        }
    }

    /**
     * Определяет кодировку файла
     */
    private String detectEncoding(Path filePath) throws IOException {
        byte[] buf = new byte[ENCODING_BUFFER_SIZE];

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            UniversalDetector detector = new UniversalDetector(null);

            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();

            String encoding = detector.getDetectedCharset();
            detector.reset();

            if (encoding == null) {
                log.warn("Не удалось определить кодировку, используем UTF-8");
                encoding = StandardCharsets.UTF_8.name();
            }

            return encoding;
        }
    }

    /**
     * Определяет формат CSV файла (разделитель, кавычки)
     */
    private CsvFormat detectCsvFormat(Path filePath, String encoding) throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(encoding))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < CSV_SAMPLE_LINES) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                    count++;
                }
            }
        }

        if (lines.isEmpty()) {
            // Возвращаем дефолтные значения для пустого файла
            return new CsvFormat(',', '"', DEFAULT_ESCAPE_CHAR);
        }

        // Объединяем первые строки для анализа
        String sampleContent = String.join("\n", lines);

        // Определяем символ кавычек
        char detectedQuote = detectQuoteChar(sampleContent);

        // Определяем разделитель через подсчет символов
        char detectedDelimiter = detectDelimiterByCount(sampleContent, detectedQuote);

        // Дополнительная проверка через парсинг для подтверждения
        char confirmedDelimiter = confirmDelimiterByParsing(lines, detectedDelimiter, detectedQuote);

        return new CsvFormat(confirmedDelimiter, detectedQuote, DEFAULT_ESCAPE_CHAR);
    }

    /**
     * Определяет разделитель по количеству вхождений
     */
    private char detectDelimiterByCount(String content, char quoteChar) {
        Map<Character, Integer> delimiterCounts = new HashMap<>();

        // Удаляем содержимое в кавычках для более точного подсчета
        String contentWithoutQuoted = removeQuotedContent(content, quoteChar);

        for (char delimiter : COMMON_DELIMITERS) {
            int count = countOccurrences(contentWithoutQuoted, delimiter);
            if (count > 0) {
                delimiterCounts.put(delimiter, count);
            }
        }

        return delimiterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    /**
     * Подтверждает разделитель через парсинг CSV
     */
    private char confirmDelimiterByParsing(List<String> lines, char candidateDelimiter, char quoteChar) {
        // Проверяем кандидата
        if (isValidDelimiter(lines, candidateDelimiter, quoteChar)) {
            return candidateDelimiter;
        }

        // Если кандидат не подошел, проверяем остальные
        for (char delimiter : COMMON_DELIMITERS) {
            if (delimiter != candidateDelimiter && isValidDelimiter(lines, delimiter, quoteChar)) {
                return delimiter;
            }
        }

        // Возвращаем кандидата как fallback
        return candidateDelimiter;
    }

    /**
     * Определяет символ кавычек по частоте использования
     */
    private char detectQuoteChar(String content) {
        Map<Character, Integer> quoteCounts = new HashMap<>();

        for (char quote : COMMON_QUOTES) {
            int count = countOccurrences(content, quote);
            if (count > 0) {
                quoteCounts.put(quote, count);
            }
        }

        return quoteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse('"');
    }

    /**
     * Проверяет валидность разделителя
     */
    private boolean isValidDelimiter(List<String> lines, char delimiter, char quoteChar) {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar(quoteChar)
                .withEscapeChar(DEFAULT_ESCAPE_CHAR)
                .build();

        Integer expectedColumns = null;

        for (String line : lines) {
            try {
                String[] tokens = parser.parseLine(line);
                if (expectedColumns == null) {
                    expectedColumns = tokens.length;
                } else if (tokens.length != expectedColumns) {
                    // Количество колонок не совпадает
                    return false;
                }
            } catch (Exception e) {
                // Ошибка парсинга
                return false;
            }
        }

        // Проверяем, что есть хотя бы 2 колонки
        return expectedColumns != null && expectedColumns > 1;
    }

    /**
     * Подсчитывает количество вхождений символа в строку
     */
    private int countOccurrences(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    /**
     * Удаляет содержимое в кавычках для более точного подсчета разделителей
     */
    private String removeQuotedContent(String content, char quoteChar) {
        StringBuilder result = new StringBuilder();
        boolean inQuotes = false;

        for (char c : content.toCharArray()) {
            if (c == quoteChar) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                result.append(c);
            }
        }

        return result.toString();
    }


    /**
     * Определяет формат файла по расширению
     */
    private String detectFileFormat(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "UNKNOWN";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "UNKNOWN";
        }

        String extension = filename.substring(lastDotIndex + 1).toUpperCase();
        switch (extension) {
            case "CSV":
                return "CSV";
            case "XLSX":
                return "XLSX";
            case "XLS":
                return "XLS";
            case "TXT":
                return "TXT";
            default:
                return extension;
        }
    }

    /**
     * Вычисляет MD5 хеш файла
     */
    private String calculateFileHash(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, md)) {

                byte[] buffer = new byte[HASH_BUFFER_SIZE];
                while (dis.read(buffer) != -1) {
                    // Читаем файл для вычисления хеша
                }
            }

            byte[] digest = md.digest();
            return bytesToHex(digest);

        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 алгоритм не найден", e);
            throw new IOException("Ошибка инициализации алгоритма хеширования", e);
        }
    }

    /**
     * Конвертирует байты в hex строку
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Получает значение ячейки Excel как строку
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Убираем .0 для целых чисел
                double numericValue = cell.getNumericCellValue();
                if (numericValue == (long) numericValue) {
                    return String.valueOf((long) numericValue);
                }
                return String.valueOf(numericValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    // Пытаемся вычислить формулу
                    Workbook wb = cell.getSheet().getWorkbook();
                    FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);

                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
                            return String.valueOf(cellValue.getNumberValue());
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                        default:
                            return cell.getCellFormula();
                    }
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Конвертирует объект в JSON строку
     */
    private String convertToJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Ошибка конвертации в JSON", e);
            return "[]";
        }
    }

    /**
     * Внутренний класс для хранения формата CSV
     */
    private static class CsvFormat {
        final char delimiter;
        final char quoteChar;
        final char escapeChar;

        CsvFormat(char delimiter, char quoteChar, char escapeChar) {
            this.delimiter = delimiter;
            this.quoteChar = quoteChar;
            this.escapeChar = escapeChar;
        }
    }
}