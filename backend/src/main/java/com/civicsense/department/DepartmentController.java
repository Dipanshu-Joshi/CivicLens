package com.civicsense.department;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.auth.AuthService;
import com.civicsense.auth.dto.AuthResponses.DepartmentContextResponse;
import com.civicsense.complaint.ComplaintService;
import com.civicsense.complaint.dto.ComplaintDtos.ActivityEntry;
import com.civicsense.department.dto.DepartmentDtos.CapabilityResponse;
import com.civicsense.department.dto.DepartmentDtos.CapabilityUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.DepartmentOverviewResponse;
import com.civicsense.department.dto.DepartmentDtos.DepartmentProfileResponse;
import com.civicsense.department.dto.DepartmentDtos.DepartmentProfileUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.TeamMemberResponse;
import com.civicsense.department.dto.DepartmentDtos.TeamUpdateRequest;
import com.civicsense.notification.NotificationService;
import com.civicsense.notification.dto.NotificationDtos.NotificationResponse;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/**
 * Department-admin dashboard surface. The id is always taken from the JWT —
 * never from the client — and the class-level guard rejects unverified,
 * suspended or inactive departments before any service method runs.
 */
@RestController
@RequestMapping("/department")
@PreAuthorize("@departmentGuard.isVerified(authentication)")
public class DepartmentController {

    private final AuthService authService;
    private final ComplaintService complaintService;
    private final DepartmentManagementService managementService;
    private final NotificationService notificationService;

    public DepartmentController(
            AuthService authService,
            ComplaintService complaintService,
            DepartmentManagementService managementService,
            NotificationService notificationService) {
        this.authService = authService;
        this.complaintService = complaintService;
        this.managementService = managementService;
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    DepartmentContextResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.departmentContext(principal);
    }

    @GetMapping("/overview")
    DepartmentOverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "30") int periodDays) {
        return new DepartmentOverviewResponse(
                authService.departmentContext(principal),
                complaintService.departmentAnalytics(principal, periodDays),
                complaintService.departmentActivity(principal, 20));
    }

    @GetMapping("/activity")
    List<ActivityEntry> activity(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "20") int limit) {
        return complaintService.departmentActivity(principal, limit);
    }

    @GetMapping("/profile")
    DepartmentProfileResponse profile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return managementService.profile(principal);
    }

    @PatchMapping("/profile")
    DepartmentProfileResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DepartmentProfileUpdateRequest request) {
        return managementService.updateProfile(principal, request);
    }

    @GetMapping("/capabilities")
    List<CapabilityResponse> capabilities(@AuthenticationPrincipal AuthenticatedUser principal) {
        return managementService.capabilities(principal);
    }

    @PutMapping("/capabilities")
    List<CapabilityResponse> replaceCapabilities(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CapabilityUpdateRequest request) {
        return managementService.replaceCapabilities(principal, request.capabilities());
    }

    @GetMapping("/team")
    List<TeamMemberResponse> team(@AuthenticationPrincipal AuthenticatedUser principal) {
        return managementService.team(principal);
    }

    @PatchMapping("/team/{userId}")
    List<TeamMemberResponse> updateTeamMember(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TeamUpdateRequest request) {
        return managementService.updateTeamMember(principal, userId, request);
    }

    /** Department-prefixed alias for the existing recipient-scoped notification list. */
    @GetMapping("/notifications")
    List<NotificationResponse> notifications(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.listMine(principal);
    }
}
