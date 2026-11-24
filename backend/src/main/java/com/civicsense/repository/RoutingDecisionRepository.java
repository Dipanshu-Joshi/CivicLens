package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.RoutingDecision;

public interface RoutingDecisionRepository extends JpaRepository<RoutingDecision, UUID> {

    List<RoutingDecision> findAllByComplaintIdOrderByCreatedAtDesc(UUID complaintId);

    boolean existsByComplaintId(UUID complaintId);

    List<RoutingDecision> findAllByCurrentTrueAndOutcomeNot(String outcome);
}
