package com.java.dto.utils;

import lombok.Data;

@Data
public class SourceDataRow {
    private String id;              // ID
    private String clientModel;     // Модель клиента
    private String analogModel;     // Модель аналога
    private String coefficient;     // Коэффициент
}