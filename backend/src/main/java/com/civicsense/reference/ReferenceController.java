package com.civicsense.reference;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.reference.dto.ReferenceDtos.CategoryPatchRequest;
import com.civicsense.reference.dto.ReferenceDtos.CategoryRequest;
import com.civicsense.reference.dto.ReferenceDtos.CategoryResponse;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionRequest;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionResponse;

import jakarta.validation.Valid;

/** Category/jurisdiction reads are public metadata; writes are super-admin only. */
@RestController
public class ReferenceController {

    private final ReferenceService referenceService;

    public ReferenceController(ReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    @GetMapping("/meta/categories")
    List<CategoryResponse> categories(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return referenceService.categoryTree(includeInactive);
    }

    @GetMapping("/meta/jurisdictions")
    List<JurisdictionResponse> jurisdictions(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return referenceService.jurisdictions(includeInactive);
    }

    @PostMapping("/admin/reference/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return referenceService.createCategory(request);
    }

    @PatchMapping("/admin/reference/categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    CategoryResponse patchCategory(
            @PathVariable UUID id, @Valid @RequestBody CategoryPatchRequest request) {
        return referenceService.patchCategory(id, request);
    }

    @PostMapping("/admin/reference/jurisdictions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    JurisdictionResponse createJurisdiction(@Valid @RequestBody JurisdictionRequest request) {
        return referenceService.createJurisdiction(request);
    }
}
