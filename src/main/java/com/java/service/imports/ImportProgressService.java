package com.java.service.imports;

import com.java.controller.ImportProgressController;
import com.java.dto.ImportProgressDto;
import com.java.model.FileOperation;
import com.java.model.entity.ImportSession;
import com.java.model.enums.ImportStatus;
import com.java.repository.FileOperationRepository;
import com.java.service.progress.BaseProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * Сервис для отслеживания и уведомления о прогрессе импорта
 */
@Service
@Slf4j
public class ImportProgressService extends BaseProgressService<ImportSession, ImportProgressDto> {

    private final ImportProgressController progressController;

    public ImportProgressService(FileOperationRepository fileOperationRepository,
                               ImportProgressController progressController) {
        super(fileOperationRepository);
        this.progressController = progressController;
    }

    // Реализация абстрактных методов BaseProgressService

    @Override
    protected ImportProgressDto buildProgressDto(ImportSession session) {
        ImportProgressDto dto = ImportProgressDto.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .totalRows(session.getTotalRows())
                .processedRows(session.getProcessedRows())
                .successRows(session.getSuccessRows())
                .errorRows(session.getErrorRows())
                .progressPercentage(calculateProgressPercentage(session)) // Новый метод
                .isCompleted(isCompleted(session.getStatus()))
                .timestamp(System.currentTimeMillis())
                .updateType("PROGRESS")
                .build();

        dto.setCurrentOperation(getCurrentOperation(session));

        if (session.getStatus() == ImportStatus.PROCESSING) {
            dto.setEstimatedTimeRemaining(calculateRemainingTime(session));
        }

        return dto;
    }

    @Override
    protected void sendWebSocketUpdate(Long operationId, ImportProgressDto progress) {
        progressController.sendProgressUpdate(operationId, progress);
    }

    @Override
    protected String buildCompletionMessage(ImportSession session) {
        if (session.getStatus() == ImportStatus.COMPLETED) {
            if (session.getErrorRows() == 0) {
                return String.format("Импорт успешно завершен. Обработано %d записей.",
                        session.getSuccessRows());
            } else {
                return String.format("Импорт завершен. Успешно: %d, Ошибок: %d.",
                        session.getSuccessRows(), session.getErrorRows());
            }
        } else if (session.getStatus() == ImportStatus.FAILED) {
            return "Импорт завершился с ошибкой: " +
                    (session.getErrorMessage() != null ? session.getErrorMessage() : "Неизвестная ошибка");
        } else if (session.getStatus() == ImportStatus.CANCELLED) {
            return "Импорт был отменен пользователем.";
        }
        return "Импорт завершен.";
    }

    // Геттеры для извлечения данных
    
    @Override
    protected Long getSessionId(ImportSession session) {
        return session.getId();
    }

    @Override
    protected Long getOperationId(ImportSession session) {
        return session.getFileOperation().getId();
    }

    @Override
    protected FileOperation getFileOperation(ImportSession session) {
        return session.getFileOperation();
    }

    @Override
    protected Integer getProgressPercentage(ImportProgressDto progress) {
        return progress.getProgressPercentage();
    }

    @Override
    protected Integer getProcessedRecords(ImportProgressDto progress) {
        return progress.getProcessedRows() != null ? progress.getProcessedRows().intValue() : null;
    }

    @Override
    protected Integer getTotalRecords(ImportProgressDto progress) {
        return progress.getTotalRows() != null ? progress.getTotalRows().intValue() : null;
    }

    // Сеттеры для установки данных в DTO
    
    @Override
    protected void setUpdateType(ImportProgressDto progress, String updateType) {
        progress.setUpdateType(updateType);
    }

    @Override
    protected void setIsCompleted(ImportProgressDto progress, boolean isCompleted) {
        progress.setIsCompleted(isCompleted);
    }

    @Override
    protected void setMessage(ImportProgressDto progress, String message) {
        progress.setMessage(message);
    }

    // Приватные методы (оставляем как есть)

    /**
     * Вычисляет прогресс с учетом того, что totalRows может быть оценкой
     */
    private Integer calculateProgressPercentage(ImportSession session) {
        if (session.getTotalRows() == null || session.getTotalRows() == 0 ||
                session.getProcessedRows() == null) {
            return 0;
        }

        int baseProgress = (int) Math.min(100, (session.getProcessedRows() * 100) / session.getTotalRows());

        // Если это оценка и мы близки к 100%, ограничиваем до 95%
        if (Boolean.TRUE.equals(session.getIsEstimated()) && baseProgress > 95) {
            return 95;
        }

        return baseProgress;
    }

    /**
     * Определяет текущую операцию
     */
    private String getCurrentOperation(ImportSession session) {
        switch (session.getStatus()) {
            case INITIALIZING:
                return "Инициализация импорта...";
            case ANALYZING:
                return "Анализ файла...";
            case VALIDATING:
                return "Валидация данных...";
            case PROCESSING:
                if (session.getProcessedRows() != null && session.getTotalRows() != null) {
                    String progressText = String.format("Обработка данных (%d из %s строк)",
                            session.getProcessedRows(),
                            Boolean.TRUE.equals(session.getIsEstimated()) ?
                                    "~" + session.getTotalRows() : session.getTotalRows());
                    return progressText;
                }
                return "Обработка данных...";
            case COMPLETING:
                return "Завершение импорта...";
            case COMPLETED:
                return "Импорт завершен";
            case FAILED:
                return "Импорт завершился с ошибкой";
            case CANCELLED:
                return "Импорт отменен";
            default:
                return "Неизвестная операция";
        }
    }

    /**
     * Вычисляет примерное оставшееся время
     */
    private String calculateRemainingTime(ImportSession session) {
        if (session.getStartedAt() == null ||
                session.getProcessedRows() == null ||
                session.getProcessedRows() == 0 ||
                session.getTotalRows() == null) {
            return "Вычисление...";
        }

        Duration elapsed = Duration.between(session.getStartedAt(), ZonedDateTime.now());
        double rowsPerSecond = session.getProcessedRows() / (double) elapsed.getSeconds();

        if (rowsPerSecond == 0) {
            return "Вычисление...";
        }

        long remainingRows = session.getTotalRows() - session.getProcessedRows();
        long remainingSeconds = (long) (remainingRows / rowsPerSecond);

        return formatDuration(Duration.ofSeconds(remainingSeconds));
    }

    /**
     * Форматирует продолжительность
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d мин %d сек", minutes, seconds);
        } else {
            return String.format("%d сек", seconds);
        }
    }

    /**
     * Проверяет, завершен ли импорт
     */
    private boolean isCompleted(ImportStatus status) {
        return status == ImportStatus.COMPLETED ||
                status == ImportStatus.FAILED ||
                status == ImportStatus.CANCELLED;
    }
}