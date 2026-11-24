package com.civicsense.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintStatus;

public interface ComplaintRepository
        extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {

    Optional<Complaint> findByIdAndCitizenId(UUID id, UUID citizenId);

    Optional<Complaint> findByIdAndRelatedDepartmentId(UUID id, UUID departmentId);

    List<Complaint> findAllByCitizenIdOrderByCreatedAtDesc(UUID citizenId);

    List<Complaint> findAllByRelatedDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    List<Complaint> findAllByRelatedDepartmentIdIsNullAndStatusInOrderByCreatedAtDesc(
            List<ComplaintStatus> statuses);

    List<Complaint> findAllByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    long countByStatus(ComplaintStatus status);

    long countByRelatedDepartmentId(UUID departmentId);

    long countByRelatedDepartmentIdAndStatus(UUID departmentId, ComplaintStatus status);

    long countByRelatedDepartmentIdAndStatusIn(UUID departmentId, List<ComplaintStatus> statuses);

    long countByRelatedDepartmentIdAndStatusInAndResolutionDueAtBefore(
            UUID departmentId, List<ComplaintStatus> statuses, Instant dueBefore);

    long countByCitizenId(UUID citizenId);

    long countByCategoryId(UUID categoryId);

    long countBySubcategoryId(UUID subcategoryId);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    @Query("""
            select c from Complaint c
            where c.relatedDepartment is null
              and c.status in (com.civicsense.domain.ComplaintStatus.SUBMITTED,
                               com.civicsense.domain.ComplaintStatus.PENDING_ASSIGNMENT,
                               com.civicsense.domain.ComplaintStatus.UNASSIGNED)
            order by c.createdAt desc
            """)
    List<Complaint> findUnassigned();

    List<Complaint> findAllByRelatedDepartmentIdAndResolvedAtIsNotNull(UUID departmentId);

    @Query("""
            select c from Complaint c
            where c.citizen.id = :citizenId
              and (:status is null or c.status = :status)
              and (:categoryId is null or c.category.id = :categoryId
                                       or c.subcategory.id = :categoryId)
              and (:fromDate is null or c.createdAt >= :fromDate)
              and (:toDate is null or c.createdAt <= :toDate)
              and (:search is null or lower(c.description) like lower(concat('%', :search, '%'))
                                  or lower(c.complaintNumber) like lower(concat('%', :search, '%')))
            order by c.createdAt desc
            """)
    List<Complaint> findCitizenComplaints(
            @Param("citizenId") UUID citizenId,
            @Param("status") ComplaintStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("fromDate") Instant from,
            @Param("toDate") Instant to,
            @Param("search") String search);

    @Query("""
            select c from Complaint c
            where c.relatedDepartment.id = :departmentId
              and (:status is null or c.status = :status)
              and (:fromDate is null or c.createdAt >= :fromDate)
              and (:toDate is null or c.createdAt <= :toDate)
            order by c.createdAt desc
            """)
    List<Complaint> findDepartmentComplaints(
            @Param("departmentId") UUID departmentId,
            @Param("status") ComplaintStatus status,
            @Param("fromDate") Instant from,
            @Param("toDate") Instant to);
}
