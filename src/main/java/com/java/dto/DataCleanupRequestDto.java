package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO для запроса на очистку данных
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupRequestDto {

    /**
     * Дата, до которой удалять данные (включительно)
     * Все записи с датой <= cutoffDate будут удалены
     */
    private LocalDateTime cutoffDate;

    /**
     * Типы данных для очистки
     * Доступные значения: AV_DATA, IMPORT_SESSIONS, EXPORT_SESSIONS, IMPORT_ERRORS, FILE_OPERATIONS
     */
    private Set<String> entityTypes;

    /**
     * ID клиентов, данные которых НЕ должны быть удалены
     * Исключения для защиты важных данных
     */
    private Set<Long> excludedClientIds;

    /**
     * Размер порции для batch-удаления
     * По умолчанию 10000 записей
     */
    @Builder.Default
    private int batchSize = 10000;

    /**
     * Режим "сухого прогона" - только подсчет без удаления
     * true = показать что будет удалено, false = выполнить удаление
     */
    @Builder.Default
    private boolean dryRun = false;

    /**
     * Инициатор операции (имя пользователя или "system")
     */
    private String initiatedBy;

    /**
     * Уникальный идентификатор операции для WebSocket-уведомлений
     */
    private String operationId;
}
