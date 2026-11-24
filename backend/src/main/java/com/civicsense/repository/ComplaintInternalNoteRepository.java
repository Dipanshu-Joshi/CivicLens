package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.ComplaintInternalNote;

public interface ComplaintInternalNoteRepository extends JpaRepository<ComplaintInternalNote, UUID> {

    List<ComplaintInternalNote> findAllByComplaintIdAndDepartmentIdOrderByCreatedAtAsc(
            UUID complaintId, UUID departmentId);
}
