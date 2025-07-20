package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Метаданные импортируемого файла
 */
@Entity
@Table(name = "file_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_session_id", nullable = false)
    private ImportSession importSession;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash")
    private String fileHash; // MD5 или SHA-256

    @Column(name = "detected_encoding")
    private String detectedEncoding;

    @Column(name = "detected_delimiter")
    private String detectedDelimiter;

    @Column(name = "detected_quote_char")
    private String detectedQuoteChar;

    @Column(name = "detected_escape_char")
    private String detectedEscapeChar;

    @Column(name = "total_columns")
    private Integer totalColumns;

    @Column(name = "column_headers", columnDefinition = "TEXT")
    private String columnHeaders; // JSON массив заголовков

    @Column(name = "sample_data", columnDefinition = "TEXT")
    private String sampleData; // JSON с примерами данных

    @Column(name = "file_format")
    private String fileFormat; // CSV, XLSX, XLS

    @Column(name = "has_header")
    private Boolean hasHeader;

    @Column(name = "temp_file_path")
    private String tempFilePath; // Путь к временному файлу
}