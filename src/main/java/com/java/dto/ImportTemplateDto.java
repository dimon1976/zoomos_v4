package com.java.dto;

import com.java.model.enums.DataSourceType;
import com.java.model.enums.DuplicateStrategy;
import com.java.model.enums.EntityType;
import com.java.model.enums.ErrorStrategy;
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
public class ImportTemplateDto {

    private Long id;

    @NotBlank(message = "Название шаблона обязательно")
    private String name;

    private String description;

    @NotNull(message = "Клиент обязателен")
    private Long clientId;

    private String clientName;

    @NotNull(message = "Тип сущности обязателен")
    private EntityType entityType;

    @NotNull(message = "Источник данных обязателен")
    @Builder.Default
    private DataSourceType dataSourceType = DataSourceType.FILE;

    private DuplicateStrategy duplicateStrategy = DuplicateStrategy.ALLOW_ALL;

    private ErrorStrategy errorStrategy = ErrorStrategy.CONTINUE_ON_ERROR;

    private String fileType;

    private String delimiter;

    private String encoding;

    private Integer skipHeaderRows = 1;

    private Boolean isActive = true;

    private List<ImportTemplateFieldDto> fields = new ArrayList<>();

    private ZonedDateTime createdAt;

    private ZonedDateTime updatedAt;

    // Статистика использования
    private Long usageCount;
    private ZonedDateTime lastUsedAt;
}