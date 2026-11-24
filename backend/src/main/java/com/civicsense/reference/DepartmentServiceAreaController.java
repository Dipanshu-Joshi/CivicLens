package com.civicsense.reference;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.department.dto.DepartmentDtos.ServiceAreaUpdateRequest;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaRequest;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaResponse;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Service areas can only be read and changed for the caller's own department. */
@RestController
@RequestMapping("/department/service-areas")
@PreAuthorize("@departmentGuard.isVerified(authentication)")
public class DepartmentServiceAreaController {

    private final ReferenceService referenceService;

    public DepartmentServiceAreaController(ReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    @GetMapping
    List<ServiceAreaResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return referenceService.myServiceAreas(principal);
    }

    @PostMapping
    ServiceAreaResponse create(
            @Valid @RequestBody ServiceAreaRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return referenceService.createServiceArea(principal, request);
    }

    @PatchMapping("/{id}")
    ServiceAreaResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceAreaUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return referenceService.updateServiceArea(id, principal, request);
    }

    @DeleteMapping("/{id}")
    Map<String, String> deactivate(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        referenceService.deactivateServiceArea(id, principal);
        return Map.of("status", "INACTIVE");
    }
}
