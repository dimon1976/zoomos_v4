package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "export_template_filters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "template")
public class ExportTemplateFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ExportTemplate template;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "filter_type", nullable = false)
    private String filterType; // EQUALS, CONTAINS, BETWEEN, IN, etc.

    @Column(name = "filter_value", nullable = false, columnDefinition = "TEXT")
    private String filterValue; // JSON для разных типов

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}