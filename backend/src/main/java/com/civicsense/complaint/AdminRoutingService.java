package com.civicsense.complaint;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.audit.Audited;
import com.civicsense.complaint.dto.ComplaintDtos.ClassificationOverrideRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReasonRequest;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.ComplaintNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.ReasonRequiredException;
import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisSource;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.RoutingDecision;
import com.civicsense.domain.Severity;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.routing.RoutingEngine;
import com.civicsense.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Super-admin operational controls over the routing ruleset. */
@Service
public class AdminRoutingService {

    private final ComplaintRepository complaints;
    private final IssueCategoryRepository categories;
    private final AiAnalysisRepository analyses;
    private final RoutingEngine routingEngine;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;

    public AdminRoutingService(ComplaintRepository complaints,
                               IssueCategoryRepository categories,
                               AiAnalysisRepository analyses,
                               RoutingEngine routingEngine,
                               ObjectMapper objectMapper,
                               java.time.Clock clock) {
        this.complaints = complaints;
        this.categories = categories;
        this.analyses = analyses;
        this.routingEngine = routingEngine;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    @Audited(action = "COMPLAINT_REROUTE", entityType = "complaint")
    public RoutingDecision reroute(UUID complaintId, ReasonRequest request, AuthenticatedUser admin) {
        requireComplaint(complaintId);
        return routingEngine.route(complaintId, true, request.reason(), admin.id());
    }

    @Transactional
    @Audited(action = "COMPLAINT_CLASSIFICATION_OVERRIDE", entityType = "complaint")
    public RoutingDecision overrideClassification(UUID complaintId,
                                                  ClassificationOverrideRequest request,
                                                  AuthenticatedUser admin) {
        Complaint complaint = requireComplaint(complaintId);
        IssueCategory category = requireCategory(request.categoryCode());
        IssueCategory subcategory = requireCategory(request.subcategoryCode());
        if (subcategory.getParentCategory() == null
                || !subcategory.getParentCategory().getId().equals(category.getId())) {
            throw new CategoryNotFoundException();
        }

        Severity severity = request.severity();
        String description = request.description();
        if (description != null) {
            String trimmed = description.trim();
            if (trimmed.length() < 10 || trimmed.length() > 2000) {
                throw new ReasonRequiredException("classification description");
            }
            description = trimmed;
        }

        Instant now = clock.instant();
        AiAnalysis override = new AiAnalysis();
        override.setComplaint(complaint);
        // An administrator correction is not an AI provider result; source and payload
        // identify it, so no fake provider/model is recorded.
        override.setStatus(AiAnalysisStatus.SUCCESS);
        override.setSource(AiAnalysisSource.ADMIN_OVERRIDE);
        override.setIsCivicIssue(true);
        override.setCategoryCode(category.getCode());
        override.setSubcategoryCode(subcategory.getCode());
        override.setSeverity(severity == null ? complaint.getSeverity().name() : severity.name());
        // The database column is capped at 500 characters; the full value is kept in
        // the override payload and exposed through the governed history endpoint.
        if (description != null) {
            override.setDescription(description.substring(0, Math.min(description.length(), 500)));
        }
        override.setConfidence(1.0);
        override.setRequiresHumanReview(false);
        override.setAttemptNumber(0);
        override.setRawResponse(writePayload(request, admin, severity, description));
        override.setParsedResult(override.getRawResponse());
        override.setCreatedAt(now);
        analyses.save(override);

        complaint.setCategory(category);
        complaint.setSubcategory(subcategory);
        if (severity != null) {
            complaint.setSeverity(severity);
        }
        if (description != null) {
            complaint.setDescription(description);
        }
        complaint.setAdminOverrideByUserId(admin.id());
        complaint.setAdminOverrideAt(now);
        complaint.setAdminOverrideReason(request.reason());
        complaint.setAiConfidence(1.0);
        complaint.setAiRequiresReview(false);
        complaint.setUpdatedAt(now);
        complaints.save(complaint);

        return routingEngine.route(complaintId, true, request.reason(), admin.id());
    }

    private Complaint requireComplaint(UUID complaintId) {
        return complaints.findById(complaintId).orElseThrow(ComplaintNotFoundException::new);
    }

    private IssueCategory requireCategory(String code) {
        IssueCategory category = categories
                .findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(CategoryNotFoundException::new);
        if (!category.isActive()) {
            throw new CategoryNotFoundException();
        }
        return category;
    }

    private String writePayload(ClassificationOverrideRequest request, AuthenticatedUser admin,
                                Severity severity, String description) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("source", "ADMIN_OVERRIDE");
            payload.put("categoryCode", request.categoryCode());
            payload.put("subcategoryCode", request.subcategoryCode());
            payload.put("reason", request.reason());
            payload.put("adminOverrideReason", request.reason());
            payload.put("adminUserId", admin.id().toString());
            if (severity != null) {
                payload.put("severity", severity.name());
            }
            if (description != null) {
                payload.put("description", description);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"source\":\"ADMIN_OVERRIDE\"}";
        }
    }
}
