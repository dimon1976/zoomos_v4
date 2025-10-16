package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для отслеживания прогресса очистки данных через WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupProgressDto {

    /**
     * Уникальный идентификатор операции очистки
     */
    private String operationId;

    /**
     * Тип данных который очищается в данный момент
     * Например: AV_DATA, IMPORT_SESSIONS, etc.
     */
    private String entityType;

    /**
     * Человекочитаемое сообщение о текущем состоянии
     */
    private String message;

    /**
     * Процент выполнения (0-100)
     */
    private Integer percentage;

    /**
     * Количество обработанных (удаленных) записей
     */
    private Long processedRecords;

    /**
     * Общее количество записей для удаления
     */
    private Long totalRecords;

    /**
     * Номер текущей итерации batch-удаления
     */
    private Integer currentIteration;

    /**
     * Скорость удаления (записей в секунду)
     */
    private Long recordsPerSecond;

    /**
     * Статус операции: IN_PROGRESS, COMPLETED, ERROR
     */
    private String status;

    /**
     * Время отправки уведомления
     */
    private LocalDateTime timestamp;

    /**
     * Сообщение об ошибке (если status = ERROR)
     */
    private String errorMessage;
}
