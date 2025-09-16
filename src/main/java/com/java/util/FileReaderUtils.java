package com.java.util;

import com.java.model.entity.FileMetadata;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Утилитарный класс для чтения файлов в различных форматах
 * Централизует логику чтения CSV и Excel файлов
 */
@Component
@Slf4j
public class FileReaderUtils {

    /**
     * Читает полные данные из файла на основе метаданных
     */
    public List<List<String>> readFullFileData(FileMetadata metadata) throws IOException {
        Path filePath = Paths.get(metadata.getTempFilePath());

        if (!Files.exists(filePath)) {
            throw new IOException("Файл не найден: " + metadata.getTempFilePath());
        }

        String fileType = metadata.getFileFormat().toLowerCase();

        if ("csv".equals(fileType)) {
            return readCsvFile(filePath, metadata);
        } else if ("xlsx".equals(fileType) || "xls".equals(fileType)) {
            return readExcelFile(filePath);
        } else {
            throw new IllegalArgumentException("Неподдерживаемый тип файла: " + fileType);
        }
    }

    /**
     * Читает CSV файл полностью
     */
    private List<List<String>> readCsvFile(Path filePath, FileMetadata metadata) throws IOException {
        List<List<String>> data = new ArrayList<>();
        Charset charset = StandardCharsets.UTF_8;

        if (metadata.getDetectedEncoding() != null) {
            try {
                charset = Charset.forName(metadata.getDetectedEncoding());
            } catch (Exception e) {
                log.warn("Не удалось использовать кодировку {}, используем UTF-8", metadata.getDetectedEncoding());
            }
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(Files.newInputStream(filePath), charset))) {
            String[] row;
            boolean skipHeader = metadata.getHasHeader() != null && metadata.getHasHeader();

            if (skipHeader) {
                reader.readNext(); // Пропускаем заголовок
            }

            while ((row = reader.readNext()) != null) {
                data.add(Arrays.asList(row));
            }
        } catch (CsvValidationException e) {
            throw new IOException("Ошибка чтения CSV файла: " + e.getMessage(), e);
        }

        return data;
    }

    /**
     * Читает Excel файл полностью
     */
    private List<List<String>> readExcelFile(Path filePath) throws IOException {
        List<List<String>> data = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(Files.newInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0);

            boolean firstRow = true;
            for (Row row : sheet) {
                // Пропускаем первую строку (заголовок)
                if (firstRow) {
                    firstRow = false;
                    continue;
                }

                List<String> rowData = new ArrayList<>();
                int lastCellNum = row.getLastCellNum();

                for (int i = 0; i < lastCellNum; i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) {
                        rowData.add("");
                    } else {
                        rowData.add(getCellValue(cell));
                    }
                }

                data.add(rowData);
            }
        }

        return data;
    }

    /**
     * Получает значение ячейки Excel как строку
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}