package com.civicsense.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.audit.Audited;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryInUseException;
import com.civicsense.complaint.exception.ComplaintExceptions.CategoryNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.DuplicateReferenceException;
import com.civicsense.complaint.exception.ComplaintExceptions.JurisdictionNotFoundException;
import com.civicsense.complaint.exception.ComplaintExceptions.ServiceAreaNotFoundException;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.Jurisdiction;
import com.civicsense.domain.ServiceArea;
import com.civicsense.department.dto.DepartmentDtos.ServiceAreaUpdateRequest;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.reference.dto.ReferenceDtos.CategoryPatchRequest;
import com.civicsense.reference.dto.ReferenceDtos.CategoryRequest;
import com.civicsense.reference.dto.ReferenceDtos.CategoryResponse;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionRequest;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionResponse;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaRequest;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaResponse;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.repository.JurisdictionRepository;
import com.civicsense.repository.ServiceAreaRepository;
import com.civicsense.security.AuthenticatedUser;

/** Reference data is global; service areas are strictly owned by one department. */
@Service
public class ReferenceService {

    private final IssueCategoryRepository categories;
    private final JurisdictionRepository jurisdictions;
    private final ServiceAreaRepository serviceAreas;
    private final DepartmentRepository departments;
    private final ComplaintRepository complaints;
    private final GeometryValidator geometryValidator;

    public ReferenceService(
            IssueCategoryRepository categories,
            JurisdictionRepository jurisdictions,
            ServiceAreaRepository serviceAreas,
            DepartmentRepository departments,
            ComplaintRepository complaints,
            GeometryValidator geometryValidator) {
        this.categories = categories;
        this.jurisdictions = jurisdictions;
        this.serviceAreas = serviceAreas;
        this.departments = departments;
        this.complaints = complaints;
        this.geometryValidator = geometryValidator;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categoryTree(boolean includeInactive) {
        List<IssueCategory> all = includeInactive
                ? categories.findAllByOrderByDisplayOrderAsc()
                : categories.findAllByActiveTrueOrderByDisplayOrderAsc();
        Map<UUID, CategoryResponse> byId = new LinkedHashMap<>();
        for (IssueCategory category : all) {
            byId.put(category.getId(), toResponse(category));
        }
        List<CategoryResponse> roots = new ArrayList<>();
        for (IssueCategory category : all) {
            CategoryResponse response = byId.get(category.getId());
            UUID parentId = category.getParentCategory() == null ? null : category.getParentCategory().getId();
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(response);
            } else {
                byId.get(parentId).children().add(response);
            }
        }
        return roots;
    }

    @Transactional
    @Audited(action = "CATEGORY_CREATE", entityType = "issue_category")
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categories.findByCode(request.code()).isPresent()) {
            throw new DuplicateReferenceException("Category code already exists: " + request.code());
        }
        IssueCategory category = new IssueCategory();
        category.setCode(request.code().trim().toUpperCase());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setDisplayOrder(request.displayOrder());
        category.setActive(true);
        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            category.setParentCategory(resolveCategory(request.parentCode()));
        }
        return toResponse(categories.save(category));
    }

    @Transactional
    @Audited(action = "CATEGORY_UPDATE", entityType = "issue_category")
    public CategoryResponse patchCategory(UUID id, CategoryPatchRequest request) {
        IssueCategory category = categories.findById(id).orElseThrow(CategoryNotFoundException::new);
        if (request.name() != null) {
            category.setName(request.name().trim());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
        if (request.color() != null) {
            category.setColor(request.color());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            if (!request.active()
                    && (complaints.countByCategoryId(id) > 0 || complaints.countBySubcategoryId(id) > 0)) {
                throw new CategoryInUseException();
            }
            category.setActive(request.active());
        }
        return toResponse(categories.save(category));
    }

    @Transactional(readOnly = true)
    public List<JurisdictionResponse> jurisdictions(boolean includeInactive) {
        List<Jurisdiction> all = includeInactive
                ? jurisdictions.findAllByOrderByCodeAsc()
                : jurisdictions.findAllByActiveTrueOrderByCodeAsc();
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional
    @Audited(action = "JURISDICTION_CREATE", entityType = "jurisdiction")
    public JurisdictionResponse createJurisdiction(JurisdictionRequest request) {
        if (jurisdictions.findByCode(request.code()).isPresent()) {
            throw new DuplicateReferenceException("Jurisdiction code already exists: " + request.code());
        }
        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setCode(request.code().trim().toUpperCase());
        jurisdiction.setName(request.name().trim());
        jurisdiction.setLevel(request.level().trim().toUpperCase());
        jurisdiction.setCity(request.city());
        jurisdiction.setState(request.state());
        jurisdiction.setCountry(request.country());
        jurisdiction.setActive(true);
        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            jurisdiction.setParent(jurisdictions.findByCode(request.parentCode())
                    .orElseThrow(JurisdictionNotFoundException::new));
        }
        return toResponse(jurisdictions.save(jurisdiction));
    }

    @Transactional(readOnly = true)
    public List<ServiceAreaResponse> myServiceAreas(AuthenticatedUser principal) {
        requireOwnDepartment(principal);
        return serviceAreas.findAllByDepartmentIdAndActiveTrue(principal.departmentId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @Audited(action = "SERVICE_AREA_CREATE", entityType = "service_area")
    public ServiceAreaResponse createServiceArea(AuthenticatedUser principal, ServiceAreaRequest request) {
        requireOwnDepartment(principal);
        geometryValidator.validateMultiPolygon(request.boundaryWkt());
        ServiceArea area = new ServiceArea();
        area.setDepartment(departments.getReferenceById(principal.departmentId()));
        area.setName(request.name().trim());
        area.setBoundaryWkt(request.boundaryWkt().trim());
        area.setCoversWard(request.coversWard());
        area.setCoversZone(request.coversZone());
        area.setActive(true);
        if (request.jurisdictionId() != null) {
            area.setJurisdiction(jurisdictions.findById(request.jurisdictionId())
                    .orElseThrow(JurisdictionNotFoundException::new));
        }
        return toResponse(serviceAreas.save(area));
    }

    @Transactional
    @Audited(action = "SERVICE_AREA_UPDATE", entityType = "service_area")
    public ServiceAreaResponse updateServiceArea(
            UUID id, AuthenticatedUser principal, ServiceAreaUpdateRequest request) {
        requireOwnDepartment(principal);
        geometryValidator.validateMultiPolygon(request.boundaryWkt());
        ServiceArea area = serviceAreas.findByIdAndDepartmentId(id, principal.departmentId())
                .orElseThrow(ServiceAreaNotFoundException::new);
        area.setName(request.name().trim());
        area.setBoundaryWkt(request.boundaryWkt().trim());
        area.setCoversWard(request.coversWard());
        area.setCoversZone(request.coversZone());
        return toResponse(serviceAreas.save(area));
    }

    @Transactional
    @Audited(action = "SERVICE_AREA_DEACTIVATE", entityType = "service_area")
    public void deactivateServiceArea(UUID id, AuthenticatedUser principal) {
        requireOwnDepartment(principal);
        ServiceArea area = serviceAreas.findByIdAndDepartmentId(id, principal.departmentId())
                .orElseThrow(ServiceAreaNotFoundException::new);
        area.setActive(false);
    }

    private static void requireOwnDepartment(AuthenticatedUser principal) {
        if (!principal.isVerifiedDepartmentAdmin()) {
            throw new ServiceAreaNotFoundException();
        }
    }

    private IssueCategory resolveCategory(String code) {
        return categories.findByCode(code).orElseThrow(CategoryNotFoundException::new);
    }

    private CategoryResponse toResponse(IssueCategory category) {
        return new CategoryResponse(
                category.getId(), category.getCode(), category.getName(), category.getDescription(),
                category.getIcon(), category.getColor(), category.getDisplayOrder(), category.isActive(),
                new ArrayList<>());
    }

    private JurisdictionResponse toResponse(Jurisdiction jurisdiction) {
        return new JurisdictionResponse(
                jurisdiction.getId(), jurisdiction.getCode(), jurisdiction.getName(),
                jurisdiction.getLevel(), jurisdiction.getCity(), jurisdiction.getState(),
                jurisdiction.getCountry(),
                jurisdiction.getParent() == null ? null : jurisdiction.getParent().getCode());
    }

    private ServiceAreaResponse toResponse(ServiceArea area) {
        Department department = area.getDepartment();
        return new ServiceAreaResponse(
                area.getId(), department == null ? null : department.getId(), area.getName(),
                area.getCoversWard(), area.getCoversZone(),
                area.getJurisdiction() == null ? null : area.getJurisdiction().getCode(),
                area.isActive(), area.getCreatedAt());
    }
}
