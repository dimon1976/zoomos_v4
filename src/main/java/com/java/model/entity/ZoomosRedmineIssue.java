package com.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
