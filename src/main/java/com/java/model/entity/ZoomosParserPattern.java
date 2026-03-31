package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Паттерн парсера для сайта.
 * Уникальность обеспечивается функциональным индексом в БД:
 * UNIQUE (site_name, md5(pattern)) — см. миграцию V36.
 * Используется в upsert через ON CONFLICT (site_name, md5(pattern)).
 */
@Entity
@Table(name = "zoomos_parser_patterns")
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
