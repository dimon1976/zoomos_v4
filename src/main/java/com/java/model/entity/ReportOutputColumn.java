package com.java.model.entity;

import com.java.model.enums.ReportOutputColumnType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "report_output_columns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "config")
public class ReportOutputColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private ReportConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReportOutputColumnType type;

    @Column(name = "output_header_name", nullable = false)
    private String outputHeaderName;

    @Column(name = "column_order", nullable = false)
    private Integer position;

    @Column(name = "included", nullable = false)
    @Builder.Default
    private Boolean included = true;

    @Column(name = "source_column_name")
    private String sourceColumnName;

    @Column(name = "formula", columnDefinition = "TEXT")
    private String formula;

    @Column(name = "key_column_in_report")
    private String keyColumnInReport;

    @Column(name = "key_column_in_lookup")
    private String keyColumnInLookup;

    @Column(name = "value_column_in_lookup")
    private String valueColumnInLookup;
}
