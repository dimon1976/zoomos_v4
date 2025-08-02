package com.java.model.entity;

import com.java.model.FileOperation;
import com.java.model.enums.ExportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "export_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_operation_id", nullable = false)
    private FileOperation fileOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ExportTemplate template;

    @Column(name = "source_operation_ids", nullable = false, columnDefinition = "TEXT")
    private String sourceOperationIds; // JSON array

    @Column(name = "date_filter_from")
    private ZonedDateTime dateFilterFrom;

    @Column(name = "date_filter_to")
    private ZonedDateTime dateFilterTo;

    @Column(name = "applied_filters", columnDefinition = "TEXT")
    private String appliedFilters; // JSON

    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "exported_rows")
    @Builder.Default
    private Long exportedRows = 0L;

    @Column(name = "filtered_rows")
    @Builder.Default
    private Long filteredRows = 0L;

    @Column(name = "modified_rows")
    @Builder.Default
    private Long modifiedRows = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ExportStatus status = ExportStatus.INITIALIZING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_file_path")
    private String resultFilePath;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Transient
    public Integer getProgressPercentage() {
        if (totalRows == null || totalRows == 0) return 0;
        return (int) ((exportedRows * 100.0) / totalRows);
    }
}