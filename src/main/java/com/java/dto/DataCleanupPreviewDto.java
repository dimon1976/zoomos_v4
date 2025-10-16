package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/**
 * DTO для предпросмотра очистки данных (без фактического удаления)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupPreviewDto {

    /**
     * Дата отсечки
     */
    private LocalDateTime cutoffDate;

    /**
     * Количество записей к удалению по типам данных
     * Ключ - тип данных, Значение - количество записей
     */
    @Builder.Default
    private Map<String, Long> recordsToDelete = new HashMap<>();

    /**
     * Детализация по клиентам
     * Ключ - тип данных, Значение - Map<Имя клиента, Количество записей>
     */
    @Builder.Default
    private Map<String, Map<String, Long>> recordsByClient = new HashMap<>();

    /**
     * Оценка освобождаемого места в байтах
     */
    private long estimatedFreeSpaceBytes;

    /**
     * Оценка освобождаемого места (человекочитаемый формат)
     */
    private String formattedEstimatedSpace;

    /**
     * Предупреждения о потенциальных проблемах
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Общее количество записей к удалению
     */
    private long totalRecordsToDelete;

    /**
     * Информация о самых старых и новых записях
     */
    @Builder.Default
    private Map<String, DateRange> dateRanges = new HashMap<>();

    /**
     * Добавляет количество записей к удалению для типа данных
     */
    public void addRecordsToDelete(String entityType, long count) {
        recordsToDelete.put(entityType, count);
        totalRecordsToDelete += count;
    }

    /**
     * Добавляет предупреждение
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Диапазон дат для типа данных
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DateRange {
        private LocalDateTime oldestRecord;
        private LocalDateTime newestRecord;
    }
}
