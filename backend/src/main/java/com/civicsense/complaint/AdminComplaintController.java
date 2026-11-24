package com.civicsense.complaint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.complaint.dto.ComplaintDtos.AiAnalysisHistoryResponse;
import com.civicsense.complaint.dto.ComplaintDtos.AssignRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ClassificationOverrideRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintDetail;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintSummary;
import com.civicsense.complaint.dto.ComplaintDtos.PagedResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ReasonRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignDecisionRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignRequestResponse;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.Severity;
import com.civicsense.repository.RoutingDecisionRepository;
import com.civicsense.routing.RoutingDecisionMapper;
import com.civicsense.routing.dto.RoutingDtos.RoutingDecisionResponse;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Super-admin complaint and reassignment workflow. */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminComplaintController {

    private final ComplaintService complaintService;
    private final AdminRoutingService adminRoutingService;
    private final RoutingDecisionRepository routingDecisions;
    private final RoutingDecisionMapper routingDecisionMapper;

    public AdminComplaintController(ComplaintService complaintService,
                                    AdminRoutingService adminRoutingService,
                                    RoutingDecisionRepository routingDecisions,
                                    RoutingDecisionMapper routingDecisionMapper) {
        this.complaintService = complaintService;
        this.adminRoutingService = adminRoutingService;
        this.routingDecisions = routingDecisions;
        this.routingDecisionMapper = routingDecisionMapper;
    }

    @GetMapping("/complaints")
    List<ComplaintSummary> all() {
        return complaintService.allComplaints();
    }

    @GetMapping("/complaints/unassigned")
    List<ComplaintSummary> unassigned() {
        return complaintService.unassignedComplaints();
    }

    /** Rich platform-wide queue; deliberately distinct from the legacy list endpoints. */
    @GetMapping("/complaints/queue")
    PagedResponse<ComplaintSummary> queue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) List<ComplaintStatus> status,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean aiAssigned,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String routingOutcome,
            @RequestParam(required = false) String aiStatus) {
        return complaintService.platformComplaintQueue(page, size, sort, status, categoryCode,
                ward, zone, severity, search, overdue, aiAssigned, from, to,
                departmentId, routingOutcome, aiStatus);
    }

    @GetMapping("/complaints/{id}")
    ComplaintDetail detail(@PathVariable UUID id) {
        return complaintService.adminComplaint(id);
    }

    /** Append-only AI and administrator override history; no raw provider payloads. */
    @GetMapping("/complaints/{id}/analyses")
    AiAnalysisHistoryResponse analyses(@PathVariable UUID id) {
        return complaintService.aiHistory(id);
    }

    @GetMapping("/complaints/{id}/routing-decisions")
    List<RoutingDecisionResponse> routingDecisions(@PathVariable UUID id) {
        return routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(id).stream()
                .map(routingDecisionMapper::toResponse)
                .toList();
    }

    @PostMapping("/complaints/{id}/reroute")
    RoutingDecisionResponse reroute(
            @PathVariable UUID id,
            @Valid @RequestBody ReasonRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return routingDecisionMapper.toResponse(
                adminRoutingService.reroute(id, request, admin));
    }

    @PatchMapping("/complaints/{id}/classification")
    RoutingDecisionResponse overrideClassification(
            @PathVariable UUID id,
            @Valid @RequestBody ClassificationOverrideRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return routingDecisionMapper.toResponse(
                adminRoutingService.overrideClassification(id, request, admin));
    }

    @PostMapping("/complaints/{id}/assign")
    void assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        complaintService.assign(id, request, admin);
    }

    @PostMapping("/complaints/{id}/unassign")
    void unassign(
            @PathVariable UUID id,
            @Valid @RequestBody ReasonRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        complaintService.markUnassigned(id, request.reason(), admin);
    }

    @GetMapping("/reassignment-requests")
    List<ReassignRequestResponse> pendingReassignments() {
        return complaintService.pendingReassignments();
    }

    @PostMapping("/reassignment-requests/{id}/review")
    void review(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        complaintService.decideReassignment(id, request, admin);
    }

    // Cross-department analytics are intentionally not exposed here: Phase 3
    // only returns aggregates computed from real rows on /department/analytics.
}
