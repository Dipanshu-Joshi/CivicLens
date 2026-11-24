package com.civicsense.routing;

import org.springframework.stereotype.Component;

import com.civicsense.domain.RoutingDecision;
import com.civicsense.routing.dto.RoutingDtos.RoutingDecisionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RoutingDecisionMapper {

    private final ObjectMapper objectMapper;

    public RoutingDecisionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RoutingDecisionResponse toResponse(RoutingDecision decision) {
        Object candidates;
        try {
            candidates = decision.getCandidates() == null
                    ? java.util.List.of()
                    : objectMapper.readValue(decision.getCandidates(), Object.class);
        } catch (Exception ex) {
            candidates = java.util.List.of();
        }
        return new RoutingDecisionResponse(
                decision.getId(),
                decision.getRulesetVersion(),
                decision.getAiCategoryCode(),
                decision.getConfidence(),
                decision.getLocationPointWkt(),
                decision.getMatchedJurisdiction() == null
                        ? null : decision.getMatchedJurisdiction().getId(),
                candidates,
                decision.getSelectedDepartment() == null
                        ? null : decision.getSelectedDepartment().getId(),
                decision.getOutcome(),
                decision.getReason(),
                decision.isCurrent(),
                decision.getCreatedAt());
    }
}
