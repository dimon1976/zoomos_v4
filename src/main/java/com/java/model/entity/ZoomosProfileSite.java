package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zoomos_profile_sites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "site_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosProfileSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ZoomosCheckProfile profile;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    /** Переопределяют настройки магазина, если заполнены (запятая-разделённые city IDs). */
    @Column(name = "city_ids")
    private String cityIds;

    /** Фильтр по аккаунту выкачки. Пусто = любой аккаунт. */
    @Column(name = "account_filter")
    private String accountFilter;

    @Column(name = "parser_include")
    private String parserInclude;

    @Column(name = "parser_include_mode")
    @Builder.Default
    private String parserIncludeMode = "OR";

    @Column(name = "parser_exclude")
    private String parserExclude;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
}
