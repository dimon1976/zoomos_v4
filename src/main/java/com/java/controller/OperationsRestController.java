package com.java.controller;

import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.ExportSessionRepository;
import com.java.repository.FileOperationRepository;
import com.java.repository.ImportSessionRepository;
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
import java.util.List;
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

        List<FileOperationDto> operations = operationsPage.getContent().stream()
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
        log.debug("Удаление операции ID: {}", operationId);

        if (!fileOperationRepository.existsById(operationId)) {
            throw new IllegalArgumentException("Операция не найдена");
        }

        importSessionRepository.findByFileOperationId(operationId)
                .ifPresent(importSessionRepository::delete);
        exportSessionRepository.findByFileOperationId(operationId)
                .ifPresent(exportSessionRepository::delete);
        fileOperationRepository.deleteById(operationId);
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
}