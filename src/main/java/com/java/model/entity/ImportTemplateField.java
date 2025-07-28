package com.java.model.entity;

import com.java.model.enums.FieldType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Поле шаблона импорта с маппингом и правилами трансформации
 */
@Entity
@Table(name = "import_template_fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "template")
public class ImportTemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ImportTemplate template;

    @Column(name = "column_name")
    private String columnName; // Название колонки в файле

    @Column(name = "column_index")
    private Integer columnIndex; // Индекс колонки (0-based)

    @Column(name = "entity_field_name", nullable = false)
    private String entityFieldName; // Имя поля в сущности

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type")
    private FieldType fieldType = FieldType.STRING;

    @Column(name = "is_required")
    private Boolean isRequired = false;

    @Column(name = "is_unique")
    private Boolean isUnique = false; // Для проверки дубликатов

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "date_format")
    private String dateFormat; // Формат даты для парсинга

    @Column(name = "transformation_rule")
    private String transformationRule; // Правило трансформации (JSON)

    @Column(name = "validation_regex")
    private String validationRegex;

    @Column(name = "validation_message")
    private String validationMessage;
}