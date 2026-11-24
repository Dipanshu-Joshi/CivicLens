package com.civicsense.domain;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.identity.Department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "routing_decisions")
@Getter
@Setter
@NoArgsConstructor
public class RoutingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(name = "ruleset_version", nullable = false)
    private String rulesetVersion;

    @Column(name = "ai_category_code")
    private String aiCategoryCode;

    private Double confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_jurisdiction_id")
    private Jurisdiction matchedJurisdiction;

    @JdbcTypeCode(SqlTypes.JSON)
    private String candidates;

    @Column(name = "location_point_wkt")
    private String locationPointWkt;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_department_id")
    private Department selectedDepartment;

    @Column(nullable = false)
    private String outcome;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
