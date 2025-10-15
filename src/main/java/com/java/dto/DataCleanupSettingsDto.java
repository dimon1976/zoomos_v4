package com.java.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для настроек очистки данных
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCleanupSettingsDto {

    /**
     * ID настройки
     */
    private Long id;

    /**
     * Тип данных (AV_DATA, IMPORT_SESSIONS и т.д.)
     */
    private String entityType;

    /**
     * Количество дней хранения
     */
    private Integer retentionDays;

    /**
     * Включена ли автоматическая очистка
     */
    private Boolean autoCleanupEnabled;

    /**
     * Размер порции для batch-удаления
     */
    private Integer cleanupBatchSize;

    /**
     * Описание настройки
     */
    private String description;
}
