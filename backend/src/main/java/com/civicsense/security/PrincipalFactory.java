package com.civicsense.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;

/** Single place that turns a persisted user into an authenticated principal. */
@Component
public class PrincipalFactory {

    private final DepartmentAdminRepository departmentAdminRepository;
    private final DepartmentRepository departmentRepository;

    public PrincipalFactory(
            DepartmentAdminRepository departmentAdminRepository,
            DepartmentRepository departmentRepository) {
        this.departmentAdminRepository = departmentAdminRepository;
        this.departmentRepository = departmentRepository;
    }

    public AuthenticatedUser from(User user) {
        UUID departmentId = null;
        VerificationStatus verificationStatus = null;

        if (user.getRole() == Role.DEPARTMENT_ADMIN) {
            Optional<DepartmentAdmin> admin = departmentAdminRepository.findByUserId(user.getId());
            if (admin.isPresent()) {
                departmentId = admin.get().getDepartmentId();
                verificationStatus = departmentRepository.findById(departmentId)
                        .map(Department::getVerificationStatus)
                        .orElse(null);
            }
        }

        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                departmentId,
                verificationStatus);
    }
}
