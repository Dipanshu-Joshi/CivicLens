package com.civicsense.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentAdminRepository extends JpaRepository<DepartmentAdmin, UUID> {

    Optional<DepartmentAdmin> findByUserId(UUID userId);

    java.util.List<DepartmentAdmin> findByDepartmentId(UUID departmentId);
}
