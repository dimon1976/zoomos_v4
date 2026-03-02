package com.java.util;

import com.java.model.entity.FileMetadata;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import javax.xml.parsers.SAXParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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

        IOUtils.setByteArrayMaxOverride(Integer.MAX_VALUE);
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
     * SAX streaming чтение XLSX — только нужные колонки.
     * Не загружает весь workbook в память. Пропускает строку-заголовок.
     * @param filePath путь к файлу xlsx
     * @param columnIndices 0-based индексы колонок; порядок сохраняется в результате
     */
    public List<List<String>> readExcelColumnar(Path filePath, List<Integer> columnIndices)
            throws IOException {

        int maxColIdx = columnIndices.stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<String>> result = new ArrayList<>();

        try (OPCPackage pkg = OPCPackage.open(filePath.toFile(), PackageAccess.READ)) {
            XSSFReader xssfReader = new XSSFReader(pkg);
            StylesTable styles = xssfReader.getStylesTable();
            org.apache.poi.xssf.model.SharedStringsTable sst =
                    (org.apache.poi.xssf.model.SharedStringsTable) xssfReader.getSharedStringsTable();

            SheetContentsHandler handler = new SheetContentsHandler() {
                boolean headerSkipped = false;
                String[] currentRow;

                @Override
                public void startRow(int rowNum) {
                    if (headerSkipped) {
                        currentRow = new String[columnIndices.size()];
                        Arrays.fill(currentRow, "");
                    }
                }

                @Override
                public void cell(String cellRef, String formattedValue, XSSFComment comment) {
                    if (currentRow == null) return;
                    int colIdx = cellRefToColIndex(cellRef);
                    if (colIdx > maxColIdx) return;
                    int pos = columnIndices.indexOf(colIdx);
                    if (pos >= 0) {
                        currentRow[pos] = formattedValue != null ? formattedValue : "";
                    }
                }

                @Override
                public void endRow(int rowNum) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        return;
                    }
                    if (currentRow != null) {
                        result.add(Arrays.asList(currentRow));
                        currentRow = null;
                    }
                }

                @Override
                public void headerFooter(String text, boolean isHeader, String tagName) {}
            };

            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            XMLReader xmlReader = saxFactory.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false));
            Iterator<InputStream> sheets = xssfReader.getSheetsData();
            if (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    xmlReader.parse(new InputSource(sheet));
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Ошибка SAX-чтения XLSX: " + e.getMessage(), e);
        }
        return result;
    }

    /** Ссылка на ячейку (A1, BC12) → 0-based индекс колонки */
    private static int cellRefToColIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (!Character.isLetter(c)) break;
            col = col * 26 + (c - 'A' + 1);
        }
        return col - 1;
    }

    /**
     * Получает значение ячейки Excel как строку
     */
    public String getCellValue(Cell cell) {
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
