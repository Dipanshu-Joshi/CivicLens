package com.civicsense.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ComplaintFeedback;

public interface ComplaintFeedbackRepository extends JpaRepository<ComplaintFeedback, UUID> {

    Optional<ComplaintFeedback> findByComplaintId(UUID complaintId);
}
