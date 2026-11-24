package com.civicsense.department;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.audit.Audited;
import com.civicsense.complaint.exception.ComplaintExceptions.NotOwnerException;
import com.civicsense.department.dto.DepartmentDtos.CapabilityRequest;
import com.civicsense.department.dto.DepartmentDtos.CapabilityResponse;
import com.civicsense.department.dto.DepartmentDtos.DepartmentProfileResponse;
import com.civicsense.department.dto.DepartmentDtos.DepartmentProfileUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.TeamMemberResponse;
import com.civicsense.department.dto.DepartmentDtos.TeamUpdateRequest;
import com.civicsense.department.exception.DepartmentExceptions.InvalidDepartmentSettingException;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.DepartmentIssueCategory;
import com.civicsense.domain.DepartmentIssueCategoryId;
import com.civicsense.domain.IssueCategory;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.repository.DepartmentIssueCategoryRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.security.AuthenticatedUser;

/** Profile, capability and team settings that a verified department admin may change. */
@Service
public class DepartmentManagementService {

    private final DepartmentRepository departments;
    private final UserRepository users;
    private final DepartmentAdminRepository departmentAdmins;
    private final DepartmentIssueCategoryRepository departmentCategories;
    private final IssueCategoryRepository categories;

    public DepartmentManagementService(
            DepartmentRepository departments,
            UserRepository users,
            DepartmentAdminRepository departmentAdmins,
            DepartmentIssueCategoryRepository departmentCategories,
            IssueCategoryRepository categories) {
        this.departments = departments;
        this.users = users;
        this.departmentAdmins = departmentAdmins;
        this.departmentCategories = departmentCategories;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public DepartmentProfileResponse profile(AuthenticatedUser principal) {
        return toProfile(ownDepartment(principal));
    }

    @Transactional
    @Audited(action = "DEPARTMENT_PROFILE_UPDATE", entityType = "department")
    public DepartmentProfileResponse updateProfile(
            AuthenticatedUser principal, DepartmentProfileUpdateRequest request) {
        Department department = ownDepartment(principal);
        if (request.description() != null) {
            department.setDescription(request.description().trim());
        }
        if (request.contactNumber() != null) {
            department.setContactNumber(request.contactNumber().trim());
        }
        if (request.officeAddress() != null) {
            department.setOfficeAddress(request.officeAddress().trim());
        }
        department.setAcceptsComplaints(Boolean.TRUE.equals(request.acceptsComplaints()));
        department.setAutoAssignEnabled(Boolean.TRUE.equals(request.autoAssignEnabled()));
        return toProfile(departments.save(department));
    }

    @Transactional(readOnly = true)
    public List<CapabilityResponse> capabilities(AuthenticatedUser principal) {
        Department department = ownDepartment(principal);
        List<CapabilityResponse> result = new ArrayList<>();
        for (DepartmentIssueCategory link : departmentCategories
                .findAllByIdDepartmentIdOrderByPriorityAsc(department.getId())) {
            IssueCategory category = categories.findById(link.getId().getCategoryId()).orElse(null);
            if (category == null || !category.isActive()) {
                continue;
            }
            result.add(new CapabilityResponse(category.getId(), category.getCode(), category.getName(),
                    link.isPrimary(), link.getPriority()));
        }
        return result;
    }

    @Transactional
    @Audited(action = "DEPARTMENT_CAPABILITIES_UPDATE", entityType = "department")
    public List<CapabilityResponse> replaceCapabilities(
            AuthenticatedUser principal, List<CapabilityRequest> requests) {
        Department department = ownDepartment(principal);
        if (requests == null || requests.isEmpty()) {
            throw new InvalidDepartmentSettingException("At least one capability is required");
        }
        if (requests.stream().filter(CapabilityRequest::primary).count() != 1) {
            throw new InvalidDepartmentSettingException("Exactly one primary capability is required");
        }
        Set<UUID> categoryIds = new HashSet<>();
        for (CapabilityRequest request : requests) {
            if (!categoryIds.add(request.categoryId())) {
                throw new InvalidDepartmentSettingException("Duplicate capabilities are not allowed");
            }
            IssueCategory category = categories.findById(request.categoryId())
                    .orElseThrow(() -> new InvalidDepartmentSettingException("Unknown capability category"));
            if (!category.isActive()) {
                throw new InvalidDepartmentSettingException("Inactive categories cannot be capabilities");
            }
        }
        List<DepartmentIssueCategory> existing = departmentCategories
                .findAllByIdDepartmentIdOrderByPriorityAsc(department.getId());
        departmentCategories.deleteAll(existing);
        for (CapabilityRequest request : requests) {
            DepartmentIssueCategory link = new DepartmentIssueCategory();
            DepartmentIssueCategoryId id = new DepartmentIssueCategoryId(
                    department.getId(), request.categoryId());
            link.setId(id);
            link.setPrimary(request.primary());
            link.setPriority(request.priority());
            departmentCategories.save(link);
        }
        return capabilities(principal);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> team(AuthenticatedUser principal) {
        Department department = ownDepartment(principal);
        List<TeamMemberResponse> result = new ArrayList<>();
        for (DepartmentAdmin admin : departmentAdmins.findByDepartmentId(department.getId())) {
            User user = users.findById(admin.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            result.add(new TeamMemberResponse(user.getId(), user.getFullName(), user.getEmail(),
                    admin.getAdminLevel(), admin.isPrimaryAdmin(), admin.getTitle(),
                    user.getAccountStatus()));
        }
        return result;
    }

    @Transactional
    @Audited(action = "DEPARTMENT_TEAM_UPDATE", entityType = "department")
    public List<TeamMemberResponse> updateTeamMember(
            AuthenticatedUser principal, UUID userId, TeamUpdateRequest request) {
        DepartmentAdmin caller = departmentAdmins.findByUserId(principal.id())
                .orElseThrow(NotOwnerException::new);
        if (caller.getAdminLevel() != AdminLevel.OWNER) {
            throw new NotOwnerException();
        }
        DepartmentAdmin target = departmentAdmins.findByUserId(userId)
                .orElseThrow(NotOwnerException::new);
        if (!principal.departmentId().equals(target.getDepartmentId())
                || target.getAdminLevel() == AdminLevel.OWNER
                || request.adminLevel() == AdminLevel.OWNER) {
            throw new InvalidDepartmentSettingException("This team member cannot be changed");
        }
        if (Boolean.TRUE.equals(request.primaryAdmin())) {
            departmentAdmins.findByDepartmentId(target.getDepartmentId()).forEach(member -> {
                if (!member.getUserId().equals(userId)) {
                    member.setPrimaryAdmin(false);
                }
            });
        } else if (target.isPrimaryAdmin()) {
            long primaryCount = departmentAdmins.findByDepartmentId(target.getDepartmentId()).stream()
                    .filter(DepartmentAdmin::isPrimaryAdmin).count();
            if (primaryCount <= 1) {
                throw new InvalidDepartmentSettingException("A department must retain a primary admin");
            }
        }
        target.setAdminLevel(request.adminLevel());
        target.setPrimaryAdmin(Boolean.TRUE.equals(request.primaryAdmin()));
        departmentAdmins.save(target);
        return team(principal);
    }

    private Department ownDepartment(AuthenticatedUser principal) {
        if (!principal.isVerifiedDepartmentAdmin()) {
            throw new NotOwnerException();
        }
        return departments.findById(principal.departmentId()).orElseThrow(NotOwnerException::new);
    }

    private static DepartmentProfileResponse toProfile(Department department) {
        return new DepartmentProfileResponse(
                department.getId(), department.getName(), department.getDepartmentCode(),
                department.getDepartmentType(), department.getDescription(),
                department.getOfficialEmail(), department.getContactNumber(),
                department.getOfficeAddress(), department.getCity(), department.getState(),
                department.getCountry(), department.getVerificationStatus(), department.isActive(),
                department.isAcceptsComplaints(), department.isAutoAssignEnabled(),
                department.getRoutingPriority(), department.getMaxOpenComplaints());
    }
}
