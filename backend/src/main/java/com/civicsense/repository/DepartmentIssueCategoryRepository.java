package com.civicsense.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.DepartmentIssueCategory;
import com.civicsense.domain.DepartmentIssueCategoryId;

public interface DepartmentIssueCategoryRepository
        extends JpaRepository<DepartmentIssueCategory, DepartmentIssueCategoryId> {

    /**
     * The department id lives inside the composite {@code @EmbeddedId}, so Spring Data
     * traverses {@code id.departmentId}; hence {@code IdDepartmentId} in the method name.
     */
    List<DepartmentIssueCategory> findAllByIdDepartmentIdOrderByPriorityAsc(java.util.UUID departmentId);

    List<DepartmentIssueCategory> findAllByIdCategoryId(java.util.UUID categoryId);

    Optional<DepartmentIssueCategory> findByIdDepartmentIdAndIdCategoryId(
            java.util.UUID departmentId, java.util.UUID categoryId);
}
