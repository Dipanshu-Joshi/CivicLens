package com.civicsense.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;

/** Response records for the Phase 2 auth surface. */
public final class AuthResponses {

    private AuthResponses() {
    }

    public record UserInfo(
            UUID id,
            String email,
            String fullName,
            Role role,
            UUID departmentId,
            VerificationStatus verificationStatus) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserInfo user) {
    }

    public record MeResponse(
            UUID id,
            String email,
            String fullName,
            Role role,
            AccountStatus accountStatus,
            VerificationStatus verificationStatus,
            UUID departmentId,
            List<String> permissions) {
    }

    public record MessageResponse(String message) {
    }

    public record DepartmentRegistrationResponse(
            UUID departmentId,
            VerificationStatus verificationStatus,
            String message) {
    }

    public record DepartmentSummary(
            UUID id,
            String name,
            String departmentCode,
            String departmentType,
            String officialEmail,
            String city,
            VerificationStatus verificationStatus,
            boolean isActive,
            boolean acceptsComplaints,
            Instant verifiedAt) {
    }

    public record VerifyDepartmentRequest(
            @jakarta.validation.constraints.NotNull VerificationStatus decision,
            String reason) {
    }

    public record DepartmentContextResponse(
            UUID departmentId,
            String departmentName,
            String departmentCode,
            VerificationStatus verificationStatus,
            boolean acceptsComplaints) {
    }
}
