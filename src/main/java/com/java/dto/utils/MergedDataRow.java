package com.java.dto.utils;

import lombok.Data;

@Data
public class MergedDataRow {
    private String id;              // ID (из исходных)
    private String clientModel;     // Модель клиента (из исходных)
    private String analogModel;     // Модель аналога (из исходных)
    private String coefficient;     // Коэффициент (из исходных)
    private String link;           // Одна ссылка на строку (построчная выгрузка)
}