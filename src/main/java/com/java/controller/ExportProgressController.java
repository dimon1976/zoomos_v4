package com.java.controller;

import com.java.dto.ExportProgressDto;
import com.java.dto.ExportSessionDto;
import com.java.mapper.ExportSessionMapper;
import com.java.repository.ExportSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class ExportProgressController {

    private final ExportSessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/progress/{sessionId}")
    public ExportSessionDto getExportProgress(@PathVariable Long sessionId) {
        log.debug("Запрос прогресса экспорта для сессии ID: {}", sessionId);

        return sessionRepository.findById(sessionId)
                .map(ExportSessionMapper::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Сессия экспорта не найдена"));
    }

    @GetMapping("/progress/operation/{operationId}")
    public ExportProgressDto getExportProgressByOperation(@PathVariable Long operationId) {
        log.debug("Запрос прогресса экспорта для операции ID: {}", operationId);

        return sessionRepository.findByFileOperationId(operationId)
                .map(session -> ExportProgressDto.builder()
                        .sessionId(session.getId())
                        .status(session.getStatus())
                        .totalRows(session.getTotalRows())
                        .exportedRows(session.getExportedRows())
                        .filteredRows(session.getFilteredRows())
                        .progressPercentage(calculateProgressPercentage(session))
                        .isCompleted(isCompleted(session.getStatus()))
                        .build())
                .orElse(null);
    }

    @SubscribeMapping("/topic/export-progress/{operationId}")
    public ExportProgressDto subscribeToOperationProgress(@DestinationVariable Long operationId) {
        log.debug("Подписка на прогресс экспорта операции ID: {}", operationId);
        return getExportProgressByOperation(operationId);
    }

    @MessageMapping("/export/progress/{operationId}")
    @SendTo("/topic/export-progress/{operationId}")
    public ExportProgressDto requestProgressUpdate(@DestinationVariable Long operationId) {
        log.debug("Запрос обновления прогресса экспорта для операции ID: {}", operationId);
        return getExportProgressByOperation(operationId);
    }

    public void sendProgressUpdate(Long operationId, ExportProgressDto progress) {
        String destination = "/topic/export-progress/" + operationId;
        log.debug("Отправка WebSocket сообщения экспорта на {}: прогресс {}%",
                destination, progress.getProgressPercentage());
        try {
            messagingTemplate.convertAndSend(destination, progress);
            log.debug("✓ WebSocket сообщение экспорта успешно отправлено");
        } catch (Exception e) {
            log.error("✗ Ошибка отправки WebSocket сообщения экспорта на {}", destination, e);
            throw e;
        }
    }

    private Integer calculateProgressPercentage(com.java.model.entity.ExportSession session) {
        switch (session.getStatus()) {
            case INITIALIZING:
                return 5;
            case PROCESSING:
                if (session.getTotalRows() != null && session.getTotalRows() > 0 && session.getExportedRows() != null) {
                    return Math.min(95, (int) ((session.getExportedRows() * 80) / session.getTotalRows()) + 15);
                }
                return 50;
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    private boolean isCompleted(com.java.model.enums.ExportStatus status) {
        return status == com.java.model.enums.ExportStatus.COMPLETED ||
                status == com.java.model.enums.ExportStatus.FAILED ||
                status == com.java.model.enums.ExportStatus.CANCELLED;
    }
}