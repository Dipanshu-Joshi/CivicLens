package com.civicsense.complaint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

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

import com.civicsense.complaint.dto.ComplaintDtos.AnalyticsResponse;
import com.civicsense.complaint.dto.ComplaintDtos.CommentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintDetail;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintSummary;
import com.civicsense.complaint.dto.ComplaintDtos.EvidenceRequest;
import com.civicsense.complaint.dto.ComplaintDtos.NoteRequest;
import com.civicsense.complaint.dto.ComplaintDtos.NoteResponse;
import com.civicsense.complaint.dto.ComplaintDtos.PagedResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignRequestRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReasonRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ResolveRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.Severity;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** All data is fetched with the department id from the JWT, never from the client. */
@RestController
@RequestMapping("/department")
@PreAuthorize("@departmentGuard.isVerified(authentication)")
public class DepartmentComplaintController {

    private final ComplaintService complaintService;

    public DepartmentComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping("/complaints")
    List<ComplaintSummary> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return complaintService.departmentComplaints(principal, status, from, to);
    }

    @GetMapping("/complaints/queue")
    PagedResponse<ComplaintSummary> queue(
            @AuthenticationPrincipal AuthenticatedUser principal,
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return complaintService.departmentComplaintQueue(principal, page, size, sort, status,
                categoryCode, ward, zone, severity, search, overdue, aiAssigned, from, to);
    }

    @GetMapping("/complaints/{id}")
    ComplaintDetail detail(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.departmentComplaint(id, principal);
    }

    @PostMapping("/complaints/{id}/accept")
    void accept(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.accept(id, principal);
    }

    @PostMapping("/complaints/{id}/reject")
    void reject(
            @PathVariable UUID id,
            @Valid @RequestBody ReasonRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.reject(id, request.reason(), principal);
    }

    @PatchMapping("/complaints/{id}/status")
    void patchStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.patchStatus(id, request, principal);
    }

    @PostMapping("/complaints/{id}/resolve")
    void resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.resolve(id, request, principal);
    }

    @PostMapping("/complaints/{id}/evidence")
    Map<String, Object> attachEvidence(
            @PathVariable UUID id,
            @Valid @RequestBody EvidenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.attachEvidence(id, request, principal);
        return Map.of("status", "ATTACHED", "imageId", request.imageId());
    }

    @PostMapping("/complaints/{id}/request-info")
    void requestInfo(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.requestInfo(id, request, principal);
    }

    @GetMapping("/complaints/{id}/notes")
    List<NoteResponse> notes(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.notes(id, principal);
    }

    @PostMapping("/complaints/{id}/notes")
    NoteResponse addNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.addNote(id, request, principal);
    }

    @PostMapping("/complaints/{id}/reassign-request")
    Map<String, String> requestReassignment(
            @PathVariable UUID id,
            @Valid @RequestBody ReassignRequestRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.requestReassignment(id, request, principal);
        return Map.of("status", "PENDING");
    }

    @GetMapping("/analytics")
    AnalyticsResponse analytics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "30") int periodDays) {
        return complaintService.departmentAnalytics(principal, periodDays);
    }
}
