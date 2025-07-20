package com.java.model.entity;

import com.java.model.Client;
import com.java.model.enums.DuplicateStrategy;
import com.java.model.enums.EntityType;
import com.java.model.enums.ErrorStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Шаблон импорта для маппинга полей файла на поля сущности
 */
@Entity
@Table(name = "import_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_strategy", nullable = false)
    private DuplicateStrategy duplicateStrategy = DuplicateStrategy.ALLOW_ALL;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_strategy", nullable = false)
    private ErrorStrategy errorStrategy = ErrorStrategy.CONTINUE_ON_ERROR;

    @Column(name = "file_type")
    private String fileType; // CSV, XLSX, etc.

    @Column(name = "delimiter")
    private String delimiter; // для CSV

    @Column(name = "encoding")
    private String encoding;

    @Column(name = "skip_header_rows")
    private Integer skipHeaderRows = 1;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("columnIndex ASC")
    @Default
    private List<ImportTemplateField> fields = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Вспомогательные методы
    public void addField(ImportTemplateField field) {
        fields.add(field);
        field.setTemplate(this);
    }

    public void removeField(ImportTemplateField field) {
        fields.remove(field);
        field.setTemplate(null);
    }
}