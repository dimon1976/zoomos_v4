package com.java.controller;

import com.java.dto.ImportProgressDto;
import com.java.dto.ImportSessionDto;
import com.java.mapper.ImportSessionMapper;
import com.java.repository.ImportSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.*;

/**
 * WebSocket контроллер для отслеживания прогресса импорта
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
public class ImportProgressController {

    private final ImportSessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * REST endpoint - получение текущего статуса импорта
     */
    @GetMapping("/progress/{sessionId}")
    public ImportSessionDto getImportProgress(@PathVariable Long sessionId) {
        log.debug("Запрос прогресса для сессии ID: {}", sessionId);

        return sessionRepository.findById(sessionId)
                .map(ImportSessionMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));
    }

    /**
     * REST endpoint - получение прогресса по операции
     */
    @GetMapping("/progress/operation/{operationId}")
    public ImportProgressDto getImportProgressByOperation(@PathVariable Long operationId) {
        log.debug("Запрос прогресса для операции ID: {}", operationId);

        return sessionRepository.findByFileOperationId(operationId)
                .map(session -> ImportProgressDto.builder()
                        .sessionId(session.getId())
                        .status(session.getStatus())
                        .totalRows(session.getTotalRows())
                        .processedRows(session.getProcessedRows())
                        .successRows(session.getSuccessRows())
                        .errorRows(session.getErrorRows())
                        .progressPercentage(session.getProgressPercentage())
                        .isCompleted(isCompleted(session.getStatus()))
                        .build())
                .orElse(null);
    }

    /**
     * WebSocket endpoint - подписка на обновления конкретной операции
     * Клиент отправляет: SUBSCRIBE /topic/import-progress/{operationId}
     */
    @SubscribeMapping("/topic/import-progress/{operationId}")
    public ImportProgressDto subscribeToOperationProgress(@DestinationVariable Long operationId) {
        log.debug("Подписка на прогресс операции ID: {}", operationId);

        return getImportProgressByOperation(operationId);
    }

    /**
     * WebSocket endpoint - запрос обновления прогресса
     * Клиент отправляет сообщение на /app/import/progress/{operationId}
     */
    @MessageMapping("/import/progress/{operationId}")
    @SendTo("/topic/import-progress/{operationId}")
    public ImportProgressDto requestProgressUpdate(@DestinationVariable Long operationId) {
        log.debug("Запрос обновления прогресса для операции ID: {}", operationId);

        return getImportProgressByOperation(operationId);
    }

    /**
     * Программная отправка обновления прогресса
     * Используется из ImportProgressService
     */
    public void sendProgressUpdate(Long operationId, ImportProgressDto progress) {
        String destination = "/topic/import-progress/" + operationId;
        messagingTemplate.convertAndSend(destination, progress);
        log.trace("Отправлено обновление прогресса для операции {}", operationId);
    }

    /**
     * Проверяет, завершен ли импорт
     */
    private boolean isCompleted(com.java.model.enums.ImportStatus status) {
        return status == com.java.model.enums.ImportStatus.COMPLETED ||
                status == com.java.model.enums.ImportStatus.FAILED ||
                status == com.java.model.enums.ImportStatus.CANCELLED;
    }
}