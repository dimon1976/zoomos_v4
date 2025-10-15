package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO с результатами очистки данных
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupResultDto {

    /**
     * Время выполнения очистки
     */
    private LocalDateTime cleanupTime;

    /**
     * Дата отсечки - данные до этой даты были удалены
     */
    private LocalDateTime cutoffDate;

    /**
     * Количество удаленных записей по типам данных
     * Ключ - тип данных (AV_DATA, IMPORT_SESSIONS и т.д.)
     * Значение - количество удаленных записей
     */
    @Builder.Default
    private Map<String, Long> deletedRecordsByType = new HashMap<>();

    /**
     * Общее количество удаленных записей
     */
    private long totalRecordsDeleted;

    /**
     * Освобождено места в байтах (примерная оценка)
     */
    private long freedSpaceBytes;

    /**
     * Освобожденное место в человекочитаемом формате
     */
    private String formattedFreedSpace;

    /**
     * Время выполнения операции в миллисекундах
     */
    private long executionTimeMs;

    /**
     * Успешность операции
     */
    private boolean success;

    /**
     * Сообщение об ошибке (если success = false)
     */
    private String errorMessage;

    /**
     * Размер использованной порции для batch-удаления
     */
    private int batchSize;

    /**
     * ID исключенных клиентов
     */
    private String excludedClientIds;

    /**
     * Был ли это dry run (без фактического удаления)
     */
    private boolean dryRun;

    /**
     * Добавляет количество удаленных записей для типа данных
     */
    public void addDeletedRecords(String entityType, long count) {
        deletedRecordsByType.put(entityType, count);
        totalRecordsDeleted += count;
    }
}
