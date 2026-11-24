package com.civicsense.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "department_issue_categories")
@Getter
@Setter
@NoArgsConstructor
public class DepartmentIssueCategory {

    @EmbeddedId
    private DepartmentIssueCategoryId id;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
