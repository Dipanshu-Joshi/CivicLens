package com.civicsense.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;

/**
 * Immutable principal placed in the SecurityContext by JwtAuthenticationFilter.
 * Department verification is re-read from the database on every request, so a
 * token issued before verification cannot bypass the verification guard.
 */
public record AuthenticatedUser(
        UUID id,
        String email,
        Role role,
        UUID departmentId,
        VerificationStatus departmentVerificationStatus) {

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isVerifiedDepartmentAdmin() {
        return role == Role.DEPARTMENT_ADMIN
                && departmentId != null
                && departmentVerificationStatus == VerificationStatus.VERIFIED;
    }
}
