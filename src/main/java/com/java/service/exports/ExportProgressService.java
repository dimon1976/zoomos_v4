package com.java.service.exports;

import com.java.controller.ExportProgressController;
import com.java.dto.ExportProgressDto;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.model.enums.ExportStatus;
import com.java.repository.FileOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportProgressService {

    private final ExportProgressController progressController;
    private final FileOperationRepository fileOperationRepository;
    private final ConcurrentHashMap<Long, ZonedDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    @Transactional
    public void sendProgressUpdate(ExportSession session) {
        log.debug("=== Отправка обновления прогресса экспорта для сессии {} ===", session.getId());

        if (!shouldSendUpdate(session.getId())) {
            log.trace("Пропуск обновления - слишком частые вызовы");
            return;
        }

        ExportProgressDto progress = buildProgressDto(session);
        log.debug("Создан DTO прогресса экспорта: {}% ({}/{})",
                progress.getProgressPercentage(),
                progress.getExportedRows(),
                progress.getTotalRows());

        // Обновляем связанную операцию
        FileOperation operation = session.getFileOperation();
        operation.setProcessingProgress(progress.getProgressPercentage());
        if (progress.getExportedRows() != null) {
            operation.setProcessedRecords(progress.getExportedRows().intValue());
        }
        if (progress.getTotalRows() != null) {
            operation.setTotalRecords(progress.getTotalRows().intValue());
        }

        fileOperationRepository.saveAndFlush(operation);

        Long operationId = session.getFileOperation().getId();
        log.debug("Отправка через WebSocket для операции экспорта ID: {}", operationId);

        try {
            progressController.sendProgressUpdate(operationId, progress);
            log.info("✓ WebSocket обновление экспорта отправлено для сессии {}: {}%",
                    session.getId(), progress.getProgressPercentage());
        } catch (Exception e) {
            log.error("✗ Ошибка отправки WebSocket обновления экспорта для сессии {}", session.getId(), e);
        }
    }

    @Transactional
    public void sendCompletionNotification(ExportSession session) {
        ExportProgressDto progress = buildProgressDto(session);
        progress.setUpdateType("COMPLETED");
        progress.setIsCompleted(true);

        String message = buildCompletionMessage(session);
        progress.setMessage(message);

        Long operationId = session.getFileOperation().getId();
        progressController.sendProgressUpdate(operationId, progress);

        lastUpdateTimes.remove(session.getId());
        log.info("Отправлено уведомление о завершении экспорта для сессии {}", session.getId());
    }

    @Transactional
    public void sendErrorNotification(ExportSession session, String errorMessage) {
        ExportProgressDto progress = buildProgressDto(session);
        progress.setUpdateType("ERROR");
        progress.setMessage(errorMessage);

        Long operationId = session.getFileOperation().getId();
        progressController.sendProgressUpdate(operationId, progress);

        lastUpdateTimes.remove(session.getId());
        log.error("Отправлено уведомление об ошибке экспорта для сессии {}: {}",
                session.getId(), errorMessage);
    }

    private ExportProgressDto buildProgressDto(ExportSession session) {
        return ExportProgressDto.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .totalRows(session.getTotalRows())
                .exportedRows(session.getExportedRows())
                .filteredRows(session.getFilteredRows())
                .progressPercentage(calculateProgressPercentage(session))
                .isCompleted(isCompleted(session.getStatus()))
                .timestamp(System.currentTimeMillis())
                .updateType("PROGRESS")
                .currentOperation(getCurrentOperation(session))
                .build();
    }

    private Integer calculateProgressPercentage(ExportSession session) {
        // Базовый расчет по статусу
        switch (session.getStatus()) {
            case INITIALIZING:
                return 5;
            case PROCESSING:
                // Промежуточные значения в зависимости от этапа
                if (session.getTotalRows() != null && session.getTotalRows() > 0) {
                    if (session.getExportedRows() != null) {
                        return Math.min(95, (int) ((session.getExportedRows() * 80) / session.getTotalRows()) + 15);
                    }
                    return 50; // Середина обработки
                }
                return 30;
            case COMPLETED:
                return 100;
            case FAILED:
            case CANCELLED:
                return 0;
            default:
                return 0;
        }
    }

    private String getCurrentOperation(ExportSession session) {
        switch (session.getStatus()) {
            case INITIALIZING:
                return "Инициализация экспорта...";
            case PROCESSING:
                if (session.getExportedRows() != null && session.getTotalRows() != null) {
                    return String.format("Экспорт данных (%d из %d строк)",
                            session.getExportedRows(), session.getTotalRows());
                }
                return "Обработка данных...";
            case COMPLETED:
                return "Экспорт завершен";
            case FAILED:
                return "Экспорт завершился с ошибкой";
            case CANCELLED:
                return "Экспорт отменен";
            default:
                return "Неизвестная операция";
        }
    }

    private String buildCompletionMessage(ExportSession session) {
        if (session.getStatus() == ExportStatus.COMPLETED) {
            return String.format("Экспорт успешно завершен. Экспортировано %d записей.",
                    session.getExportedRows());
        } else if (session.getStatus() == ExportStatus.FAILED) {
            return "Экспорт завершился с ошибкой: " +
                    (session.getErrorMessage() != null ? session.getErrorMessage() : "Неизвестная ошибка");
        }
        return "Экспорт завершен.";
    }

    private boolean shouldSendUpdate(Long sessionId) {
        ZonedDateTime lastUpdate = lastUpdateTimes.get(sessionId);
        ZonedDateTime now = ZonedDateTime.now();

        if (lastUpdate == null || Duration.between(lastUpdate, now).getSeconds() >= 1) {
            lastUpdateTimes.put(sessionId, now);
            return true;
        }
        return false;
    }

    private boolean isCompleted(ExportStatus status) {
        return status == ExportStatus.COMPLETED ||
                status == ExportStatus.FAILED ||
                status == ExportStatus.CANCELLED;
    }
}