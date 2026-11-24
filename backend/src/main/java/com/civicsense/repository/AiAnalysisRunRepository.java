package com.civicsense.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civicsense.domain.AiAnalysisRun;

public interface AiAnalysisRunRepository extends JpaRepository<AiAnalysisRun, UUID> {

    @Modifying
    @Query("delete from AiAnalysisRun r where r.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
