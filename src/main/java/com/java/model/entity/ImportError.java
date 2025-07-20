package com.java.model.entity;

import com.java.model.enums.ErrorType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Детальная информация об ошибках импорта
 */
@Entity
@Table(name = "import_errors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_session_id", nullable = false)
    private ImportSession importSession;

    @Column(name = "row_number", nullable = false)
    private Long rowNumber;

    @Column(name = "column_name")
    private String columnName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false)
    private ErrorType errorType;

    @Column(name = "error_message", columnDefinition = "TEXT", nullable = false)
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @CreationTimestamp
    @Column(name = "occurred_at", updatable = false)
    private ZonedDateTime occurredAt;
}
