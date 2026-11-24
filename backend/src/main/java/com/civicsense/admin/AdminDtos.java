package com.civicsense.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response records for the Phase 8 platform governance surface. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record PlatformOverview(
            int periodDays,
            PlatformStats stats,
            GovernanceStats governance,
            List<CountEntry> byStatus,
            List<CountEntry> byCategory,
            List<CountEntry> byDepartment,
            List<CountEntry> byJurisdiction,
            List<ActivityEntry> activity,
            List<AuditLogEntry> audit) {
    }

    public record PlatformStats(
            long total,
            long resolved,
            long pending,
            long unassigned,
            long overdue,
            long failedAi,
            double averageResolutionHours,
            double slaCompliancePercent) {
    }

    public record GovernanceStats(
            long pendingDepartments,
            long suspendedDepartments,
            long ambiguous,
            long lowConfidence,
            long failedAi,
            long reassignmentRequests) {
    }

    public record CountEntry(String key, String label, long count) {
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

    public record AuditLogEntry(
            UUID id,
            String actorName,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String reason,
            Instant createdAt,
            String correlationId) {
    }

    public record AdminDepartmentDetail(
            UUID id,
            String name,
            String departmentCode,
            String departmentType,
            String description,
            String officialEmail,
            String contactNumber,
            String officeAddress,
            String city,
            String state,
            String country,
            VerificationStatus verificationStatus,
            boolean isActive,
            boolean acceptsComplaints,
            boolean autoAssignEnabled,
            int routingPriority,
            Integer maxOpenComplaints,
            Instant verifiedAt,
            UUID verifiedByUserId,
            String rejectionReason,
            Instant createdAt,
            long openComplaints,
            long totalComplaints,
            long adminCount) {
    }

    public record DepartmentStatusRequest(
            @NotNull VerificationStatus status,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record DepartmentOperationsRequest(
            @Min(0) @Max(10000) Integer routingPriority,
            @Min(1) @Max(10000) Integer maxOpenComplaints,
            @NotNull Boolean autoAssignEnabled,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            Role role,
            AccountStatus accountStatus,
            UUID departmentId,
            String departmentName,
            Instant lastLoginAt) {
    }

    public record UserStatusRequest(
            @NotNull AccountStatus status,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }

    public record UserRoleRequest(
            @NotNull Role role,
            @NotBlank @Size(min = 10, max = 1000) String reason) {
    }
}
