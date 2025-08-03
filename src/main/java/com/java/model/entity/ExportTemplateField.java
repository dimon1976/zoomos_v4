package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "export_template_fields")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "template")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExportTemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ExportTemplate template;

    @Column(name = "entity_field_name", nullable = false)
    private String entityFieldName;

    @Column(name = "export_column_name", nullable = false)
    private String exportColumnName;

    @Column(name = "field_order", nullable = false)
    private Integer fieldOrder;

    @Column(name = "is_included")
    @Builder.Default
    private Boolean isIncluded = true;

    @Column(name = "data_format")
    private String dataFormat; // Например: "dd.MM.yyyy" для дат

    @Column(name = "transformation_rule")
    private String transformationRule; // JSON с правилами
}