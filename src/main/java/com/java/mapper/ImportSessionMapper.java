package com.java.mapper;

import com.java.dto.ImportSessionDto;
import com.java.model.entity.ImportSession;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Маппер для преобразования ImportSession между Entity и DTO
 */
public class ImportSessionMapper {

    private ImportSessionMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> DTO
     */
    public static ImportSessionDto toDto(ImportSession entity) {
        if (entity == null) return null;

        ImportSessionDto dto = ImportSessionDto.builder()
                .id(entity.getId())
                .fileOperationId(entity.getFileOperation() != null ?
                        entity.getFileOperation().getId() : null)
                .templateId(entity.getTemplate() != null ?
                        entity.getTemplate().getId() : null)
                .templateName(entity.getTemplate() != null ?
                        entity.getTemplate().getName() : null)
                .fileName(entity.getFileOperation() != null ?
                        entity.getFileOperation().getFileName() : null)
                .totalRows(entity.getTotalRows())
                .processedRows(entity.getProcessedRows())
                .successRows(entity.getSuccessRows())
                .errorRows(entity.getErrorRows())
                .duplicateRows(entity.getDuplicateRows())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .progressPercentage(entity.getProgressPercentage())
                .isCancelled(entity.getIsCancelled())
                .build();

        // Вычисляем оставшееся время
        if (entity.getStatus().name().equals("PROCESSING") &&
                entity.getProcessedRows() != null &&
                entity.getTotalRows() != null &&
                entity.getProcessedRows() > 0) {

            dto.setEstimatedTimeRemaining(calculateEstimatedTime(entity));
        }

        return dto;
    }

    /**
     * Список Entity -> список DTO
     */
    public static List<ImportSessionDto> toDtoList(List<ImportSession> entities) {
        return entities.stream()
                .map(ImportSessionMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Вычисляет примерное оставшееся время
     */
    private static String calculateEstimatedTime(ImportSession session) {
        if (session.getStartedAt() == null ||
                session.getProcessedRows() == 0 ||
                session.getTotalRows() == 0) {
            return "Неизвестно";
        }

        Duration elapsed = Duration.between(session.getStartedAt(), ZonedDateTime.now());
        long elapsedSeconds = elapsed.getSeconds();

        double rowsPerSecond = (double) session.getProcessedRows() / elapsedSeconds;
        long remainingRows = session.getTotalRows() - session.getProcessedRows();
        long remainingSeconds = (long) (remainingRows / rowsPerSecond);

        return formatDuration(Duration.ofSeconds(remainingSeconds));
    }

    /**
     * Форматирует продолжительность
     */
    private static String formatDuration(Duration duration) {
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
}