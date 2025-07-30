package com.java.service.imports;

import com.java.config.ImportConfig;
import com.java.config.MemoryMonitor;
import com.java.model.FileOperation;
import com.java.model.entity.*;
import com.java.model.enums.*;
import com.java.repository.*;
import com.java.service.imports.handlers.DataTransformationService;
import com.java.service.imports.handlers.DuplicateCheckService;
import com.java.service.imports.handlers.EntityPersistenceService;
import com.java.util.PathResolver;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Основной сервис обработки импорта файлов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportProcessorService {

    private final ImportSessionRepository sessionRepository;
    private final ImportErrorRepository errorRepository;
    private final FileMetadataRepository metadataRepository;
    private final ImportTemplateRepository templateRepository;

    private final DataTransformationService transformationService;
    private final DuplicateCheckService duplicateCheckService;
    private final EntityPersistenceService persistenceService;
    private final ImportProgressService progressService;

    private final ImportConfig.ImportSettings importSettings;
    private final MemoryMonitor memoryMonitor;
    private final PathResolver pathResolver;

    // Хранилище для отмены операций
    private final Map<Long, AtomicBoolean> cancellationFlags = new HashMap<>();

    /**
     * Обрабатывает импорт файла
     */
    @Transactional
    public void processImport(ImportSession session) {
        // Загружать сессию из репозитория внутри транзакции, чтобы избежать
        // проблем с detached entity и коллекциями orphanRemoval
        ImportSession managedSession = sessionRepository.findById(session.getId())
                .orElseThrow(() -> new RuntimeException("Сессия импорта не найдена"));

        session = managedSession;
        log.info("Начало обработки импорта, сессия ID: {}", session.getId());

        // Регистрируем флаг отмены
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellationFlags.put(session.getId(), cancelled);

        try {
            // Обновляем статус
            updateSessionStatus(session, ImportStatus.PROCESSING);

            // Получаем метаданные файла
//            FileMetadata metadata = metadataRepository.findByImportSession(session)
//                    .orElseThrow(() -> new RuntimeException("Метаданные файла не найдены"));
            FileMetadata metadata = metadataRepository.findByImportSession(session)
                    .orElse(session.getFileMetadata());
            if (metadata == null) {
                throw new RuntimeException("Метаданные файла не найдены");
            }

            // Получаем шаблон с полями
            ImportTemplate template = templateRepository.findByIdWithFields(session.getTemplate().getId())
                    .orElseThrow(() -> new RuntimeException("Шаблон не найден"));

            // Определяем обработчик по типу файла
            if ("CSV".equalsIgnoreCase(metadata.getFileFormat()) ||
                    "TXT".equalsIgnoreCase(metadata.getFileFormat())) {
                processCsvFile(session, template, metadata, cancelled);
            } else if ("XLSX".equalsIgnoreCase(metadata.getFileFormat()) ||
                    "XLS".equalsIgnoreCase(metadata.getFileFormat())) {
                processExcelFile(session, template, metadata, cancelled);
            } else {
                throw new UnsupportedOperationException("Неподдерживаемый формат файла: " +
                        metadata.getFileFormat());
            }

            // Финализация
            if (cancelled.get()) {
                handleCancellation(session);
            } else {
                finalizeImport(session);
            }

        } catch (Exception e) {
            log.error("Ошибка обработки импорта", e);
            handleImportError(session, e);
        } finally {
            // Удаляем флаг отмены
            cancellationFlags.remove(session.getId());
        }
    }

    /**
     * Обрабатывает CSV файл
     */
    private void processCsvFile(ImportSession session, ImportTemplate template,
                                FileMetadata metadata, AtomicBoolean cancelled) throws Exception {
        Path filePath = Paths.get(metadata.getTempFilePath());

        // Определяем общее количество строк в файле до начала обработки
        long totalLines;
        try (Stream<String> lines = Files.lines(filePath, Charset.forName(metadata.getDetectedEncoding()))) {
            totalLines = lines.count();
        }

        try (Reader reader = Files.newBufferedReader(filePath,
                Charset.forName(metadata.getDetectedEncoding()))) {

            // Создаем парсер CSV
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(metadata.getDetectedDelimiter().charAt(0))
                    .withQuoteChar(metadata.getDetectedQuoteChar() != null ?
                            metadata.getDetectedQuoteChar().charAt(0) : '"')
                    .withEscapeChar(metadata.getDetectedEscapeChar() != null ?
                            metadata.getDetectedEscapeChar().charAt(0) : '\\')
                    .build();

            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(parser)
                    .build();

            String[] headers = null;
            int skippedRows = 0;

            // Сначала считываем строку заголовков, если она присутствует
            if (metadata.getHasHeader()) {
                headers = csvReader.readNext();
                skippedRows++; // учтем считанную строку
            }

            // Далее пропускаем оставшиеся строки, указанные в шаблоне
            while (skippedRows < template.getSkipHeaderRows()) {
                csvReader.readNext();
                skippedRows++;
            }
            int startRowNumber = skippedRows;
            // Теперь мы знаем общее количество строк
            long totalRows = totalLines - startRowNumber;
            session.setTotalRows(totalRows);
            sessionRepository.save(session);

            // Обрабатываем данные батчами
            processBatches(session, template, csvReader, headers, cancelled);
        }
    }

    /**
     * Обрабатывает Excel файл
     */
    private void processExcelFile(ImportSession session, ImportTemplate template,
                                  FileMetadata metadata, AtomicBoolean cancelled) throws Exception {
        Path filePath = Paths.get(metadata.getTempFilePath());

        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Пропускаем строки заголовка
            int startRow = template.getSkipHeaderRows();

            // Читаем заголовки если есть
            String[] headers = null;
            if (metadata.getHasHeader() && startRow > 0) {
                Row headerRow = sheet.getRow(startRow - 1);
                if (headerRow != null) {
                    headers = new String[headerRow.getLastCellNum()];
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.getCell(i);
                        headers[i] = cell != null ? getCellValueAsString(cell) : "";
                    }
                }
            }

            // Подсчитываем общее количество строк
            long totalRows = sheet.getLastRowNum() - startRow + 1;
            session.setTotalRows(totalRows);
            sessionRepository.save(session);

            // Обрабатываем строки батчами
            List<Map<String, String>> batch = new ArrayList<>();
            AtomicLong rowNumber = new AtomicLong(startRow);

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                if (cancelled.get()) break;

                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Конвертируем строку в Map
                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    String value = cell != null ? getCellValueAsString(cell) : "";

                    // Добавляем по индексу
                    rowData.put(String.valueOf(j), value);

                    // Добавляем по имени заголовка если есть
                    if (headers != null && j < headers.length && headers[j] != null) {
                        rowData.put(headers[j], value);
                    }
                }

                batch.add(rowData);

                // Обрабатываем батч
                if (batch.size() >= importSettings.getBatchSize()) {
                    processBatch(session, template, batch, rowNumber);
                    batch.clear();
                }
            }

            // Обрабатываем оставшиеся записи
            if (!batch.isEmpty() && !cancelled.get()) {
                processBatch(session, template, batch, rowNumber);
            }
        }
    }

    /**
     * Обрабатывает данные батчами из CSV
     */
    private void processBatches(ImportSession session, ImportTemplate template,
                                CSVReader csvReader, String[] headers,
                                AtomicBoolean cancelled) throws Exception {
        List<Map<String, String>> batch = new ArrayList<>();
        String[] row;
        AtomicLong rowNumber = new AtomicLong(template.getSkipHeaderRows());

        while ((row = csvReader.readNext()) != null && !cancelled.get()) {
            rowNumber.incrementAndGet();

            // Проверяем доступность памяти
            if (!memoryMonitor.isMemoryAvailable()) {
                log.warn("Недостаточно памяти, ожидание освобождения...");
                Thread.sleep(5000);
                System.gc();
            }

            // Конвертируем массив в Map для удобства
            Map<String, String> rowData = new HashMap<>();
            for (int i = 0; i < row.length; i++) {
                // Добавляем по индексу
                rowData.put(String.valueOf(i), row[i]);

                // Добавляем по имени заголовка если есть
                if (headers != null && i < headers.length && headers[i] != null) {
                    rowData.put(headers[i], row[i]);
                }
            }

            batch.add(rowData);

            // Обрабатываем батч
            if (batch.size() >= importSettings.getBatchSize()) {
                processBatch(session, template, batch, rowNumber);
                batch.clear();
            }
        }

        // Обрабатываем оставшиеся записи
        if (!batch.isEmpty() && !cancelled.get()) {
            processBatch(session, template, batch, rowNumber);
        }
    }

    /**
     * Обрабатывает батч записей
     */
    @Transactional
    public void processBatch(ImportSession session, ImportTemplate template,
                             List<Map<String, String>> batch, AtomicLong currentRow) {
        log.debug("=== Обработка батча из {} записей, сессия ID: {} ===", batch.size(), session.getId());

        List<Map<String, Object>> transformedBatch = new ArrayList<>();
        Set<String> batchDuplicateKeys = new HashSet<>();

        for (Map<String, String> rowData : batch) {
            long rowNumber = currentRow.get() - batch.size() + batch.indexOf(rowData) + 1;
            log.trace("Обработка строки {}", rowNumber);

            try {
                // Трансформируем данные согласно маппингу
                Map<String, Object> transformedData = transformationService.transformRow(
                        rowData, template, rowNumber
                );

                // Проверяем на дубликаты если нужно
                if (template.getDuplicateStrategy() == DuplicateStrategy.SKIP_DUPLICATES) {
                    String duplicateKey = duplicateCheckService.generateDuplicateKey(
                            transformedData, template
                    );

                    if (batchDuplicateKeys.contains(duplicateKey) ||
                            duplicateCheckService.isDuplicate(duplicateKey, template)) {

                        session.setDuplicateRows(session.getDuplicateRows() + 1);
                        recordError(session, rowNumber, null, null,
                                ErrorType.DUPLICATE_ERROR, "Дубликат записи");
                        continue;
                    }

                    batchDuplicateKeys.add(duplicateKey);
                }

                transformedBatch.add(transformedData);

            } catch (Exception e) {
                log.error("Ошибка обработки строки {}: {}", rowNumber, e.getMessage());
                recordError(session, rowNumber, null, null,
                        ErrorType.TRANSFORMATION_ERROR, e.getMessage());

                // Проверяем стратегию обработки ошибок
                if (template.getErrorStrategy() == ErrorStrategy.STOP_ON_ERROR) {
                    throw new RuntimeException("Критическая ошибка в строке " + rowNumber, e);
                }
            }
        }

        // Сохраняем батч в БД
        if (!transformedBatch.isEmpty()) {
            try {
                int saved = persistenceService.saveBatch(
                        transformedBatch,
                        template.getEntityType(),
                        session);
                session.setSuccessRows(session.getSuccessRows() + saved);
                log.debug("Сохранено {} записей в БД", saved);

                // Сохраняем ключи дубликатов
                if (template.getDuplicateStrategy() == DuplicateStrategy.SKIP_DUPLICATES) {
                    duplicateCheckService.saveDuplicateKeys(batchDuplicateKeys, template.getEntityType());
                }

            } catch (Exception e) {
                log.error("Ошибка сохранения батча из {} записей", transformedBatch.size(), e);
                session.setErrorRows(session.getErrorRows() + transformedBatch.size());

                if (template.getErrorStrategy() == ErrorStrategy.STOP_ON_ERROR) {
                    throw e;
                }
            }
        }

        // Обновляем прогресс
        long oldProcessedRows = session.getProcessedRows();
        session.setProcessedRows(session.getProcessedRows() + batch.size());
        log.debug("Прогресс обновлен: {} -> {} (обработано +{} записей)",
                oldProcessedRows, session.getProcessedRows(), batch.size());

        // ПРИНУДИТЕЛЬНО СОХРАНЯЕМ В ТРАНЗАКЦИИ
        session = sessionRepository.saveAndFlush(session);
        log.debug("Сессия сохранена в БД с прогрессом {}%", session.getProgressPercentage());

        // Отправляем обновление прогресса
        try {
            progressService.sendProgressUpdate(session);
            log.debug("WebSocket обновление прогресса отправлено");
        } catch (Exception e) {
            log.error("Ошибка отправки WebSocket обновления", e);
        }
    }

    /**
     * Записывает ошибку импорта
     */
    private void recordError(ImportSession session, Long rowNumber, String columnName,
                             String fieldValue, ErrorType errorType, String errorMessage) {
        ImportError error = ImportError.builder()
                .importSession(session)
                .rowNumber(rowNumber)
                .columnName(columnName)
                .fieldValue(fieldValue)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();

        errorRepository.save(error);
        session.setErrorRows(session.getErrorRows() + 1);
    }

    /**
     * Финализирует импорт
     */
    private void finalizeImport(ImportSession session) {
        log.info("Финализация импорта, сессия ID: {}", session.getId());

        session.setStatus(ImportStatus.COMPLETING);
        session.setCompletedAt(ZonedDateTime.now());

        // Определяем финальный статус
        if (session.getErrorRows() == 0) {
            session.setStatus(ImportStatus.COMPLETED);
        } else if (session.getSuccessRows() == 0) {
            session.setStatus(ImportStatus.FAILED);
        } else {
            session.setStatus(ImportStatus.COMPLETED);
        }

        // Обновляем FileOperation
        FileOperation fileOperation = session.getFileOperation();
        fileOperation.setStatus(FileOperation.OperationStatus.COMPLETED);
        fileOperation.setRecordCount(session.getSuccessRows().intValue());
        fileOperation.setTotalRecords(session.getTotalRows().intValue());
        fileOperation.setProcessedRecords(session.getTotalRows().intValue());
        fileOperation.setProcessingProgress(100);
        fileOperation.setCompletedAt(ZonedDateTime.now());

        // гарантируем, что обработанные строки равны общему количеству
        session.setProcessedRows(session.getTotalRows());

        sessionRepository.save(session);

        // Отправляем финальное обновление
        progressService.sendProgressUpdate(session);

        log.info("Импорт завершен. Успешно: {}, Ошибок: {}, Дубликатов: {}",
                session.getSuccessRows(), session.getErrorRows(), session.getDuplicateRows());
    }

    /**
     * Обрабатывает отмену импорта
     */
    private void handleCancellation(ImportSession session) {
        log.info("Обработка отмены импорта, сессия ID: {}", session.getId());

        session.setStatus(ImportStatus.CANCELLED);
        session.setCompletedAt(ZonedDateTime.now());
        session.setIsCancelled(true);

        // Откатываем изменения если нужно
        if (session.getSuccessRows() > 0) {
            try {
                persistenceService.rollbackImport(session);
                session.setSuccessRows(0L);
            } catch (Exception e) {
                log.error("Ошибка отката импорта", e);
            }
        }

        sessionRepository.save(session);
        progressService.sendProgressUpdate(session);
    }

    /**
     * Обрабатывает ошибку импорта
     */
    private void handleImportError(ImportSession session, Exception e) {
        session.setStatus(ImportStatus.FAILED);
        session.setCompletedAt(ZonedDateTime.now());
        session.setErrorMessage(e.getMessage());

        FileOperation fileOperation = session.getFileOperation();
        fileOperation.setStatus(FileOperation.OperationStatus.FAILED);
        fileOperation.setErrorMessage(e.getMessage());
        fileOperation.setCompletedAt(ZonedDateTime.now());

        sessionRepository.save(session);
        progressService.sendProgressUpdate(session);
    }

    /**
     * Отменяет импорт
     */
    public void cancelImport(Long sessionId) {
        AtomicBoolean flag = cancellationFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
            log.info("Запрошена отмена импорта для сессии ID: {}", sessionId);
        }
    }

    /**
     * Обновляет статус сессии
     */
    private void updateSessionStatus(ImportSession session, ImportStatus status) {
        session.setStatus(status);
        sessionRepository.save(session);
        progressService.sendProgressUpdate(session);
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
                // Убираем дробную часть для целых чисел
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
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
}
