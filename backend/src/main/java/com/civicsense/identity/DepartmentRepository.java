package com.civicsense.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByOfficialEmailIgnoreCase(String officialEmail);

    boolean existsByDepartmentCode(String departmentCode);
}
