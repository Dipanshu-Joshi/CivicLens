package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ReassignmentRequest;
import com.civicsense.domain.ReassignmentStatus;

public interface ReassignmentRequestRepository extends JpaRepository<ReassignmentRequest, UUID> {

    List<ReassignmentRequest> findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus status);

    List<ReassignmentRequest> findAllByFromDepartmentIdAndStatusOrderByCreatedAtDesc(
            UUID fromDepartmentId, ReassignmentStatus status);
}
