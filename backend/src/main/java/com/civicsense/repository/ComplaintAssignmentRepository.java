package com.civicsense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ComplaintAssignment;

public interface ComplaintAssignmentRepository extends JpaRepository<ComplaintAssignment, UUID> {

    Optional<ComplaintAssignment> findByComplaintIdAndActiveTrue(UUID complaintId);

    List<ComplaintAssignment> findAllByComplaintIdOrderByAssignedAtDesc(UUID complaintId);
}
