package com.civicsense.complaint;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.complaint.dto.ComplaintDtos.CommentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.CommentResponse;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintDetail;
import com.civicsense.complaint.dto.ComplaintDtos.ComplaintSummary;
import com.civicsense.complaint.dto.ComplaintDtos.FeedbackRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReopenRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusEntry;
import com.civicsense.complaint.dto.ComplaintDtos.SubmitComplaintRequest;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Every query in this controller is restricted to the JWT's citizen id. */
@RestController
@RequestMapping("/citizen")
@PreAuthorize("hasRole('CITIZEN')")
public class CitizenComplaintController {

    private final ComplaintService complaintService;

    public CitizenComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @PostMapping("/complaints")
    ComplaintDetail submit(
            @Valid @RequestBody SubmitComplaintRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.submit(principal, request);
    }

    @GetMapping("/complaints")
    java.util.List<ComplaintSummary> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search) {
        return complaintService.myComplaints(principal, status, categoryId, from, to, search);
    }

    @GetMapping("/complaints/{id}")
    ComplaintDetail detail(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.myComplaint(id, principal);
    }

    @GetMapping("/complaints/{id}/timeline")
    java.util.List<StatusEntry> timeline(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.timeline(id, principal);
    }

    @GetMapping("/complaints/{id}/comments")
    java.util.List<CommentResponse> comments(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.comments(id, principal);
    }

    @PostMapping("/complaints/{id}/comments")
    CommentResponse addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.addComment(id, request, principal);
    }

    @PostMapping("/complaints/{id}/respond-info")
    void respondInfo(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.respondInfo(id, request, principal);
    }

    @PostMapping("/complaints/{id}/reopen")
    void reopen(
            @PathVariable UUID id,
            @Valid @RequestBody ReopenRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.reopen(id, request, principal);
    }

    @PostMapping("/complaints/{id}/feedback")
    void feedback(
            @PathVariable UUID id,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        complaintService.feedback(id, request, principal);
    }
}
