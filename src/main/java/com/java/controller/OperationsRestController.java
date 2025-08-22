package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.ExportStatisticsRepository;
import com.java.repository.FileOperationRepository;
import com.java.repository.ImportSessionRepository;
import com.java.service.imports.AsyncImportService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST контроллер для работы с операциями
 */
@RestController
@RequestMapping(UrlConstants.API_BASE)
@RequiredArgsConstructor
@Slf4j
public class OperationsRestController {

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final ImportSessionRepository importSessionRepository;
    private final ExportSessionRepository exportSessionRepository;
    private final ExportStatisticsRepository exportStatisticsRepository;
    private final AsyncImportService asyncImportService;

    /**
     * Получение списка операций клиента с фильтрацией и пагинацией
     */
    @GetMapping(UrlConstants.REL_CLIENT_OPERATIONS)
    public FileOperationPageDto getClientOperations(@PathVariable Long clientId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size,
                                                    @RequestParam(required = false) FileOperation.OperationType operationType,
                                                    @RequestParam(required = false) FileOperation.OperationStatus status,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("Запрос операций для клиента ID: {}", clientId);

        // Проверяем существование клиента
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Получаем последние операции клиента
        Specification<FileOperation> spec = (root, query, cb) -> cb.equal(root.get("client"), client);
        if (operationType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("operationType"), operationType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            ZonedDateTime fromDate = from.atStartOfDay(ZoneId.systemDefault());
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startedAt"), fromDate));
        }
        if (to != null) {
            ZonedDateTime toDate = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startedAt"), toDate));
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<FileOperation> operationsPage = fileOperationRepository.findAll(spec, pageRequest);

        List<FileOperation> operationsList = operationsPage.getContent();
        log.debug("Получено {} операций для клиента {}", operationsList.size(), clientId);
        operationsList.forEach(op ->
                log.debug("\tID: {} Статус: {} Прогресс: {}", op.getId(), op.getStatus(), op.getProcessingProgress()));

        List<FileOperationDto> operations = operationsList.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return FileOperationPageDto.builder()
                .operations(operations)
                .page(operationsPage.getNumber())
                .size(operationsPage.getSize())
                .totalElements(operationsPage.getTotalElements())
                .totalPages(operationsPage.getTotalPages())
                .build();
    }

    /**
     * Получение статуса операции
     */
    @GetMapping(UrlConstants.REL_OPERATION_STATUS)
    public FileOperationDto getOperationStatus(@PathVariable Long operationId) {
        log.debug("Запрос статуса операции ID: {}", operationId);

        return fileOperationRepository.findById(operationId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));
    }

    /**
     * Отмена операции
     */
    @PostMapping(UrlConstants.REL_OPERATION_CANCEL)
    public OperationResponse cancelOperation(@PathVariable Long operationId) {
        log.debug("Запрос на отмену операции ID: {}", operationId);

        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));

            if (operation.getStatus() != FileOperation.OperationStatus.PROCESSING 
                && operation.getStatus() != FileOperation.OperationStatus.PENDING) {
                return new OperationResponse(false, "Операция не может быть отменена в текущем статусе");
            }

            if (operation.getOperationType() == FileOperation.OperationType.IMPORT) {
                var sessionOpt = importSessionRepository.findByFileOperationId(operationId);
                if (sessionOpt.isPresent()) {
                    boolean cancelled = asyncImportService.cancelImport(sessionOpt.get().getId());
                    if (cancelled) {
                        return new OperationResponse(true, "Операция отменена");
                    } else {
                        return new OperationResponse(false, "Не удалось отменить операцию");
                    }
                }
            }

            // Для других типов операций - обновляем статус напрямую
            operation.markAsCancelled();
            fileOperationRepository.save(operation);
            
            return new OperationResponse(true, "Операция отменена");

        } catch (Exception e) {
            log.error("Ошибка отмены операции ID: {}", operationId, e);
            return new OperationResponse(false, "Ошибка отмены операции: " + e.getMessage());
        }
    }

    /**
     * Удаление операции и связанных данных
     */
    @DeleteMapping(UrlConstants.REL_OPERATION_DELETE)
    @Transactional
    public void deleteOperation(@PathVariable Long operationId) {
        log.debug("Удаление операции ID: {}", operationId);

        if (!fileOperationRepository.existsById(operationId)) {
            throw new IllegalArgumentException("Операция не найдена");
        }

        try {
            // Удаляем связанные данные импорта
            importSessionRepository.findByFileOperationId(operationId)
                    .ifPresent(importSessionRepository::delete);
            
            // Удаляем связанные данные экспорта с каскадным удалением статистик
            exportSessionRepository.findByFileOperationId(operationId)
                    .ifPresent(exportSession -> {
                        log.debug("Удаление экспорт-сессии ID: {} и связанных статистик", exportSession.getId());
                        
                        // Сначала удаляем все статистики этой сессии
                        exportStatisticsRepository.deleteByExportSessionId(exportSession.getId());
                        log.debug("Удалены статистики для экспорт-сессии ID: {}", exportSession.getId());
                        
                        // Затем удаляем саму сессию
                        exportSessionRepository.delete(exportSession);
                        log.debug("Удалена экспорт-сессия ID: {}", exportSession.getId());
                    });
            
            // Наконец удаляем файловую операцию
            fileOperationRepository.deleteById(operationId);
            log.debug("Удалена файловая операция ID: {}", operationId);
            
        } catch (Exception e) {
            log.error("Ошибка при удалении операции ID: {}", operationId, e);
            throw new RuntimeException("Ошибка удаления операции: " + e.getMessage(), e);
        }
    }

    /**
     * Преобразование Entity в DTO
     */
    private FileOperationDto toDto(FileOperation operation) {
        return FileOperationDto.builder()
                .id(operation.getId())
                .operationType(operation.getOperationType().name())
                .fileName(operation.getFileName())
                .fileType(operation.getFileType())
                .status(operation.getStatus().name())
                .recordCount(operation.getRecordCount())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .processingProgress(operation.getProcessingProgress())
                .startedAt(operation.getStartedAt())
                .completedAt(operation.getCompletedAt())
                .errorMessage(operation.getErrorMessage())
                .build();
    }

    /**
     * DTO для операции
     */
    @Data
    @Builder
    public static class FileOperationDto {
        private Long id;
        private String operationType;
        private String fileName;
        private String fileType;
        private String status;
        private Integer recordCount;
        private Integer totalRecords;
        private Integer processedRecords;
        private Integer processingProgress;
        private java.time.ZonedDateTime startedAt;
        private java.time.ZonedDateTime completedAt;
        private String errorMessage;
    }

    /**
     * DTO страницы операций
     */
    @Data
    @Builder
    public static class FileOperationPageDto {
        private java.util.List<FileOperationDto> operations;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /**
     * DTO ответа операции
     */
    @Data
    @lombok.AllArgsConstructor
    public static class OperationResponse {
        private boolean success;
        private String message;
    }
}