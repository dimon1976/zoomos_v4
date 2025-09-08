package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Сущность для хранения статистики экспорта
 */
@Entity
@Table(name = "export_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "exportSession")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExportStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_session_id", nullable = false)
    private ExportSession exportSession;

    @Column(name = "group_field_name", nullable = false)
    private String groupFieldName;

    @Column(name = "group_field_value", nullable = false)
    private String groupFieldValue;

    @Column(name = "count_field_name", nullable = false)
    private String countFieldName;

    @Column(name = "count_value", nullable = false)
    @Builder.Default
    private Long countValue = 0L;

    @Column(name = "filter_conditions", columnDefinition = "TEXT")
    private String filterConditions; // JSON условий фильтрации (может не использоваться)

    @Column(name = "date_modifications_count", nullable = false)
    @Builder.Default
    private Long dateModificationsCount = 0L;

    @Column(name = "total_records_count", nullable = false)
    @Builder.Default
    private Long totalRecordsCount = 0L;

    @Column(name = "modification_type", nullable = false, length = 50)
    @Builder.Default
    private String modificationType = "STANDARD";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    /**
     * Метаданные полей для продвинутой фильтрации.
     * Содержит информацию о доступных значениях полей, их типах и другие метаданные
     * для построения динамических фильтров.
     * 
     * Пример структуры JSON:
     * {
     *   "availableValues": {
     *     "city": ["Москва", "СПб", "Екатеринбург"],
     *     "status": ["ACTIVE", "INACTIVE"],
     *     "priceRange": {"min": 100, "max": 5000}
     *   },
     *   "fieldTypes": {
     *     "city": "STRING",
     *     "status": "ENUM", 
     *     "price": "NUMERIC"
     *   }
     * }
     */
    @Column(name = "field_metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> fieldMetadata = new java.util.HashMap<>();
}