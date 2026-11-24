package com.civicsense.department.dto;

import java.util.List;
import java.util.UUID;

import com.civicsense.auth.dto.AuthResponses.DepartmentContextResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ActivityEntry;
import com.civicsense.complaint.dto.ComplaintDtos.AnalyticsResponse;
import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.VerificationStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Read/write models for the verified department-admin dashboard. */
public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentProfileResponse(
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
            boolean active,
            boolean acceptsComplaints,
            boolean autoAssignEnabled,
            int routingPriority,
            Integer maxOpenComplaints) {
    }

    public record DepartmentProfileUpdateRequest(
            @Size(max = 1000) String description,
            @Size(max = 100) String contactNumber,
            @Size(max = 500) String officeAddress,
            @NotNull Boolean acceptsComplaints,
            @NotNull Boolean autoAssignEnabled) {
    }

    public record CapabilityResponse(
            UUID categoryId,
            String categoryCode,
            String categoryName,
            boolean primary,
            int priority) {
    }

    public record CapabilityRequest(
            @NotNull UUID categoryId,
            boolean primary,
            @Min(1) @Max(1000) int priority) {
    }

    public record TeamMemberResponse(
            UUID userId,
            String fullName,
            String email,
            AdminLevel adminLevel,
            boolean primaryAdmin,
            String title,
            AccountStatus accountStatus) {
    }

    public record TeamUpdateRequest(@NotNull AdminLevel adminLevel, boolean primaryAdmin) {
    }

    public record ServiceAreaUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 4000) String boundaryWkt,
            @Size(max = 60) String coversWard,
            @Size(max = 60) String coversZone) {
    }

    public record CapabilityUpdateRequest(
            @NotEmpty List<CapabilityRequest> capabilities) {
    }

    public record DepartmentOverviewResponse(
            DepartmentContextResponse context,
            AnalyticsResponse analytics,
            List<ActivityEntry> activity) {
    }
}
