package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ComplaintComment;

public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, UUID> {

    List<ComplaintComment> findAllByComplaintIdOrderByCreatedAtAsc(UUID complaintId);
}
