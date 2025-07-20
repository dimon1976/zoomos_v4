package com.java.service.imports;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

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
    private final PathResolver pathResolver;

    /**
     * Запускает асинхронный импорт файла
     */
    @Async("importTaskExecutor")
    @Transactional
    public CompletableFuture<ImportSession> startImport(ImportRequestDto request, Long clientId) {
        log.info("Запуск асинхронного импорта для клиента ID: {}", clientId);

        ImportSession session = null;
        FileOperation fileOperation = null;

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
            fileOperation = createFileOperation(request.getFile(), client);
            fileOperation = fileOperationRepository.save(fileOperation);

            // Создаем сессию импорта
            session = createImportSession(fileOperation, template);
            session = sessionRepository.save(session);

            // Анализируем файл
            log.info("Анализ файла: {}", request.getFile().getOriginalFilename());
            session.setStatus(ImportStatus.ANALYZING);
            sessionRepository.save(session);
            progressService.sendProgressUpdate(session);

            FileMetadata metadata = fileAnalyzerService.analyzeFile(request.getFile());
            metadata.setImportSession(session);
            metadata = metadataRepository.save(metadata);

            // Устанавливаем общее количество строк
            if (metadata.getSampleData() != null) {
                // Примерная оценка на основе размера файла и sample data
                long estimatedRows = estimateRowCount(metadata);
                session.setTotalRows(estimatedRows);
                sessionRepository.save(session);
            }

            // Применяем переопределения настроек если есть
            if (request.getDelimiter() != null) {
                metadata.setDetectedDelimiter(request.getDelimiter());
            }
            if (request.getEncoding() != null) {
                metadata.setDetectedEncoding(request.getEncoding());
            }

            // Запускаем обработку
            if (!request.getValidateOnly()) {
                processorService.processImport(session);
            } else {
                // Режим только валидации
                validateOnly(session, template, metadata);
            }

            log.info("Импорт завершен для сессии ID: {}", session.getId());
            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            log.error("Ошибка асинхронного импорта", e);

            if (session != null) {
                session.setStatus(ImportStatus.FAILED);
                session.setCompletedAt(ZonedDateTime.now());
                session.setErrorMessage(e.getMessage());
                sessionRepository.save(session);
                progressService.sendErrorNotification(session, e.getMessage());
            }

            if (fileOperation != null) {
                fileOperation.setStatus(FileOperation.OperationStatus.FAILED);
                fileOperation.setErrorMessage(e.getMessage());
                fileOperation.setCompletedAt(ZonedDateTime.now());
                fileOperationRepository.save(fileOperation);
            }

            return CompletableFuture.failedFuture(e);
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
