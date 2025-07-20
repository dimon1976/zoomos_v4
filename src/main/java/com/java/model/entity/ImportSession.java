package com.java.model.entity;

import com.java.model.FileOperation;
import com.java.model.enums.ImportStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сессия импорта - отслеживает процесс импорта файла
 */
@Entity
@Table(name = "import_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_operation_id", nullable = false)
    private FileOperation fileOperation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ImportTemplate template;

    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "processed_rows")
    private Long processedRows = 0L;

    @Column(name = "success_rows")
    private Long successRows = 0L;

    @Column(name = "error_rows")
    private Long errorRows = 0L;

    @Column(name = "duplicate_rows")
    private Long duplicateRows = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status = ImportStatus.INITIALIZING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "is_cancelled")
    private Boolean isCancelled = false;

    @OneToMany(mappedBy = "importSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImportError> errors = new ArrayList<>();

    @OneToOne(mappedBy = "importSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private FileMetadata fileMetadata;

    // Вычисляемое поле для прогресса
    @Transient
    public Integer getProgressPercentage() {
        if (totalRows == null || totalRows == 0) return 0;
        return (int) ((processedRows * 100.0) / totalRows);
    }
}