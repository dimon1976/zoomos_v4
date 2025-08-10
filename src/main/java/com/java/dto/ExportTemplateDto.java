package com.java.dto;

import com.java.model.enums.EntityType;
import com.java.model.enums.ExportStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTemplateDto {

    private Long id;

    @NotBlank(message = "Название шаблона обязательно")
    private String name;

    private String description;

    @NotNull(message = "Клиент обязателен")
    private Long clientId;

    private String clientName;

    @NotNull(message = "Тип сущности обязателен")
    private EntityType entityType;

    @Builder.Default
    private ExportStrategy exportStrategy = ExportStrategy.DEFAULT;

    @NotBlank(message = "Формат файла обязателен")
    @Builder.Default
    private String fileFormat = "CSV";

    // CSV настройки
    @Builder.Default
    private String csvDelimiter = ";";

    @Builder.Default
    private String csvEncoding = "UTF-8";

    @Builder.Default
    private String csvQuoteChar = "\"";

    @Builder.Default
    private Boolean csvIncludeHeader = true;

    // XLSX настройки
    @Builder.Default
    private String xlsxSheetName = "Данные";

    @Builder.Default
    private Boolean xlsxAutoSizeColumns = true;

    private Integer maxRowsPerFile;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private List<ExportTemplateFieldDto> fields = new ArrayList<>();

    @Builder.Default
    private List<ExportTemplateFilterDto> filters = new ArrayList<>();

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    // Статистика использования
    private Long usageCount;
    private ZonedDateTime lastUsedAt;

    // Настройки статистики
    @Builder.Default
    private Boolean enableStatistics = false;

    @Builder.Default
    private List<String> statisticsCountFields = new ArrayList<>(); // Поля для подсчета

    private String statisticsGroupField; // Поле для группировки

    @Builder.Default
    private List<String> statisticsFilterFields = new ArrayList<>(); // Поля для фильтрации

    // Настройки именования файлов
    private String filenameTemplate;

    @Builder.Default
    private Boolean includeClientName = true;

    @Builder.Default
    private Boolean includeExportType = false;

    @Builder.Default
    private Boolean includeTaskNumber = false;

    private String exportTypeLabel;

    private String operationNameSource; // FILE_NAME, TASK_NUMBER, CUSTOM
}