package com.civicsense.routing.dto;

import java.time.Instant;
import java.util.UUID;

/** Read model for persisted, deterministic routing decisions. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record RoutingDecisionResponse(
            UUID id,
            String rulesetVersion,
            String aiCategoryCode,
            Double confidence,
            String locationPointWkt,
            UUID matchedJurisdictionId,
            Object candidates,
            UUID selectedDepartmentId,
            String outcome,
            String reason,
            boolean current,
            Instant createdAt) {
    }
}
