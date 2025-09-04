package com.java.dto.utils;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * DTO для конфигурации утилиты поиска финальных ссылок после редиректов
 */
@Data
public class RedirectFinderDto {
    
    @NotNull(message = "Колонка с URL обязательна")
    @Min(value = 0, message = "Номер колонки должен быть положительным")
    private Integer urlColumn;
    
    @Min(value = 0, message = "Номер колонки должен быть положительным")
    private Integer idColumn;
    
    @Min(value = 0, message = "Номер колонки должен быть положительным") 
    private Integer modelColumn;
    
    @Min(value = 1, message = "Максимум редиректов должен быть больше 0")
    @Max(value = 20, message = "Максимум редиректов не может превышать 20")
    private Integer maxRedirects = 5;
    
    @Min(value = 1000, message = "Таймаут должен быть минимум 1 секунда")
    @Max(value = 60000, message = "Таймаут не может превышать 60 секунд")
    private Integer timeoutMs = 10000;
    
    private Boolean usePlaywright = false;
    
    private String outputFormat = "csv";
    
    // Конструктор с умолчаниями для удобства
    public RedirectFinderDto() {
        this.maxRedirects = 5;
        this.timeoutMs = 10000; 
        this.usePlaywright = false;
        this.outputFormat = "csv";
    }
}