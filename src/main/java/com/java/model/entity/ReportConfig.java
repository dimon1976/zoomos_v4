package com.java.model.entity;

import com.java.model.Client;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"client", "outputColumns"})
public class ReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "source_url", columnDefinition = "TEXT", nullable = false)
    private String sourceUrl;

    @Column(name = "lookup_file_original_name")
    private String lookupFileOriginalName;

    @Column(name = "lookup_file_stored_path")
    private String lookupFileStoredPath;

    @Column(name = "lookup_file_format")
    private String lookupFileFormat;

    @Column(name = "lookup_file_delimiter")
    private String lookupFileDelimiter;

    @Column(name = "lookup_file_encoding")
    private String lookupFileEncoding;

    @Column(name = "detected_report_columns", columnDefinition = "TEXT")
    private String detectedReportColumns;

    @Column(name = "output_format", nullable = false)
    @Builder.Default
    private String outputFormat = "XLSX";

    @Column(name = "row_filter_expression", columnDefinition = "TEXT")
    private String rowFilterExpression;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<ReportOutputColumn> outputColumns = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
