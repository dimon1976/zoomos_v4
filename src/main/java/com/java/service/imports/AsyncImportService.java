package com.java.service.imports;

import com.java.config.MemoryMonitor;
import com.java.dto.ImportRequestDto;
import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.model.entity.FileMetadata;
import com.java.model.entity.ImportSession;
import com.java.model.entity.ImportTemplate;
import com.java.model.enums.ImportStatus;
import com.java.repository.*;
import com.java.service.file.FileAnalyzerService;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Сервис для асинхронного выполнения импорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncImportService {

    private final ImportTemplateRepository templateRepository;
    private final ImportSessionRepository sessionRepository;
    private final FileOperationRepository fileOperationRepository;
    private final FileMetadataRepository metadataRepository;
    private final ClientRepository clientRepository;

    private final FileAnalyzerService fileAnalyzerService;
    private final ImportProcessorService processorService;
    private final ImportProgressService progressService;
    private final MemoryMonitor memoryMonitor;
    private final PathResolver pathResolver;

    @Autowired
    @Qualifier("importTaskExecutor")
    private Executor taskExecutor;

    /**
     * Запускает асинхронный импорт файла
     */
    @Async("importTaskExecutor")
    @Transactional
    public CompletableFuture<ImportSession> startImport(ImportRequestDto request, Long clientId) {

        if (!memoryMonitor.isMemoryAvailable()) {
            log.warn("Недостаточно памяти для запуска импорта, отложен");
            return CompletableFuture.failedFuture(
                    new RuntimeException("Недостаточно памяти для импорта"));
        }
        log.info("Запуск асинхронного импорта для клиента ID: {}", clientId);

        try {
            // Получаем клиента и шаблон
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

            ImportTemplate template = templateRepository.findByIdWithFields(request.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Шаблон не найден"));

            // Проверяем, что шаблон принадлежит клиенту
            if (!template.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Шаблон не принадлежит клиенту");
            }

            // Создаем FileOperation
            FileOperation fileOperation = createFileOperation(request.getFile(), client);
            fileOperation = fileOperationRepository.save(fileOperation);

            // Создаем сессию импорта
            ImportSession session = createImportSession(fileOperation, template);
            session = sessionRepository.save(session);

            log.info("Сессия импорта создана с ID: {}, операция ID: {}",
                    session.getId(), fileOperation.getId());

//            // ВАЖНО: Анализ и настройка метаданных
//            setupSessionMetadata(session, request);
//
//            // Сохраняем обновленную сессию
//            session = sessionRepository.save(session);

            // ЗАПУСКАЕМ ОБРАБОТКУ ПОСЛЕ КОММИТА ТРАНЗАКЦИИ
            ImportSession finalSession = session;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> {
                        try {
                            // Загружаем свежую сущность внутри новой транзакции
                            ImportSession managed = sessionRepository.findById(finalSession.getId())
                                    .orElseThrow(() -> new RuntimeException("Сессия импорта не найдена"));

                            // Сначала анализируем файл и настраиваем метаданные
                            setupSessionMetadata(managed, request);
                            sessionRepository.save(managed);

                            log.info("Начало фоновой обработки импорта для сессии: {}", managed.getId());
                            processorService.processImport(managed);
                        } catch (Exception e) {
                            log.error("Ошибка фоновой обработки импорта", e);
                            handleAsyncImportError(finalSession, e);
                        }
                    }, taskExecutor);
                }
            });

            // НЕМЕДЛЕННО ВОЗВРАЩАЕМ СЕССИЮ (НЕ ЖДЕМ ОБРАБОТКИ)
            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            log.error("Ошибка создания импорта", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Настраивает метаданные сессии
     */
    private void setupSessionMetadata(ImportSession session, ImportRequestDto request) {
        try {
            // Анализируем файл
            Path tempFilePath = request.getSavedFilePath() != null
                    ? request.getSavedFilePath()
                    : pathResolver.saveToTempFile(request.getFile(), "import_temp");

            FileMetadata metadata = fileAnalyzerService.analyzeFile(
                    tempFilePath,
                    request.getFile().getOriginalFilename()
            );
            metadata.setImportSession(session);
            metadata = metadataRepository.save(metadata);
            // сохраняем связь в обе стороны, чтобы корректно работала каскадная
            // обработка и дальнейшие загрузки метаданных
            session.setFileMetadata(metadata);

            // Устанавливаем общее количество строк (примерная оценка)
            long estimatedRows = estimateRowCount(metadata);
            session.setTotalRows(estimatedRows);
            session.setStatus(ImportStatus.PROCESSING);

            log.info("Метаданные настроены: {} строк, статус: {}",
                    estimatedRows, session.getStatus());

        } catch (Exception e) {
            log.error("Ошибка настройки метаданных", e);
            session.setStatus(ImportStatus.FAILED);
            session.setErrorMessage("Ошибка анализа файла: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает ошибки асинхронного импорта
     */
    private void handleAsyncImportError(ImportSession session, Exception e) {
        try {
            session.setStatus(ImportStatus.FAILED);
            session.setCompletedAt(ZonedDateTime.now());
            session.setErrorMessage(e.getMessage());
            sessionRepository.save(session);

            FileOperation fileOperation = session.getFileOperation();
            fileOperation.markAsFailed(e.getMessage());
            fileOperationRepository.save(fileOperation);

            progressService.sendErrorNotification(session, e.getMessage());

        } catch (Exception ex) {
            log.error("Ошибка обработки ошибки импорта", ex);
        }
    }

    /**
     * Создает FileOperation
     */
    private FileOperation createFileOperation(MultipartFile file, Client client) {
        return FileOperation.builder()
                .client(client)
                .operationType(FileOperation.OperationType.IMPORT)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .status(FileOperation.OperationStatus.PROCESSING)
                .startedAt(ZonedDateTime.now())
                .createdBy("system") // TODO: получать из контекста безопасности
                .build();
    }

    /**
     * Создает сессию импорта
     */
    private ImportSession createImportSession(FileOperation fileOperation, ImportTemplate template) {
        return ImportSession.builder()
                .fileOperation(fileOperation)
                .template(template)
                .status(ImportStatus.INITIALIZING)
                .processedRows(0L)
                .successRows(0L)
                .errorRows(0L)
                .duplicateRows(0L)
                .startedAt(ZonedDateTime.now())
                .build();
    }

    /**
     * Оценивает количество строк в файле
     */
    private long estimateRowCount(FileMetadata metadata) {
        // Простая оценка на основе размера файла
        // Можно улучшить, анализируя среднюю длину строки из sample data
        long avgBytesPerRow = 100; // Примерное значение

        if (metadata.getFileSize() != null) {
            return metadata.getFileSize() / avgBytesPerRow;
        }

        return 1000; // Значение по умолчанию
    }

    /**
     * Выполняет только валидацию без сохранения
     */
    private void validateOnly(ImportSession session, ImportTemplate template, FileMetadata metadata) {
        log.info("Режим валидации для сессии ID: {}", session.getId());

        session.setStatus(ImportStatus.VALIDATING);
        sessionRepository.save(session);
        progressService.sendProgressUpdate(session);

        // TODO: Реализовать валидацию без сохранения
        // Прочитать файл, применить трансформации, проверить на ошибки
        // но не сохранять в БД

        session.setStatus(ImportStatus.COMPLETED);
        session.setCompletedAt(ZonedDateTime.now());
        sessionRepository.save(session);
        progressService.sendCompletionNotification(session);
    }

    /**
     * Отменяет выполняющийся импорт
     */
    @Transactional
    public boolean cancelImport(Long sessionId) {
        log.info("Запрос на отмену импорта для сессии ID: {}", sessionId);

        return sessionRepository.findById(sessionId)
                .map(session -> {
                    if (session.getStatus() == ImportStatus.PROCESSING ||
                            session.getStatus() == ImportStatus.INITIALIZING ||
                            session.getStatus() == ImportStatus.ANALYZING) {

                        // Отправляем сигнал отмены процессору
                        processorService.cancelImport(sessionId);

                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }
}