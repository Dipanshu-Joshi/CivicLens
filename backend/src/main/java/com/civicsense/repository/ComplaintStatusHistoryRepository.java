package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civicsense.domain.ComplaintStatusHistory;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, UUID> {

    List<ComplaintStatusHistory> findAllByComplaintIdOrderByCreatedAtAsc(UUID complaintId);

    @Query("""
            select h from ComplaintStatusHistory h
            where h.complaint.relatedDepartment.id = :departmentId
            order by h.createdAt desc, h.id desc
            """)
    List<ComplaintStatusHistory> findRecentByDepartmentId(
            @Param("departmentId") UUID departmentId, Pageable pageable);
}
