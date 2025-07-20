package com.java.controller;

import com.java.dto.ImportProgressDto;
import com.java.dto.ImportSessionDto;
import com.java.mapper.ImportSessionMapper;
import com.java.repository.ImportSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
     * Получение текущего статуса импорта
     */
    @GetMapping("/progress/{sessionId}")
    public ImportSessionDto getImportProgress(@PathVariable Long sessionId) {
        log.debug("Запрос прогресса для сессии ID: {}", sessionId);

        return sessionRepository.findById(sessionId)
                .map(ImportSessionMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));
    }

    /**
     * WebSocket endpoint для подписки на обновления
     */
    @MessageMapping("/import/subscribe")
    @SendTo("/topic/import-progress")
    public ImportProgressDto subscribeToProgress(Long operationId) {
        log.debug("Подписка на прогресс операции ID: {}", operationId);

        // Находим текущую сессию
        return sessionRepository.findByFileOperationId(operationId)
                .map(session -> {
                    return ImportProgressDto.builder()
                            .sessionId(session.getId())
                            .status(session.getStatus())
                            .totalRows(session.getTotalRows())
                            .processedRows(session.getProcessedRows())
                            .successRows(session.getSuccessRows())
                            .errorRows(session.getErrorRows())
                            .progressPercentage(session.getProgressPercentage())
                            .build();
                })
                .orElse(null);
    }
}
