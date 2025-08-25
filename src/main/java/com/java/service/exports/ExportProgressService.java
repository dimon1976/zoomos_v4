package com.java.service.exports;

import com.java.controller.ExportProgressController;
import com.java.dto.ExportProgressDto;
import com.java.model.FileOperation;
import com.java.model.entity.ExportSession;
import com.java.model.enums.ExportStatus;
import com.java.repository.FileOperationRepository;
import com.java.service.progress.BaseProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExportProgressService extends BaseProgressService<ExportSession, ExportProgressDto> {

    private final ExportProgressController progressController;

    public ExportProgressService(FileOperationRepository fileOperationRepository, 
                               ExportProgressController progressController) {
        super(fileOperationRepository);
        this.progressController = progressController;
    }

    // Реализация абстрактных методов BaseProgressService

    @Override
    protected ExportProgressDto buildProgressDto(ExportSession session) {
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

    @Override
    protected void sendWebSocketUpdate(Long operationId, ExportProgressDto progress) {
        progressController.sendProgressUpdate(operationId, progress);
    }

    @Override
    protected String buildCompletionMessage(ExportSession session) {
        if (session.getStatus() == ExportStatus.COMPLETED) {
            return String.format("Экспорт успешно завершен. Экспортировано %d записей.",
                    session.getExportedRows());
        } else if (session.getStatus() == ExportStatus.FAILED) {
            return "Экспорт завершился с ошибкой: " +
                    (session.getErrorMessage() != null ? session.getErrorMessage() : "Неизвестная ошибка");
        }
        return "Экспорт завершен.";
    }

    // Геттеры для извлечения данных
    
    @Override
    protected Long getSessionId(ExportSession session) {
        return session.getId();
    }

    @Override
    protected Long getOperationId(ExportSession session) {
        return session.getFileOperation().getId();
    }

    @Override
    protected FileOperation getFileOperation(ExportSession session) {
        return session.getFileOperation();
    }

    @Override
    protected Integer getProgressPercentage(ExportProgressDto progress) {
        return progress.getProgressPercentage();
    }

    @Override
    protected Integer getProcessedRecords(ExportProgressDto progress) {
        return progress.getExportedRows() != null ? progress.getExportedRows().intValue() : null;
    }

    @Override
    protected Integer getTotalRecords(ExportProgressDto progress) {
        return progress.getTotalRows() != null ? progress.getTotalRows().intValue() : null;
    }

    // Сеттеры для установки данных в DTO
    
    @Override
    protected void setUpdateType(ExportProgressDto progress, String updateType) {
        progress.setUpdateType(updateType);
    }

    @Override
    protected void setIsCompleted(ExportProgressDto progress, boolean isCompleted) {
        progress.setIsCompleted(isCompleted);
    }

    @Override
    protected void setMessage(ExportProgressDto progress, String message) {
        progress.setMessage(message);
    }

    // Приватные методы (оставляем как есть)

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

    private boolean isCompleted(ExportStatus status) {
        return status == ExportStatus.COMPLETED ||
                status == ExportStatus.FAILED ||
                status == ExportStatus.CANCELLED;
    }
}