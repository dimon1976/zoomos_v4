package com.java.service.imports;

import com.java.dto.ImportProgressDto;
import com.java.mapper.ImportSessionMapper;
import com.java.model.entity.ImportSession;
import com.java.model.enums.ImportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для отслеживания и уведомления о прогрессе импорта
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    // Кеш для отслеживания времени последнего обновления
    private final ConcurrentHashMap<Long, ZonedDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    /**
     * Отправляет обновление прогресса через WebSocket
     */
    public void sendProgressUpdate(ImportSession session) {
        // Ограничиваем частоту обновлений (не чаще раза в секунду)
        if (!shouldSendUpdate(session.getId())) {
            return;
        }

        ImportProgressDto progress = buildProgressDto(session);

        // Отправляем обновление конкретному клиенту
        String destination = "/topic/import-progress/" + session.getFileOperation().getId();
        messagingTemplate.convertAndSend(destination, progress);

        log.debug("Отправлено обновление прогресса для сессии {}: {}%",
                session.getId(), progress.getProgressPercentage());
    }

    /**
     * Отправляет уведомление о завершении импорта
     */
    public void sendCompletionNotification(ImportSession session) {
        ImportProgressDto progress = buildProgressDto(session);
        progress.setUpdateType("COMPLETED");
        progress.setIsCompleted(true);

        String message = buildCompletionMessage(session);
        progress.setMessage(message);

        // Отправляем финальное обновление
        String destination = "/topic/import-progress/" + session.getFileOperation().getId();
        messagingTemplate.convertAndSend(destination, progress);

        // Также отправляем общее уведомление
        messagingTemplate.convertAndSend("/topic/notifications", progress);

        // Очищаем кеш
        lastUpdateTimes.remove(session.getId());

        log.info("Отправлено уведомление о завершении импорта для сессии {}", session.getId());
    }

    /**
     * Отправляет уведомление об ошибке
     */
    public void sendErrorNotification(ImportSession session, String errorMessage) {
        ImportProgressDto progress = buildProgressDto(session);
        progress.setUpdateType("ERROR");
        progress.setMessage(errorMessage);

        String destination = "/topic/import-progress/" + session.getFileOperation().getId();
        messagingTemplate.convertAndSend(destination, progress);

        // Очищаем кеш
        lastUpdateTimes.remove(session.getId());

        log.error("Отправлено уведомление об ошибке для сессии {}: {}",
                session.getId(), errorMessage);
    }

    /**
     * Создает DTO прогресса из сессии
     */
    private ImportProgressDto buildProgressDto(ImportSession session) {
        ImportProgressDto dto = ImportProgressDto.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .totalRows(session.getTotalRows())
                .processedRows(session.getProcessedRows())
                .successRows(session.getSuccessRows())
                .errorRows(session.getErrorRows())
                .progressPercentage(session.getProgressPercentage())
                .isCompleted(isCompleted(session.getStatus()))
                .timestamp(System.currentTimeMillis())
                .updateType("PROGRESS")
                .build();

        // Вычисляем текущую операцию
        dto.setCurrentOperation(getCurrentOperation(session));

        // Вычисляем оставшееся время
        if (session.getStatus() == ImportStatus.PROCESSING) {
            dto.setEstimatedTimeRemaining(calculateRemainingTime(session));
        }

        return dto;
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
                    return String.format("Обработка данных (%d из %d строк)",
                            session.getProcessedRows(), session.getTotalRows());
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
     * Создает сообщение о завершении
     */
    private String buildCompletionMessage(ImportSession session) {
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

    /**
     * Проверяет, нужно ли отправлять обновление
     */
    private boolean shouldSendUpdate(Long sessionId) {
        ZonedDateTime lastUpdate = lastUpdateTimes.get(sessionId);
        ZonedDateTime now = ZonedDateTime.now();

        if (lastUpdate == null || Duration.between(lastUpdate, now).getSeconds() >= 1) {
            lastUpdateTimes.put(sessionId, now);
            return true;
        }

        return false;
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
