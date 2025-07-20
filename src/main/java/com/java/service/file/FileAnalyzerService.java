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

    /**
     * Анализирует файл и извлекает метаданные
     */
    public FileMetadata analyzeFile(MultipartFile file) throws IOException {
        log.info("Начало анализа файла: {}", file.getOriginalFilename());

        // Сохраняем файл во временную директорию
        Path tempFile = pathResolver.saveToTempFile(file, "analyze");

        try {
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(file.getOriginalFilename());
            metadata.setFileSize(file.getSize());
            metadata.setTempFilePath(tempFile.toString());

            // Определяем формат файла
            String fileFormat = detectFileFormat(file.getOriginalFilename());
            metadata.setFileFormat(fileFormat);

            // Вычисляем хеш файла
            metadata.setFileHash(calculateFileHash(tempFile));

            // Анализируем в зависимости от формата
            if ("CSV".equalsIgnoreCase(fileFormat) || "TXT".equalsIgnoreCase(fileFormat)) {
                analyzeCsvFile(tempFile, metadata);
            } else if ("XLSX".equalsIgnoreCase(fileFormat) || "XLS".equalsIgnoreCase(fileFormat)) {
                analyzeExcelFile(tempFile, metadata);
            } else {
                throw new UnsupportedOperationException("Неподдерживаемый формат файла: " + fileFormat);
            }

            log.info("Анализ файла завершен. Кодировка: {}, Разделитель: {}, Колонок: {}",
                    metadata.getDetectedEncoding(),
                    metadata.getDetectedDelimiter(),
                    metadata.getTotalColumns());

            return metadata;

        } catch (Exception e) {
            // Удаляем временный файл при ошибке
            pathResolver.deleteFile(tempFile);
            throw new IOException("Ошибка анализа файла: " + e.getMessage(), e);
        }
    }

    /**
     * Анализирует уже сохраненный файл по указанному пути
     */
    public FileMetadata analyzeFile(Path filePath) throws IOException {
        return analyzeFile(filePath, filePath.getFileName().toString());
    }

    /**
     * Анализирует уже сохраненный файл по указанному пути
     *
     * @param filePath путь к файлу
     * @param originalFilename исходное имя файла для отображения
     */
    public FileMetadata analyzeFile(Path filePath, String originalFilename) throws IOException {
        log.info("Начало анализа файла по пути: {}", filePath);

        try {
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(originalFilename);
            metadata.setFileSize(pathResolver.getFileSize(filePath));
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

        } catch (Exception e) {
            throw new IOException("Ошибка анализа файла: " + e.getMessage(), e);
        }
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
        metadata.setDetectedEscapeChar(String.valueOf(format.escapeChar));

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
                metadata.setSampleData(convertToJson(sampleData));
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Анализирует Excel файл
     */
    private void analyzeExcelFile(Path filePath, FileMetadata metadata) throws IOException {
        metadata.setDetectedEncoding("UTF-8"); // Excel внутренне использует UTF-8

        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() > 0) {
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
                    metadata.setSampleData(convertToJson(sampleData));
                }
            }
        }
    }

    /**
     * Определяет кодировку файла
     */
    private String detectEncoding(Path filePath) throws IOException {
        byte[] buf = new byte[4096];

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
        Map<Character, Integer> delimiterCounts = new HashMap<>();
        Character detectedQuote = '"';

        // Читаем первые несколько строк для анализа
        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(encoding))) {
            List<String> lines = new ArrayList<>();
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
                lines.add(line);
                count++;
            }

            // Подсчитываем частоту разделителей
            for (Character delimiter : COMMON_DELIMITERS) {
                int totalCount = 0;
                boolean consistent = true;
                Integer previousCount = null;

                for (String l : lines) {
                    int delimiterCount = countOccurrences(l, delimiter);
                    totalCount += delimiterCount;

                    if (previousCount != null && previousCount != delimiterCount) {
                        consistent = false;
                    }
                    previousCount = delimiterCount;
                }

                if (consistent && totalCount > 0) {
                    delimiterCounts.put(delimiter, totalCount);
                }
            }

            // Выбираем разделитель с наибольшей частотой
            Character detectedDelimiter = delimiterCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(',');

            // Определяем символ кавычек
            for (String l : lines) {
                for (Character quote : COMMON_QUOTES) {
                    if (l.contains(String.valueOf(quote))) {
                        detectedQuote = quote;
                        break;
                    }
                }
            }

            return new CsvFormat(detectedDelimiter, detectedQuote, '\\');
        }
    }

    /**
     * Определяет формат файла по расширению
     */
    private String detectFileFormat(String filename) {
        if (filename == null) return "UNKNOWN";

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toUpperCase();
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

                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Читаем файл для вычисления хеша
                }
            }

            byte[] digest = md.digest();
            return bytesToHex(digest);

        } catch (Exception e) {
            log.error("Ошибка вычисления хеша файла", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Конвертирует байты в hex строку
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Подсчитывает количество вхождений символа в строку
     */
    private int countOccurrences(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    /**
     * Получает значение ячейки Excel как строку
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
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