package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_redmine_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosRedmineIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false, unique = true)
    private String siteName;

    @Column(name = "issue_id", nullable = false)
    private Integer issueId;

    @Column(name = "issue_status")
    private String issueStatus;

    @Column(name = "issue_url")
    private String issueUrl;

    @Column(name = "is_closed")
    @Builder.Default
    private boolean isClosed = false;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
