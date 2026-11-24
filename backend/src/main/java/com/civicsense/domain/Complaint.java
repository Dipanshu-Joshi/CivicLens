package com.civicsense.domain;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.identity.Department;
import com.civicsense.identity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "complaint_number", nullable = false, unique = true)
    private String complaintNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private IssueCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private IssueCategory subcategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity = Severity.MEDIUM;

    @Column(nullable = false)
    private String description;

    @Column(name = "additional_info")
    private String additionalInfo;

    private Double latitude;
    private Double longitude;

    @Column(name = "address_line")
    private String addressLine;

    private String city;
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    private String country;
    private String ward;
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", nullable = false)
    private LocationSource locationSource;

    /** WKT display copy only; the PostGIS location geometry is intentionally unmapped. */
    @Column(name = "location_wkt")
    private String locationWkt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurisdiction_id")
    private Jurisdiction jurisdiction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_department_id")
    private Department relatedDepartment;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "resolution_summary")
    private String resolutionSummary;

    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Complaint duplicateOf;

    @Column(name = "duplicate_flag_reason")
    private String duplicateFlagReason;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_requires_review", nullable = false)
    private boolean aiRequiresReview;

    @Column(name = "admin_override_by_user_id")
    private UUID adminOverrideByUserId;

    @Column(name = "admin_override_at")
    private Instant adminOverrideAt;

    @Column(name = "admin_override_reason")
    private String adminOverrideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
