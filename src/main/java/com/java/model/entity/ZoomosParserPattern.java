package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "zoomos_parser_patterns",
       uniqueConstraints = @UniqueConstraint(columnNames = {"site_name", "pattern"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosParserPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;
}
