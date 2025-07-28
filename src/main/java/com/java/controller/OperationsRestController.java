package com.java.controller;

import com.java.model.Client;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.FileOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Получение списка операций клиента
     */
    @GetMapping("/clients/{clientId}/operations")
    public List<FileOperationDto> getClientOperations(@PathVariable Long clientId,
                                                      @RequestParam(defaultValue = "50") int limit) {
        log.debug("Запрос операций для клиента ID: {}", clientId);

        // Проверяем существование клиента
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));

        // Получаем последние операции клиента
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startedAt"));
        List<FileOperation> operations = fileOperationRepository.findByClient(client, pageRequest).getContent();

        return operations.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
    @lombok.Data
    @lombok.Builder
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
}