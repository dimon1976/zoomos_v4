package com.java.dto.handbook;

import lombok.Data;
import java.util.List;

@Data
public class BhSearchConfigDto {

    /** Колонка с идентификатором строки (обязательная) */
    private String idColumn;

    /** Колонка со штрихкодом (опциональная) */
    private String barcodeColumn;

    /** Колонка с наименованием (опциональная) */
    private String nameColumn;

    /** Колонка с брендом (опциональная, для уточнения совпадения) */
    private String brandColumn;

    /** Требовать совпадение бренда при поиске по наименованию */
    private boolean requireBrandMatch = false;

    /** Формат результирующего файла: CSV или XLSX */
    private String outputFormat = "XLSX";

    /** Разделитель для CSV */
    private String csvDelimiter = ";";

    /** Кодировка CSV */
    private String csvEncoding = "UTF-8";

    /** Список доменов для фильтрации URL (пустой = все домены) */
    private List<String> domainFilter;
}
