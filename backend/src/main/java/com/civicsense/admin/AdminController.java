package com.civicsense.admin;

import java.util.List;
import java.util.UUID;

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

import com.civicsense.admin.AdminDtos.ActivityEntry;
import com.civicsense.admin.AdminDtos.AdminDepartmentDetail;
import com.civicsense.admin.AdminDtos.AuditLogEntry;
import com.civicsense.admin.AdminDtos.DepartmentOperationsRequest;
import com.civicsense.admin.AdminDtos.DepartmentStatusRequest;
import com.civicsense.admin.AdminDtos.PlatformOverview;
import com.civicsense.admin.AdminDtos.UserRoleRequest;
import com.civicsense.admin.AdminDtos.UserStatusRequest;
import com.civicsense.admin.AdminDtos.UserSummary;
import com.civicsense.auth.AuthService;
import com.civicsense.auth.dto.AuthResponses.DepartmentSummary;
import com.civicsense.auth.dto.AuthResponses.VerifyDepartmentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.PagedResponse;
import com.civicsense.reference.dto.ReferenceDtos.CategoryResponse;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionResponse;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaResponse;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Super-admin platform dashboard and governance surface. */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AuthService authService;
    private final AdminPlatformService platformService;

    public AdminController(AuthService authService, AdminPlatformService platformService) {
        this.authService = authService;
        this.platformService = platformService;
    }

    // -----------------------------------------------------------------------
    // Platform insight
    // -----------------------------------------------------------------------

    @GetMapping("/overview")
    PlatformOverview overview(@RequestParam(defaultValue = "30") int periodDays) {
        return platformService.overview(periodDays, 20);
    }

    @GetMapping("/analytics")
    PlatformOverview analytics(@RequestParam(defaultValue = "30") int periodDays) {
        return platformService.analytics(periodDays);
    }

    @GetMapping("/activity")
    List<ActivityEntry> activity(@RequestParam(defaultValue = "20") int limit) {
        return platformService.activity(limit);
    }

    // -----------------------------------------------------------------------
    // Department governance (existing verification routes preserved)
    // -----------------------------------------------------------------------

    @GetMapping("/departments")
    List<DepartmentSummary> departments(
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String departmentType,
            @RequestParam(required = false) String search) {
        return platformService.departments(verificationStatus, isActive, departmentType, search);
    }

    @GetMapping("/departments/{id}")
    AdminDepartmentDetail department(@PathVariable UUID id) {
        return platformService.departmentDetail(id);
    }

    @PostMapping("/departments/{id}/verify")
    DepartmentSummary verify(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyDepartmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return authService.verifyDepartment(id, request, admin);
    }

    @PatchMapping("/departments/{id}/status")
    DepartmentSummary setDepartmentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return platformService.updateDepartmentStatus(id, request, admin);
    }

    @PatchMapping("/departments/{id}/operations")
    AdminDepartmentDetail setDepartmentOperations(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentOperationsRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return platformService.updateDepartmentOperations(id, request, admin);
    }

    // -----------------------------------------------------------------------
    // User management
    // -----------------------------------------------------------------------

    @GetMapping("/users")
    PagedResponse<UserSummary> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return platformService.users(role, status, departmentId, search, page, size);
    }

    @PatchMapping("/users/{id}/status")
    UserSummary setUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return platformService.updateUserStatus(id, request, admin);
    }

    @PatchMapping("/users/{id}/role")
    UserSummary setUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return platformService.updateUserRole(id, request, admin);
    }

    // -----------------------------------------------------------------------
    // Audit exploration
    // -----------------------------------------------------------------------

    @GetMapping("/audit-logs")
    PagedResponse<AuditLogEntry> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return platformService.auditLogs(action, entityType, entityId, actorId, from, to, page, size);
    }

    // -----------------------------------------------------------------------
    // Reference / service-area oversight
    // -----------------------------------------------------------------------

    @GetMapping("/categories")
    List<CategoryResponse> categories(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return platformService.categories(includeInactive);
    }

    @GetMapping("/jurisdictions")
    List<JurisdictionResponse> jurisdictions(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return platformService.jurisdictions(includeInactive);
    }

    @GetMapping("/service-areas")
    List<ServiceAreaResponse> serviceAreas(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean active) {
        return platformService.serviceAreas(departmentId, active);
    }
}
