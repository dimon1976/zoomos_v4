package com.java.service.error;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для распарсенной ошибки базы данных.
 * Содержит структурированную информацию об ошибке для понятного отображения пользователю.
 */
@Data
@Builder
public class ParsedDatabaseError {
    /**
     * Тип ошибки БД
     */
    private DatabaseErrorType type;

    /**
     * Имя колонки, в которой произошла ошибка
     * Например: "product_name", "competitor_url"
     */
    private String columnName;

    /**
     * Максимальная длина поля в БД
     * Например: 255 для VARCHAR(255)
     */
    private Integer maxLength;

    /**
     * Фактическая длина значения, которое пытались вставить
     * Например: 338 символов
     */
    private Integer actualLength;

    /**
     * Номер строки в батче, где произошла ошибка
     * Например: 132 из "Batch entry 132"
     */
    private Long rowNumber;

    /**
     * Имя constraint для других типов ошибок
     * Например: "unique_product_barcode"
     */
    private String constraintName;

    /**
     * Оригинальное техническое сообщение для логирования
     */
    private String originalMessage;
}
