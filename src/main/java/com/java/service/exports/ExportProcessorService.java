package com.java.service.exports;

import com.java.dto.ExportRequestDto;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.model.entity.ExportTemplate;
import com.java.model.enums.ExportStatus;
import com.java.repository.ExportSessionRepository;
import com.java.repository.FileOperationRepository;
import com.java.service.exports.strategies.ExportStrategy;
import com.java.service.exports.strategies.ExportStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис обработки процесса экспорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExportProcessorService {

    private final ExportDataService dataService;
    private final FileGeneratorService fileGeneratorService;
    private final ExportStrategyFactory strategyFactory;
    private final ExportSessionRepository sessionRepository;
    private final FileOperationRepository fileOperationRepository;
    private final ExportStatisticsWriterService statisticsWriterService;

    // Флаги отмены для каждой сессии
    private final Map<Long, AtomicBoolean> cancellationFlags = new HashMap<>();

    /**
     * Обрабатывает экспорт данных
     */
    @Transactional
    public void processExport(ExportSession session, ExportRequestDto request) {
        log.info("Начало обработки экспорта, сессия ID: {}", session.getId());

        // Регистрируем флаг отмены
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancellationFlags.put(session.getId(), cancelled);

        try {
            // Обновляем статус
            updateSessionStatus(session, ExportStatus.PROCESSING);

            // Загружаем шаблон с полями и фильтрами
            ExportTemplate template = session.getTemplate();

            // 1. Загружаем данные
            log.info("Загрузка данных для экспорта");
            List<Map<String, Object>> data = dataService.loadData(
                    request.getOperationIds() != null ? request.getOperationIds() : Collections.emptyList(),
                    template,
                    request.getDateFrom(),
                    request.getDateTo(),
                    request.getAdditionalFilters()
            );
            log.debug("После загрузки получено {} строк", data.size());
            if (!data.isEmpty()) {
                log.debug("Пример загруженной строки: {}", data.get(0));
            }
            session.setTotalRows((long) data.size());

            // Подсчитываем отфильтрованные записи
            Long totalPossible = dataService.countData(
                    request.getOperationIds() != null ? request.getOperationIds() : Collections.emptyList(),
                    template,
                    null, // без фильтра дат
                    null,
                    null  // без дополнительных фильтров
            );
            session.setFilteredRows(totalPossible - data.size());

            sessionRepository.save(session);

            // Проверяем отмену
            if (cancelled.get()) {
                handleCancellation(session);
                return;
            }

            // 2. Применяем стратегию обработки
            log.info("Применение стратегии: {}", template.getExportStrategy());
            ExportStrategy strategy = strategyFactory.getStrategy(template.getExportStrategy());

            // Подготавливаем контекст для стратегии
            Map<String, Object> context = new HashMap<>();
            context.put("operationIds", request.getOperationIds());
            context.put("session", session);
            context.put("maxReportAgeDays", request.getMaxReportAgeDays());
            if (session.getFileOperation() != null && session.getFileOperation().getClient() != null) {
                context.put("clientRegionCode", session.getFileOperation().getClient().getRegionCode());
            }

            // Для стратегии TASK_REPORT нужны дополнительные параметры
            if (template.getExportStrategy().name().equals("TASK_REPORT") &&
                    request.getOperationIds() != null && request.getOperationIds().size() >= 2) {
                // Предполагаем, что первая операция - задание, вторая - отчет
                context.put("taskOperationId", request.getOperationIds().get(0));
                context.put("reportOperationId", request.getOperationIds().get(1));
            }

            List<Map<String, Object>> processedData = strategy.processData(data, template, context);
            log.debug("После применения стратегии осталось {} строк", processedData.size());
            if (!processedData.isEmpty()) {
                log.debug("Пример строки после стратегии: {}", processedData.get(0));
            }

            // Подсчитываем модифицированные записи
            session.setModifiedRows((long) (data.size() - processedData.size()));
            session.setExportedRows((long) processedData.size());

            // Проверяем отмену
            if (cancelled.get()) {
                handleCancellation(session);
                return;
            }

            // 2.5. Сохраняем статистику по экспортированным данным
            log.info("Сохранение статистики экспорта");
            try {
                statisticsWriterService.saveExportStatistics(session, template, processedData);
            } catch (Exception e) {
                log.error("Ошибка сохранения статистики экспорта", e);
                // Не прерываем экспорт из-за ошибки статистики
            }

            // 3. Генерируем файл
            log.info("Генерация файла экспорта");
            String fileName = generateFileName(template, request);

            // Переопределяем параметры файла если указаны
            if (request.getFileFormat() != null) {
                template.setFileFormat(request.getFileFormat());
            }
            if (request.getCsvDelimiter() != null) {
                template.setCsvDelimiter(request.getCsvDelimiter());
            }
            if (request.getCsvEncoding() != null) {
                template.setCsvEncoding(request.getCsvEncoding());
            }

            Path filePath = fileGeneratorService.generateFile(processedData, template, fileName);

            // Обновляем информацию о файле
            session.setResultFilePath(filePath.toString());
            session.setFileSize(Files.size(filePath));

            // Обновляем FileOperation
            FileOperation fileOperation = session.getFileOperation();
            fileOperation.setFileName(fileName);
            fileOperation.setResultFilePath(filePath.toString());
            fileOperation.setFileSize(Files.size(filePath));
            fileOperation.setRecordCount(processedData.size());
            fileOperation.setTotalRecords(data.size());
            fileOperation.markAsCompleted(processedData.size());

            fileOperationRepository.save(fileOperation);

            // 4. Завершаем экспорт
            finalizeExport(session);

        } catch (Exception e) {
            log.error("Ошибка обработки экспорта", e);
            handleExportError(session, e);
        } finally {
            // Удаляем флаг отмены
            cancellationFlags.remove(session.getId());
        }
    }

    /**
     * Генерирует имя файла
     */
    private String generateFileName(ExportTemplate template, ExportRequestDto request) {
        StringBuilder fileName = new StringBuilder();

        // Если есть кастомный шаблон, используем его
        if (template.getFilenameTemplate() != null && !template.getFilenameTemplate().trim().isEmpty()) {
            return generateCustomFileName(template, request);
        }

        // Стандартная генерация
        fileName.append("export");

        // Имя клиента
        if (Boolean.TRUE.equals(template.getIncludeClientName())) {
            String clientName = template.getClient().getName()
                    .replaceAll("[^a-zA-Z0-9а-яА-Я]", "_");
            fileName.append("_").append(clientName);
        }

        // Тип экспорта
        if (Boolean.TRUE.equals(template.getIncludeExportType()) &&
                template.getExportTypeLabel() != null) {
            fileName.append("_").append(template.getExportTypeLabel()
                    .replaceAll("[^a-zA-Z0-9а-яА-Я]", "_"));
        }

        // Номер задания
        if (Boolean.TRUE.equals(template.getIncludeTaskNumber())) {
            String taskNumber = extractTaskNumber(request);
            if (taskNumber != null) {
                fileName.append("_").append(taskNumber);
            }
        }

        // Дата и время
        fileName.append("_").append(ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

        // Расширение
        String extension = "CSV".equalsIgnoreCase(template.getFileFormat()) ? ".csv" : ".xlsx";
        fileName.append(extension);

        return fileName.toString();
    }

    /**
     * Генерирует имя файла по кастомному шаблону
     */
    private String generateCustomFileName(ExportTemplate template, ExportRequestDto request) {
        String template_str = template.getFilenameTemplate();

        // Заменяем плейсхолдеры
        template_str = template_str.replace("{client}",
                template.getClient().getName().replaceAll("[^a-zA-Z0-9а-яА-Я]", "_"));

        template_str = template_str.replace("{date}",
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        template_str = template_str.replace("{time}",
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));

        if (template.getExportTypeLabel() != null) {
            template_str = template_str.replace("{type}",
                    template.getExportTypeLabel().replaceAll("[^a-zA-Z0-9а-яА-Я]", "_"));
        }

        String taskNumber = extractTaskNumber(request);
        if (taskNumber != null) {
            template_str = template_str.replace("{task}", taskNumber);
        }

        // Добавляем расширение если его нет
        if (!template_str.contains(".")) {
            String extension = "CSV".equalsIgnoreCase(template.getFileFormat()) ? ".csv" : ".xlsx";
            template_str += extension;
        }

        return template_str;
    }

    /**
     * Извлекает номер задания из данных операции
     */
    private String extractTaskNumber(ExportRequestDto request) {
        // Логика извлечения номера задания из операций
        // Может потребоваться дополнительный запрос к БД
        return null; // Временно
    }

    /**
     * Обновляет статус сессии
     */
    private void updateSessionStatus(ExportSession session, ExportStatus status) {
        session.setStatus(status);
        sessionRepository.save(session);
    }

    /**
     * Финализирует экспорт
     */
    private void finalizeExport(ExportSession session) {
        log.info("Финализация экспорта, сессия ID: {}", session.getId());

        session.setStatus(ExportStatus.COMPLETED);
        session.setCompletedAt(ZonedDateTime.now());

        sessionRepository.save(session);

        log.info("Экспорт завершен. Экспортировано: {}, Отфильтровано: {}, Модифицировано: {}",
                session.getExportedRows(), session.getFilteredRows(), session.getModifiedRows());
    }

    /**
     * Обрабатывает отмену экспорта
     */
    private void handleCancellation(ExportSession session) {
        log.info("Обработка отмены экспорта, сессия ID: {}", session.getId());

        session.setStatus(ExportStatus.CANCELLED);
        session.setCompletedAt(ZonedDateTime.now());

        // Удаляем частично созданный файл если есть
        if (session.getResultFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(session.getResultFilePath()));
            } catch (Exception e) {
                log.error("Ошибка удаления файла", e);
            }
        }

        sessionRepository.save(session);
    }

    /**
     * Обрабатывает ошибку экспорта
     */
    private void handleExportError(ExportSession session, Exception e) {
        session.setStatus(ExportStatus.FAILED);
        session.setCompletedAt(ZonedDateTime.now());
        session.setErrorMessage(e.getMessage());

        FileOperation fileOperation = session.getFileOperation();
        fileOperation.markAsFailed(e.getMessage());

        sessionRepository.save(session);
        fileOperationRepository.save(fileOperation);
    }

    /**
     * Отменяет экспорт
     */
    public void cancelExport(Long sessionId) {
        AtomicBoolean flag = cancellationFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
            log.info("Запрошена отмена экспорта для сессии ID: {}", sessionId);
        }
    }
}