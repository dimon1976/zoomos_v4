package com.java.controller;

import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.FileOperationRepository;
import com.java.repository.ImportSessionRepository;
import com.java.service.operations.OperationDeletionService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST контроллер для работы с операциями
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OperationsRestController {

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final ImportSessionRepository importSessionRepository;
    private final ExportSessionRepository exportSessionRepository;
    private final OperationDeletionService operationDeletionService;

    /**
     * Получение списка операций клиента с фильтрацией и пагинацией
     */
    @GetMapping("/clients/{clientId}/operations")
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

        // Batch-конвертация операций в DTO (оптимизация N+1 запросов)
        List<FileOperationDto> operations = toDtoList(operationsList);

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
    @GetMapping("/operations/{operationId}/status")
    public FileOperationDto getOperationStatus(@PathVariable Long operationId) {
        log.debug("Запрос статуса операции ID: {}", operationId);

        return fileOperationRepository.findById(operationId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));
    }

    /**
     * Удаление операции и связанных данных
     */
    @DeleteMapping("/operations/{operationId}")
    public void deleteOperation(@PathVariable Long operationId) {
        log.info("Запрос на удаление операции ID: {}", operationId);
        
        // Используем новый сервис для полного удаления операции со всеми данными
        operationDeletionService.deleteOperationCompletely(operationId);
    }

    /**
     * Получение статистики предстоящего удаления операции
     */
    @GetMapping("/operations/{operationId}/deletion-stats")
    public OperationDeletionService.DeletionStatistics getDeletionStatistics(@PathVariable Long operationId) {
        log.debug("Запрос статистики удаления для операции ID: {}", operationId);
        return operationDeletionService.getDeletionStatistics(operationId);
    }

    /**
     * Batch-конвертация списка операций в DTO (оптимизация N+1 запросов)
     */
    private List<FileOperationDto> toDtoList(List<FileOperation> operations) {
        if (operations.isEmpty()) {
            return List.of();
        }

        // Разделяем операции на import и export
        List<Long> importOperationIds = operations.stream()
                .filter(op -> op.getOperationType() == FileOperation.OperationType.IMPORT)
                .map(FileOperation::getId)
                .collect(Collectors.toList());

        List<Long> exportOperationIds = operations.stream()
                .filter(op -> op.getOperationType() == FileOperation.OperationType.EXPORT)
                .map(FileOperation::getId)
                .collect(Collectors.toList());

        // Batch-загрузка сессий с шаблонами (2 запроса вместо N)
        Map<Long, String> templateNameByOperationId = new HashMap<>();

        if (!importOperationIds.isEmpty()) {
            importSessionRepository.findByFileOperationIdInWithTemplate(importOperationIds)
                    .forEach(session -> templateNameByOperationId.put(
                            session.getFileOperation().getId(),
                            session.getTemplate().getName()
                    ));
        }

        if (!exportOperationIds.isEmpty()) {
            exportSessionRepository.findByFileOperationIdInWithTemplate(exportOperationIds)
                    .forEach(session -> templateNameByOperationId.put(
                            session.getFileOperation().getId(),
                            session.getTemplate().getName()
                    ));
        }

        // Конвертируем операции в DTO используя загруженные данные
        return operations.stream()
                .map(operation -> toDto(operation, templateNameByOperationId.get(operation.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Преобразование Entity в DTO (для единичного случая)
     */
    private FileOperationDto toDto(FileOperation operation) {
        // Получаем имя шаблона в зависимости от типа операции
        String templateName = null;
        if (operation.getOperationType() == FileOperation.OperationType.IMPORT) {
            templateName = importSessionRepository.findByFileOperationIdWithTemplate(operation.getId())
                    .map(session -> session.getTemplate().getName())
                    .orElse(null);
        } else if (operation.getOperationType() == FileOperation.OperationType.EXPORT) {
            templateName = exportSessionRepository.findByFileOperationIdWithTemplate(operation.getId())
                    .map(session -> session.getTemplate().getName())
                    .orElse(null);
        }

        return toDto(operation, templateName);
    }

    /**
     * Преобразование Entity в DTO с заданным именем шаблона
     */
    private FileOperationDto toDto(FileOperation operation, String templateName) {
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
                .templateName(templateName)
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
        private String templateName;
    }

    /**
     * Получение общей статистики клиента
     */
    @GetMapping("/clients/{clientId}/statistics/general")
    public GeneralStatsDto getClientGeneralStats(@PathVariable Long clientId) {
        log.debug("Запрос общей статистики для клиента ID: {}", clientId);

        // Проверяем существование клиента
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Подсчитываем статистику
        long totalImports = fileOperationRepository.countByClientAndOperationType(client, FileOperation.OperationType.IMPORT);
        long totalExports = fileOperationRepository.countByClientAndOperationType(client, FileOperation.OperationType.EXPORT);
        long totalRecords = fileOperationRepository.sumProcessedRecordsByClient(client);
        long activeOperations = fileOperationRepository.countByClientAndStatus(client, FileOperation.OperationStatus.PROCESSING);

        return GeneralStatsDto.builder()
                .totalImports(totalImports)
                .totalExports(totalExports)
                .totalRecords(totalRecords)
                .activeOperations(activeOperations)
                .build();
    }

    /**
     * Получение статистики по статусам операций
     */
    @GetMapping("/clients/{clientId}/statistics/status")
    public java.util.Map<String, Long> getClientStatusStats(@PathVariable Long clientId) {
        log.debug("Запрос статистики по статусам для клиента ID: {}", clientId);

        // Проверяем существование клиента
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Подсчитываем статистику по статусам
        java.util.Map<String, Long> statusStats = new java.util.HashMap<>();
        for (FileOperation.OperationStatus status : FileOperation.OperationStatus.values()) {
            long count = fileOperationRepository.countByClientAndStatus(client, status);
            if (count > 0) {
                statusStats.put(status.name(), count);
            }
        }

        return statusStats;
    }

    /**
     * DTO общей статистики
     */
    @Data
    @Builder
    public static class GeneralStatsDto {
        private long totalImports;
        private long totalExports;
        private long totalRecords;
        private long activeOperations;
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
}