package com.civicsense.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisStatus;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    List<AiAnalysis> findAllByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    Optional<AiAnalysis> findFirstByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    Optional<AiAnalysis> findFirstByComplaintIdAndStatusInOrderByCreatedAtDesc(
            UUID complaintId, List<AiAnalysisStatus> statuses);

    @Modifying
    @Query("delete from AiAnalysis a where a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
