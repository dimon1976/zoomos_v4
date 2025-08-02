package com.java.model.entity;

import com.java.model.Client;
import com.java.model.enums.EntityType;
import com.java.model.enums.ExportStrategy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "export_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"client", "fields", "filters"})
public class ExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
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
    @Column(name = "export_strategy", nullable = false)
    @Builder.Default
    private ExportStrategy exportStrategy = ExportStrategy.DEFAULT;

    @Column(name = "file_format", nullable = false)
    @Builder.Default
    private String fileFormat = "CSV";

    // CSV настройки
    @Column(name = "csv_delimiter")
    @Builder.Default
    private String csvDelimiter = ";";

    @Column(name = "csv_encoding")
    @Builder.Default
    private String csvEncoding = "UTF-8";

    @Column(name = "csv_quote_char")
    @Builder.Default
    private String csvQuoteChar = "\"";

    @Column(name = "csv_include_header")
    @Builder.Default
    private Boolean csvIncludeHeader = true;

    // XLSX настройки
    @Column(name = "xlsx_sheet_name")
    @Builder.Default
    private String xlsxSheetName = "Данные";

    @Column(name = "xlsx_auto_size_columns")
    @Builder.Default
    private Boolean xlsxAutoSizeColumns = true;

    // Общие настройки
    @Column(name = "max_rows_per_file")
    private Integer maxRowsPerFile;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fieldOrder ASC")
    @Builder.Default
    private List<ExportTemplateField> fields = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExportTemplateFilter> filters = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Вспомогательные методы
    public void addField(ExportTemplateField field) {
        fields.add(field);
        field.setTemplate(this);
    }

    public void removeField(ExportTemplateField field) {
        fields.remove(field);
        field.setTemplate(null);
    }

    public void addFilter(ExportTemplateFilter filter) {
        filters.add(filter);
        filter.setTemplate(this);
    }

    public void removeFilter(ExportTemplateFilter filter) {
        filters.remove(filter);
        filter.setTemplate(null);
    }
}