package com.java.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.ExportSessionDto;
import com.java.dto.ExportTemplateFilterDto;
import com.java.model.entity.ExportSession;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ExportSessionMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ExportSessionMapper() {
        // Утилитный класс
    }

    /**
     * Entity -> DTO
     */
    public static ExportSessionDto toDto(ExportSession entity) {
        if (entity == null) return null;

        ExportSessionDto dto = ExportSessionDto.builder()
                .id(entity.getId())
                .fileOperationId(entity.getFileOperation() != null ?
                        entity.getFileOperation().getId() : null)
                .templateId(entity.getTemplate() != null ?
                        entity.getTemplate().getId() : null)
                .templateName(entity.getTemplate() != null ?
                        entity.getTemplate().getName() : null)
                .sourceOperationIds(parseOperationIds(entity.getSourceOperationIds()))
                .fileName(entity.getFileOperation() != null ?
                        entity.getFileOperation().getFileName() : null)
                .dateFilterFrom(entity.getDateFilterFrom())
                .dateFilterTo(entity.getDateFilterTo())
                .appliedFilters(parseFilters(entity.getAppliedFilters()))
                .totalRows(entity.getTotalRows())
                .exportedRows(entity.getExportedRows())
                .filteredRows(entity.getFilteredRows())
                .modifiedRows(entity.getModifiedRows())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .progressPercentage(entity.getProgressPercentage())
                .resultFilePath(entity.getResultFilePath())
                .fileSize(entity.getFileSize())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .build();

        // Вычисляем продолжительность
        if (entity.getStartedAt() != null && entity.getCompletedAt() != null) {
            dto.setDuration(calculateDuration(entity.getStartedAt(), entity.getCompletedAt()));
        }

        return dto;
    }

    /**
     * Парсит JSON массив ID операций
     */
    private static List<Long> parseOperationIds(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга ID операций", e);
            return new ArrayList<>();
        }
    }

    /**
     * Парсит JSON фильтров
     */
    private static List<ExportTemplateFilterDto> parseFilters(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        try {
            return objectMapper.readValue(json, new TypeReference<List<ExportTemplateFilterDto>>() {});
        } catch (Exception e) {
            log.error("Ошибка парсинга фильтров", e);
            return new ArrayList<>();
        }
    }

    /**
     * Вычисляет продолжительность
     */
    private static String calculateDuration(ZonedDateTime start, ZonedDateTime end) {
        Duration duration = Duration.between(start, end);
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
     * Список Entity -> список DTO
     */
    public static List<ExportSessionDto> toDtoList(List<ExportSession> entities) {
        return entities.stream()
                .map(ExportSessionMapper::toDto)
                .collect(Collectors.toList());
    }
}