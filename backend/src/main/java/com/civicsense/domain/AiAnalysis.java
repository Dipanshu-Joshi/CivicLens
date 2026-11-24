package com.civicsense.domain;

import java.time.Instant;
import java.util.UUID;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    private String provider;
    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "request_payload")
    private String requestPayload;

    @Column(name = "raw_response")
    private String rawResponse;

    @Column(name = "parsed_result")
    private String parsedResult;

    @Column(name = "is_civic_issue")
    private Boolean isCivicIssue;

    @Column(name = "category_code")
    private String categoryCode;

    @Column(name = "subcategory_code")
    private String subcategoryCode;

    private String severity;

    @Column(name = "safety_risk")
    private Boolean safetyRisk;

    @Column(name = "is_blurry")
    private Boolean isBlurry;

    @Column(name = "ai_description")
    private String description;

    @Column(name = "visible_damage")
    private String visibleDamage;

    @Column(name = "requires_additional_info")
    private Boolean requiresAdditionalInfo;

    private Double confidence;

    @Column(name = "recommended_department_hint")
    private String recommendedDepartmentHint;

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiAnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiAnalysisSource source = AiAnalysisSource.AI;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
