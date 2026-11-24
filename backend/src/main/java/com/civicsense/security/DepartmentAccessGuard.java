package com.civicsense.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentRepository;

/**
 * Bean used by @PreAuthorize on /department/** endpoints. Department
 * verification and active status are re-read from the database on every
 * request, so a stale token cannot keep access after verification is revoked
 * or the department is deactivated. Admin account status is re-read by
 * JwtAuthenticationFilter on every request as well.
 */
@Component("departmentGuard")
public class DepartmentAccessGuard {

    private final DepartmentRepository departments;

    public DepartmentAccessGuard(DepartmentRepository departments) {
        this.departments = departments;
    }

    public boolean isVerified(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || !user.isVerifiedDepartmentAdmin()) {
            return false;
        }
        return departments.findById(user.departmentId())
                .filter(Department::isActive)
                .filter(department -> department.getVerificationStatus() == VerificationStatus.VERIFIED)
                .isPresent();
    }

    /** Denies access to a resource belonging to a different department. */
    public boolean ownsDepartment(Authentication authentication, UUID departmentId) {
        if (!isVerified(authentication) || departmentId == null) {
            return false;
        }
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return departmentId.equals(user.departmentId());
    }
}
