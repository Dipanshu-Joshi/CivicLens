package com.civicsense.complaint.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.civicsense.domain.AiAnalysisSource;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.LocationSource;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.Severity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response records for the Phase 3 complaint surface. */
public final class ComplaintDtos {

    private ComplaintDtos() {
    }

    public record SubmitComplaintRequest(
            @NotBlank @Size(min = 10, max = 2000) String description,
            @Size(max = 2000) String additionalInfo,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull LocationSource locationSource,
            @Size(max = 255) String addressLine,
            @Size(max = 120) String city,
            @Size(max = 120) String state,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String country,
            @Size(max = 50) String ward,
            @Size(max = 50) String zone,
            @Size(max = 60) String categoryCode,
            @Size(max = 60) String subcategoryCode,
            java.util.List<java.util.UUID> imageIds) {
    }

    public record ComplaintSummary(
            UUID id,
            String complaintNumber,
            String description,
            ComplaintStatus status,
            Severity severity,
            String categoryCode,
            String ward,
            String city,
            UUID departmentId,
            Instant createdAt) {
    }

    public record ComplaintDetail(
            UUID id,
            String complaintNumber,
            String description,
            String additionalInfo,
            ComplaintStatus status,
            Severity severity,
            String categoryCode,
            String subcategoryCode,
            String addressLine,
            String city,
            String state,
            String postalCode,
            String country,
            String ward,
            String zone,
            LocationSource locationSource,
            Double latitude,
            Double longitude,
            UUID departmentId,
            String departmentName,
            Instant assignedAt,
            Instant resolvedAt,
            String resolutionSummary,
            Instant resolutionDueAt,
            Instant createdAt,
            List<StatusEntry> timeline,
            List<CommentResponse> comments,
            List<AssignmentEntry> assignments,
            FeedbackResponse feedback,
            List<ComplaintImageResponse> images,
            AiAnalysisResponse analysis,
            List<com.civicsense.routing.dto.RoutingDtos.RoutingDecisionResponse> routingDecisions,
            List<NoteResponse> internalNotes) {
    }

    public record ComplaintImageResponse(
            UUID id,
            String storageKey,
            String contentType,
            Long sizeBytes,
            com.civicsense.domain.ImageKind imageKind,
            com.civicsense.domain.UploadStatus uploadStatus,
            Instant createdAt) {
    }

    public record AiAnalysisResponse(
            UUID id,
            com.civicsense.domain.AiAnalysisStatus status,
            Boolean isCivicIssue,
            String categoryCode,
            String subcategoryCode,
            String severity,
            String description,
            Boolean safetyRisk,
            String visibleDamage,
            Boolean isBlurry,
            Boolean requiresAdditionalInfo,
            Double confidence,
            String recommendedDepartmentHint,
            boolean requiresHumanReview,
            String errorMessage,
            Instant createdAt) {
    }

    public record StatusEntry(
            ComplaintStatus fromStatus,
            ComplaintStatus toStatus,
            String actorRole,
            String reason,
            Instant createdAt) {
    }

    public record AssignmentEntry(
            UUID departmentId,
            String departmentName,
            String assignmentType,
            boolean active,
            Instant assignedAt,
            Instant unassignedAt) {
    }

    public record FeedbackResponse(
            int rating,
            String comment,
            boolean resolutionConfirmed,
            String disputeReason,
            Instant createdAt) {
    }

    public record CommentRequest(@NotBlank @Size(min = 1, max = 2000) String body) {
    }

    public record CommentResponse(
            UUID id, String authorName, String authorRole, String body, Instant createdAt) {
    }

    public record StatusUpdateRequest(
            @NotNull ComplaintStatus status,
            @Size(max = 1000) String reason) {
    }

    public record AssignRequest(
            @NotNull UUID departmentId,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record ReopenRequest(@NotBlank @Size(min = 20, max = 1000) String reason) {
    }

    public record FeedbackRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String comment,
            @NotNull Boolean resolutionConfirmed,
            @Size(max = 1000) String disputeReason) {
    }

    public record ReassignRequestRequest(
            @NotNull UUID toDepartmentId,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record ReassignDecisionRequest(
            @NotNull ReassignmentStatus decision,
            @Size(max = 1000) String reason) {
    }

    public record ReassignRequestResponse(
            UUID id,
            UUID complaintId,
            UUID fromDepartmentId,
            UUID toDepartmentId,
            ReassignmentStatus status,
            String reason,
            String reviewReason,
            Instant createdAt,
            Instant reviewedAt) {
    }

    public record AnalyticsResponse(
            long total,
            Map<String, Long> byStatus,
            long resolved,
            long overdue,
            long unassigned,
            Double averageResolutionHours,
            int periodDays,
            long receivedInPeriod,
            long resolvedInPeriod,
            long pending,
            long accepted,
            long inProgress,
            long rejected,
            long closed,
            List<CategoryCount> byCategory,
            List<WardCount> byWard,
            List<TrendPoint> trend,
            Double averageOpenAgeHours,
            Double slaCompliancePercent) {
    }

    public record ReasonRequest(@NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    /** Super-admin classification correction; always append-only and audited. */
    public record ClassificationOverrideRequest(
            @NotBlank @Size(max = 60) String categoryCode,
            @NotBlank @Size(max = 60) String subcategoryCode,
            @NotBlank @Size(min = 10, max = 1000) String reason,
            Severity severity,
            @Size(min = 10, max = 2000)
            @Pattern(regexp = "(?s)^(?:\\s*\\S){10}.*$",
                    message = "description must contain at least 10 non-blank characters") String description) {
    }

    /** Append-only AI/override history entry; raw provider payloads are never exposed. */
    public record AiAnalysisHistoryEntry(
            UUID id,
            AiAnalysisStatus status,
            AiAnalysisSource source,
            Boolean isCivicIssue,
            String categoryCode,
            String subcategoryCode,
            String severity,
            String description,
            Double confidence,
            boolean requiresHumanReview,
            String adminOverrideReason,
            Instant createdAt) {
    }

    public record AiAnalysisHistoryResponse(List<AiAnalysisHistoryEntry> analyses) {
    }

    public record NoteRequest(@NotBlank @Size(min = 1, max = 2000) String body) {
    }

    public record NoteResponse(UUID id, String authorName, String body, Instant createdAt) {
    }

    public record PagedResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record EvidenceRequest(@NotNull UUID imageId) {
    }

    public record ResolveRequest(@NotBlank @Size(min = 10, max = 2000) String summary) {
    }

    public record CategoryCount(String code, String name, long count) {
    }

    public record WardCount(String ward, long count) {
    }

    public record TrendPoint(String period, long received, long resolved) {
    }

    public record ActivityEntry(
            UUID complaintId,
            String complaintNumber,
            ComplaintStatus fromStatus,
            ComplaintStatus toStatus,
            String actorRole,
            String reason,
            Instant createdAt) {
    }
}
