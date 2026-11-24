package com.civicsense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.IssueCategory;

public interface IssueCategoryRepository extends JpaRepository<IssueCategory, UUID> {

    Optional<IssueCategory> findByCode(String code);

    List<IssueCategory> findAllByOrderByDisplayOrderAsc();

    List<IssueCategory> findAllByActiveTrueOrderByDisplayOrderAsc();

    List<IssueCategory> findAllByParentCategoryIsNullOrderByDisplayOrderAsc();

    List<IssueCategory> findAllByParentCategoryIdOrderByDisplayOrderAsc(UUID parentCategoryId);

    List<IssueCategory> findAllByIdIn(java.util.Collection<UUID> ids);
}
