package com.java.service.statistics;

import com.java.dto.StatisticsComparisonDto;
import com.java.dto.StatisticsHistoryDto;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис для экспорта статистики в Excel формат
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsExcelExportService {

    private final PathResolver pathResolver;
    private final HistoricalStatisticsService historicalStatisticsService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Генерирует Excel файл со статистикой сравнения и общими трендами
     *
     * @param comparison данные сравнения статистики
     * @param clientName название клиента
     * @param templateId ID шаблона экспорта (для получения исторических данных)
     * @param filterFieldName название поля фильтрации (может быть null)
     * @param filterFieldValue значение фильтра (может быть null)
     * @return путь к сгенерированному файлу
     */
    public Path generateExcel(List<StatisticsComparisonDto> comparison,
                              String clientName,
                              Long templateId,
                              String filterFieldName,
                              String filterFieldValue) throws IOException {

        if (comparison == null || comparison.isEmpty()) {
            throw new IllegalArgumentException("Нет данных для экспорта");
        }

        log.info("Starting statistics Excel export. Client: {}, Groups: {}, TemplateId: {}",
                clientName, comparison.size(), templateId);

        // Создаём временный файл
        Path tempFile = pathResolver.createTempFile("statistics_export_", ".xlsx");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); // window size = 100
             FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {

            workbook.setCompressTempFiles(true);

            // Создаём стили
            ExcelStyles styles = createStyles(workbook);

            // Лист 1: Основная таблица статистики
            Sheet statisticsSheet = workbook.createSheet("Статистика");
            writeStatisticsData(statisticsSheet, comparison, styles);
            autoSizeColumns(statisticsSheet, comparison);

            // Лист 2: Тренды (общие по всем операциям)
            Sheet trendsSheet = workbook.createSheet("Тренды");
            writeTrendsSheet(trendsSheet, comparison, templateId, filterFieldName, filterFieldValue, styles);

            // Сохраняем файл
            workbook.write(fos);
            workbook.dispose();

            log.info("Statistics Excel export completed with trends. Path: {}", tempFile);
            return tempFile;

        } catch (IOException e) {
            log.error("Error generating statistics Excel", e);
            throw e;
        }
    }

    /**
     * Записывает данные статистики в лист
     */
    private void writeStatisticsData(Sheet sheet, List<StatisticsComparisonDto> comparison, ExcelStyles styles) {

        int currentRow = 0;

        // Определяем количество операций из первой группы
        int operationsCount = comparison.get(0).getOperations().size();

        // Создаём заголовки
        currentRow = writeHeaders(sheet, comparison.get(0), styles, currentRow);

        // Записываем данные по каждой группе
        for (StatisticsComparisonDto group : comparison) {
            currentRow = writeGroupData(sheet, group, styles, currentRow, operationsCount);
        }

        // Применяем автофильтр
        if (currentRow > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, currentRow - 1, 0, 1 + operationsCount));
        }
    }

    /**
     * Создаёт строку заголовков
     */
    private int writeHeaders(Sheet sheet, StatisticsComparisonDto firstGroup, ExcelStyles styles, int startRow) {

        Row headerRow1 = sheet.createRow(startRow);
        Row headerRow2 = sheet.createRow(startRow + 1);

        // Заголовки фиксированных колонок
        createCell(headerRow1, 0, "Группа", styles.headerStyle);
        createCell(headerRow2, 0, "", styles.headerStyle);

        createCell(headerRow1, 1, "Метрика", styles.headerStyle);
        createCell(headerRow2, 1, "", styles.headerStyle);

        // Объединяем ячейки для фиксированных колонок
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow + 1, 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow + 1, 1, 1));

        // Заголовки для каждой операции
        int col = 2;
        for (StatisticsComparisonDto.OperationStatistics operation : firstGroup.getOperations()) {
            String operationHeader = "Операция #" + operation.getOperationId();
            String dateHeader = formatDate(operation.getExportDate());
            String nameHeader = operation.getOperationName() != null ? operation.getOperationName() : "";

            createCell(headerRow1, col, operationHeader + " (" + dateHeader + ")", styles.headerStyle);
            createCell(headerRow2, col, nameHeader, styles.headerSubStyle);
            col++;
        }

        // Фиксируем первые две строки и две колонки
        sheet.createFreezePane(2, 2);

        return startRow + 2;
    }

    /**
     * Записывает данные одной группы
     */
    private int writeGroupData(Sheet sheet, StatisticsComparisonDto group,
                               ExcelStyles styles, int startRow, int operationsCount) {

        // Получаем все метрики из первой операции (предполагаем что метрики одинаковые для всех операций)
        if (group.getOperations().isEmpty()) {
            return startRow;
        }

        Map<String, StatisticsComparisonDto.MetricValue> metricsMap = group.getOperations().get(0).getMetrics();
        if (metricsMap == null || metricsMap.isEmpty()) {
            return startRow;
        }

        int rowsCount = metricsMap.size();
        int currentRow = startRow;

        // Особая обработка для группы "ОБЩЕЕ КОЛИЧЕСТВО"
        boolean isTotalSummary = "ОБЩЕЕ КОЛИЧЕСТВО".equals(group.getGroupFieldValue());

        // Для каждой метрики создаём строку
        for (Map.Entry<String, StatisticsComparisonDto.MetricValue> metricEntry : metricsMap.entrySet()) {
            Row row = sheet.createRow(currentRow);

            // Название группы (только в первой строке)
            if (currentRow == startRow) {
                Cell groupCell = createCell(row, 0, group.getGroupFieldValue(),
                        isTotalSummary ? styles.totalGroupStyle : styles.groupStyle);

                // Объединяем ячейки группы
                if (rowsCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(currentRow, currentRow + rowsCount - 1, 0, 0));
                }
            }

            // Название метрики
            createCell(row, 1, metricEntry.getKey(),
                    isTotalSummary ? styles.totalMetricStyle : styles.metricStyle);

            // Значения метрики для каждой операции
            int col = 2;
            for (StatisticsComparisonDto.OperationStatistics operation : group.getOperations()) {
                StatisticsComparisonDto.MetricValue metricValue = operation.getMetrics().get(metricEntry.getKey());

                if (metricValue != null) {
                    // Определяем стиль ячейки на основе alertLevel и changeType
                    CellStyle cellStyle = determineCellStyle(metricValue, isTotalSummary, styles);

                    // Формируем значение ячейки
                    String cellValue = formatMetricValue(metricValue);
                    createCell(row, col, cellValue, cellStyle);
                } else {
                    createCell(row, col, "-", styles.normalStyle);
                }

                col++;
            }

            currentRow++;
        }

        return currentRow;
    }

    /**
     * Форматирует значение метрики для отображения в ячейке
     */
    private String formatMetricValue(StatisticsComparisonDto.MetricValue metricValue) {
        StringBuilder sb = new StringBuilder();

        // Текущее значение
        sb.append(metricValue.getCurrentValue());

        // Процент от общего (если есть фильтр)
        if (metricValue.getPercentageOfTotal() != null) {
            sb.append(String.format("\n(%.1f%% от %d)",
                    metricValue.getPercentageOfTotal(),
                    metricValue.getTotalValue()));
        }

        // Изменение (если есть предыдущее значение)
        if (metricValue.getPreviousValue() != null) {
            String arrow = switch (metricValue.getChangeType()) {
                case UP -> "↑";
                case DOWN -> "↓";
                case STABLE -> "−";
            };
            sb.append(String.format("\n%s %.1f%%", arrow, Math.abs(metricValue.getChangePercentage())));
        }

        return sb.toString();
    }

    /**
     * Определяет стиль ячейки на основе метрики
     */
    private CellStyle determineCellStyle(StatisticsComparisonDto.MetricValue metricValue,
                                        boolean isTotalSummary, ExcelStyles styles) {

        // Для итоговой строки используем специальный стиль
        if (isTotalSummary) {
            return styles.totalValueStyle;
        }

        // Если нет изменений или нет предыдущего значения
        if (metricValue.getPreviousValue() == null ||
            metricValue.getChangeType() == StatisticsComparisonDto.ChangeType.STABLE) {
            return styles.normalStyle;
        }

        // Определяем стиль на основе changeType и alertLevel
        if (metricValue.getChangeType() == StatisticsComparisonDto.ChangeType.UP) {
            return switch (metricValue.getAlertLevel()) {
                case WARNING -> styles.increaseWarningStyle;
                case CRITICAL -> styles.increaseCriticalStyle;
                default -> styles.normalStyle;
            };
        } else { // DOWN
            return switch (metricValue.getAlertLevel()) {
                case WARNING -> styles.decreaseWarningStyle;
                case CRITICAL -> styles.decreaseCriticalStyle;
                default -> styles.normalStyle;
            };
        }
    }

    /**
     * Создаёт ячейку с указанным значением и стилем
     */
    private Cell createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    /**
     * Форматирует дату
     */
    private String formatDate(ZonedDateTime date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    /**
     * Устанавливает ширину колонок
     */
    private void autoSizeColumns(Sheet sheet, List<StatisticsComparisonDto> comparison) {
        if (comparison.isEmpty()) {
            return;
        }

        // Количество колонок = 2 (группа + метрика) + количество операций
        int operationsCount = comparison.get(0).getOperations().size();
        int totalColumns = 2 + operationsCount;

        for (int col = 0; col < totalColumns; col++) {
            if (col == 0) {
                sheet.setColumnWidth(col, 30 * 256); // ~30 символов для группы
            } else if (col == 1) {
                sheet.setColumnWidth(col, 25 * 256); // ~25 символов для метрики
            } else {
                // Фиксированная ширина для колонок операций (20 символов)
                // Достаточно для "12345\n(50.0% от 10000)\n↑ 25.5%"
                sheet.setColumnWidth(col, 20 * 256);
            }
        }
    }

    /**
     * Записывает лист "Тренды" с общей информацией о трендах для каждой группы/метрики
     * Использует анализ всех исторических данных через HistoricalStatisticsService
     *
     * @param sheet лист Excel для записи
     * @param comparison данные сравнения статистики
     * @param templateId ID шаблона (для получения исторических данных)
     * @param filterFieldName название поля фильтрации (может быть null)
     * @param filterFieldValue значение фильтра (может быть null)
     * @param styles стили Excel
     */
    private void writeTrendsSheet(Sheet sheet,
                                  List<StatisticsComparisonDto> comparison,
                                  Long templateId,
                                  String filterFieldName,
                                  String filterFieldValue,
                                  ExcelStyles styles) {
        int rowIndex = 0;

        if (comparison.isEmpty()) {
            return;
        }

        // 1. ЗАГОЛОВКИ
        Row headerRow = sheet.createRow(rowIndex++);
        createCell(headerRow, 0, "Группа", styles.headerStyle);
        createCell(headerRow, 1, "Метрика", styles.headerStyle);
        createCell(headerRow, 2, "Тренд", styles.headerStyle);
        createCell(headerRow, 3, "%", styles.headerStyle);

        // 2. ДАННЫЕ ТРЕНДОВ
        for (StatisticsComparisonDto group : comparison) {
            String groupValue = group.getGroupFieldValue();

            // Пропускаем итоговые строки "ОБЩЕЕ КОЛИЧЕСТВО"
            if ("ОБЩЕЕ КОЛИЧЕСТВО".equals(groupValue)) {
                continue;
            }

            // Получаем все уникальные метрики для группы
            Map<String, List<StatisticsComparisonDto.MetricValue>> metricsByName = extractMetricsByName(group);

            for (String metricName : metricsByName.keySet()) {
                Row dataRow = sheet.createRow(rowIndex++);

                // Колонка "Группа"
                createCell(dataRow, 0, groupValue, styles.groupStyle);

                // Колонка "Метрика"
                createCell(dataRow, 1, metricName, styles.metricStyle);

                // Получаем исторические данные с анализом тренда
                try {
                    StatisticsHistoryDto history = historicalStatisticsService.getHistoryForMetric(
                            templateId, groupValue, metricName, filterFieldName, filterFieldValue, 0);

                    StatisticsHistoryDto.TrendInfo trendInfo = history.getTrendInfo();
                    CellStyle trendStyle = determineTrendStyle(trendInfo, styles);

                    // Колонка "Тренд" (описание)
                    String trendDescription = trendInfo != null ? trendInfo.getDescription() : "Нет данных";
                    createCell(dataRow, 2, trendDescription, trendStyle);

                    // Колонка "%" (процент изменения)
                    String changePercentage = formatHistoricalTrendPercentage(trendInfo);
                    createCell(dataRow, 3, changePercentage, trendStyle);

                } catch (Exception e) {
                    log.warn("Не удалось получить тренд для группы={}, метрика={}: {}",
                            groupValue, metricName, e.getMessage());

                    createCell(dataRow, 2, "Недостаточно данных", styles.normalStyle);
                    createCell(dataRow, 3, "-", styles.normalStyle);
                }
            }
        }

        // 3. НАСТРОЙКИ ЛИСТА
        // Закрепить первую строку (заголовок) и первые 2 колонки (Группа + Метрика)
        sheet.createFreezePane(2, 1);

        // Добавляем автофильтр для всех колонок
        if (rowIndex > 1) { // Есть данные кроме заголовка
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, 3));
        }

        // Устанавливаем ширину колонок
        sheet.setColumnWidth(0, 30 * 256); // Группа
        sheet.setColumnWidth(1, 25 * 256); // Метрика
        sheet.setColumnWidth(2, 40 * 256); // Тренд (описание)
        sheet.setColumnWidth(3, 10 * 256); // %
    }

    /**
     * Форматирует процент изменения из исторического анализа тренда
     *
     * @param trendInfo информация о тренде из HistoricalStatisticsService
     * @return отформатированный процент (например "53.8" или "-")
     */
    private String formatHistoricalTrendPercentage(StatisticsHistoryDto.TrendInfo trendInfo) {
        if (trendInfo == null || trendInfo.getChangePercentage() == null) {
            return "-";
        }

        double changePercentage = trendInfo.getChangePercentage();
        return String.format("%.1f", Math.abs(changePercentage));
    }

    /**
     * Определяет стиль ячейки тренда на основе направления тренда
     *
     * @param trendInfo информация о тренде
     * @param styles стили Excel
     * @return стиль ячейки
     */
    private CellStyle determineTrendStyle(StatisticsHistoryDto.TrendInfo trendInfo, ExcelStyles styles) {
        if (trendInfo == null || trendInfo.getDirection() == null) {
            return styles.normalStyle;
        }

        return switch (trendInfo.getDirection()) {
            case STRONG_GROWTH -> styles.increaseCriticalStyle;  // Сильный рост - яркий зелёный
            case GROWTH -> styles.increaseWarningStyle;          // Рост - светло-зелёный
            case STABLE -> styles.normalStyle;                   // Стабильность - обычный
            case DECLINE -> styles.decreaseWarningStyle;         // Спад - светло-оранжевый
            case STRONG_DECLINE -> styles.decreaseCriticalStyle; // Сильный спад - яркий красный
        };
    }

    /**
     * Извлекает метрики из группы, организованные по названию метрики
     *
     * @param group группа статистики
     * @return Map: название метрики -> список значений по операциям
     */
    private Map<String, List<StatisticsComparisonDto.MetricValue>> extractMetricsByName(StatisticsComparisonDto group) {
        Map<String, List<StatisticsComparisonDto.MetricValue>> result = new LinkedHashMap<>();

        List<StatisticsComparisonDto.OperationStatistics> operations = group.getOperations();

        // Собираем все уникальные имена метрик
        Set<String> allMetricNames = new LinkedHashSet<>();
        for (StatisticsComparisonDto.OperationStatistics operation : operations) {
            if (operation.getMetrics() != null) {
                allMetricNames.addAll(operation.getMetrics().keySet());
            }
        }

        // Для каждой метрики собираем значения по всем операциям
        for (String metricName : allMetricNames) {
            List<StatisticsComparisonDto.MetricValue> metricValues = new ArrayList<>();

            for (StatisticsComparisonDto.OperationStatistics operation : operations) {
                StatisticsComparisonDto.MetricValue metricValue = null;
                if (operation.getMetrics() != null) {
                    metricValue = operation.getMetrics().get(metricName);
                }
                metricValues.add(metricValue != null ? metricValue : createEmptyMetricValue());
            }

            result.put(metricName, metricValues);
        }

        return result;
    }

    /**
     * Создаёт пустое значение метрики (для случаев когда метрика отсутствует)
     *
     * @return пустое значение метрики
     */
    private StatisticsComparisonDto.MetricValue createEmptyMetricValue() {
        StatisticsComparisonDto.MetricValue empty = new StatisticsComparisonDto.MetricValue();
        empty.setCurrentValue(0L);
        empty.setPreviousValue(null);
        empty.setChangePercentage(0.0);
        empty.setChangeType(StatisticsComparisonDto.ChangeType.STABLE);
        empty.setAlertLevel(StatisticsComparisonDto.AlertLevel.NORMAL);
        return empty;
    }

    /**
     * Создаёт стили для Excel
     */
    private ExcelStyles createStyles(Workbook workbook) {
        ExcelStyles styles = new ExcelStyles();

        // Базовый шрифт
        Font baseFont = workbook.createFont();
        baseFont.setFontName("Arial");
        baseFont.setFontHeightInPoints((short) 10);

        // Жирный шрифт
        Font boldFont = workbook.createFont();
        boldFont.setFontName("Arial");
        boldFont.setFontHeightInPoints((short) 10);
        boldFont.setBold(true);

        // Стиль заголовка
        styles.headerStyle = workbook.createCellStyle();
        styles.headerStyle.setFont(boldFont);
        styles.headerStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        styles.headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.headerStyle.setBorderBottom(BorderStyle.THIN);
        styles.headerStyle.setBorderTop(BorderStyle.THIN);
        styles.headerStyle.setBorderLeft(BorderStyle.THIN);
        styles.headerStyle.setBorderRight(BorderStyle.THIN);
        styles.headerStyle.setWrapText(true);

        // Стиль подзаголовка
        styles.headerSubStyle = workbook.createCellStyle();
        styles.headerSubStyle.cloneStyleFrom(styles.headerStyle);
        Font smallFont = workbook.createFont();
        smallFont.setFontName("Arial");
        smallFont.setFontHeightInPoints((short) 9);
        styles.headerSubStyle.setFont(smallFont);

        // Стиль группы
        styles.groupStyle = workbook.createCellStyle();
        styles.groupStyle.setFont(boldFont);
        styles.groupStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.groupStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.groupStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        styles.groupStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.groupStyle.setBorderBottom(BorderStyle.THIN);
        styles.groupStyle.setBorderTop(BorderStyle.THIN);
        styles.groupStyle.setBorderLeft(BorderStyle.THIN);
        styles.groupStyle.setBorderRight(BorderStyle.THIN);
        styles.groupStyle.setWrapText(true);

        // Стиль итоговой группы
        styles.totalGroupStyle = workbook.createCellStyle();
        styles.totalGroupStyle.cloneStyleFrom(styles.groupStyle);
        styles.totalGroupStyle.setFillForegroundColor(IndexedColors.GOLD.getIndex());

        // Стиль метрики
        styles.metricStyle = workbook.createCellStyle();
        styles.metricStyle.setFont(baseFont);
        styles.metricStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.metricStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.metricStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        styles.metricStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.metricStyle.setBorderBottom(BorderStyle.THIN);
        styles.metricStyle.setBorderTop(BorderStyle.THIN);
        styles.metricStyle.setBorderLeft(BorderStyle.THIN);
        styles.metricStyle.setBorderRight(BorderStyle.THIN);

        // Стиль итоговой метрики
        styles.totalMetricStyle = workbook.createCellStyle();
        styles.totalMetricStyle.cloneStyleFrom(styles.metricStyle);
        styles.totalMetricStyle.setFont(boldFont);
        styles.totalMetricStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());

        // Обычный стиль
        styles.normalStyle = workbook.createCellStyle();
        styles.normalStyle.setFont(baseFont);
        styles.normalStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.normalStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.normalStyle.setBorderBottom(BorderStyle.THIN);
        styles.normalStyle.setBorderTop(BorderStyle.THIN);
        styles.normalStyle.setBorderLeft(BorderStyle.THIN);
        styles.normalStyle.setBorderRight(BorderStyle.THIN);
        styles.normalStyle.setWrapText(true);

        // Стиль для роста (warning)
        styles.increaseWarningStyle = workbook.createCellStyle();
        styles.increaseWarningStyle.cloneStyleFrom(styles.normalStyle);
        styles.increaseWarningStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        styles.increaseWarningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Стиль для роста (critical)
        styles.increaseCriticalStyle = workbook.createCellStyle();
        styles.increaseCriticalStyle.cloneStyleFrom(styles.normalStyle);
        styles.increaseCriticalStyle.setFillForegroundColor(IndexedColors.LIME.getIndex());
        styles.increaseCriticalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Стиль для падения (warning)
        styles.decreaseWarningStyle = workbook.createCellStyle();
        styles.decreaseWarningStyle.cloneStyleFrom(styles.normalStyle);
        styles.decreaseWarningStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        styles.decreaseWarningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Стиль для падения (critical)
        styles.decreaseCriticalStyle = workbook.createCellStyle();
        styles.decreaseCriticalStyle.cloneStyleFrom(styles.normalStyle);
        styles.decreaseCriticalStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        styles.decreaseCriticalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Стиль для итоговых значений
        styles.totalValueStyle = workbook.createCellStyle();
        styles.totalValueStyle.cloneStyleFrom(styles.normalStyle);
        styles.totalValueStyle.setFont(boldFont);
        styles.totalValueStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        styles.totalValueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        return styles;
    }

    /**
     * Вспомогательный класс для хранения стилей Excel
     */
    private static class ExcelStyles {
        CellStyle headerStyle;
        CellStyle headerSubStyle;
        CellStyle groupStyle;
        CellStyle totalGroupStyle;
        CellStyle metricStyle;
        CellStyle totalMetricStyle;
        CellStyle normalStyle;
        CellStyle increaseWarningStyle;
        CellStyle increaseCriticalStyle;
        CellStyle decreaseWarningStyle;
        CellStyle decreaseCriticalStyle;
        CellStyle totalValueStyle;
    }
}
