package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.civicsense.auth.dto.AuthResponses.VerifyDepartmentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.EvidenceRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReasonRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignDecisionRequest;
import com.civicsense.complaint.dto.ComplaintDtos.ReassignRequestRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.CapabilityRequest;
import com.civicsense.department.dto.DepartmentDtos.CapabilityUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.DepartmentProfileUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.ServiceAreaUpdateRequest;
import com.civicsense.department.dto.DepartmentDtos.TeamUpdateRequest;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.UploadStatus;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.User;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaRequest;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;

/** Phase 7 complaint actions, notes, management, service areas and evidence authorization. */
class Phase7DepartmentOperationsIntegrationTest extends Phase7TestSupport {

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void setUp() {
        resetAndSeed();
        when(storage.presignGet(anyString(), any(Duration.class)))
                .thenReturn(new PresignedUrl("https://storage.test/view",
                        Instant.now().plusSeconds(300), Map.of()));
    }

    @Test
    void unverifiedDepartmentAdminIsDeniedTheWholeDepartmentSurface() throws Exception {
        User unverifiedAdmin = createUser("p7-unverified-admin@example.com",
                com.civicsense.domain.Role.DEPARTMENT_ADMIN, unverifiedDept,
                AdminLevel.OWNER, true);
        String token = tokenFor(unverifiedAdmin, unverifiedDept);

        for (String path : List.of("/department/me", "/department/overview",
                "/department/activity", "/department/complaints",
                "/department/complaints/queue", "/department/analytics",
                "/department/profile", "/department/capabilities", "/department/team",
                "/department/notifications", "/department/service-areas")) {
            mockMvc.perform(get(BASE + path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(BASE + "/department/complaints/" + UUID.randomUUID() + "/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        for (String wrongRoleToken : List.of(superAdminToken, citizenAToken)) {
            mockMvc.perform(get(BASE + "/department/me")
                            .header("Authorization", "Bearer " + wrongRoleToken))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(BASE + "/department/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveVerifiedDepartmentShouldBeDeniedDepartmentRoutes() throws Exception {
        setDepartmentActive(deptA.getId(), false);

        mockMvc.perform(get(BASE + "/department/me")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptHoldResumeAndRejectFollowTheStateMachineAndStayDepartmentScoped() throws Exception {
        UUID mine = submitComplaint(citizenAToken, "Pothole blocking the cycle lane needs repair");
        UUID theirs = submitComplaint(citizenBToken, "Pothole outside the other department boundary");
        assign(mine, deptA.getId());
        assign(theirs, deptB.getId());

        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/accept")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        assertThat(complaints.findById(mine).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.ACKNOWLEDGED);
        assertThat(statusHistory.findAllByComplaintIdOrderByCreatedAtAsc(mine))
                .anySatisfy(entry -> {
                    assertThat(entry.getFromStatus()).isEqualTo(ComplaintStatus.ASSIGNED);
                    assertThat(entry.getToStatus()).isEqualTo(ComplaintStatus.ACKNOWLEDGED);
                    assertThat(entry.getActorRole()).isEqualTo("DEPT_ADMIN");
                    assertThat(entry.getChangedBy().getId()).isEqualTo(deptAdminA.getId());
                });

        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED, "fixed"))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.ON_HOLD, " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.ON_HOLD,
                                "Waiting for the utility company to mark the site"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.IN_PROGRESS, null))))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.ON_HOLD,
                                "Heavy rain stopped work on the site"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/reject")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReasonRequest("Outside our maintenance jurisdiction"))))
                .andExpect(status().isOk());

        assertThat(complaints.findById(mine).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.REJECTED);
        assertThat(statusHistory.findAllByComplaintIdOrderByCreatedAtAsc(mine))
                .extracting(entry -> entry.getToStatus())
                .contains(ComplaintStatus.ON_HOLD, ComplaintStatus.IN_PROGRESS,
                        ComplaintStatus.REJECTED);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.COMPLAINT_REJECTED);
        assertThat(complaints.findById(theirs).orElseThrow().getStatus())
                .isNotEqualTo(ComplaintStatus.REJECTED);
    }

    @Test
    void internalNotesRemainPrivateToTheOwningDepartmentAndNeverReachCitizens() throws Exception {
        UUID mine = submitComplaint(citizenAToken, "Loose manhole cover on the public footpath");
        UUID theirs = submitComplaint(citizenBToken, "Loose manhole cover in another department");
        assign(mine, deptA.getId());
        assign(theirs, deptB.getId());

        String privateBody = "Contractor visit scheduled for tomorrow morning";
        String noteId = mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new com.civicsense.complaint.dto.ComplaintDtos.NoteRequest(privateBody))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(privateBody))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(noteId).get("id").asText()).isNotBlank();

        mockMvc.perform(get(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value(privateBody));
        mockMvc.perform(get(BASE + "/department/complaints/" + mine)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalNotes[0].body").value(privateBody));

        mockMvc.perform(get(BASE + "/citizen/complaints/" + mine)
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalNotes.length()").value(0));

        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", " "))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + deptBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "A note from another department"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/department/complaints/" + mine + "/notes")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/department/complaints/" + theirs + "/notes")
                        .header("Authorization", "Bearer " + deptBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "Water works is handling this"))))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/department/complaints/" + theirs + "/notes")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isNotFound());
        assertThat(internalNotes.findAllByComplaintIdAndDepartmentIdOrderByCreatedAtAsc(
                mine, deptA.getId())).hasSize(1);
        assertThat(internalNotes.findAllByComplaintIdAndDepartmentIdOrderByCreatedAtAsc(
                mine, deptB.getId())).isEmpty();
    }

    @Test
    void evidenceAttachAndViewRequireOwnershipVerifiedStateAndAllowedKind() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Deep pothole damaging vehicles on the main road");
        UUID otherComplaintId = submitComplaint(citizenAToken, "Second pothole for invalid evidence cases");
        assign(complaintId, deptA.getId());
        assign(otherComplaintId, deptA.getId());

        for (UUID id : List.of(complaintId, otherComplaintId)) {
            mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                            .header("Authorization", "Bearer " + deptAToken))
                    .andExpect(status().isOk());
            mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                            .header("Authorization", "Bearer " + deptAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new StatusUpdateRequest(ComplaintStatus.IN_PROGRESS, null))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(patch(BASE + "/department/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED,
                                "Repairs were completed this morning"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("AFTER")));

        UUID beforeImage = stageImage(deptAdminA.getId(), ImageKind.BEFORE,
                UploadStatus.VERIFIED, null);
        attachEvidence(complaintId, beforeImage, deptAToken, status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED,
                                "Repairs were completed this morning"))))
                .andExpect(status().isBadRequest());

        UUID afterImage = stageImage(deptAdminA.getId(), ImageKind.AFTER,
                UploadStatus.VERIFIED, null);
        String attachBody = attachEvidence(complaintId, afterImage, deptAToken, status().isOk());
        assertThat(objectMapper.readTree(attachBody).get("status").asText()).isEqualTo("ATTACHED");
        assertThat(images.findById(afterImage).orElseThrow().getComplaint().getId())
                .isEqualTo(complaintId);

        mockMvc.perform(patch(BASE + "/department/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED,
                                "Repairs were completed this morning"))))
                .andExpect(status().isOk());
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.RESOLVED);

        UUID citizenKind = stageImage(deptAdminA.getId(), ImageKind.CITIZEN,
                UploadStatus.VERIFIED, null);
        attachEvidence(otherComplaintId, citizenKind, deptAToken, status().isBadRequest());
        UUID pending = stageImage(deptAdminA.getId(), ImageKind.AFTER,
                UploadStatus.PENDING, null);
        attachEvidence(otherComplaintId, pending, deptAToken, status().isBadRequest());
        UUID foreignOwner = stageImage(citizenA.getId(), ImageKind.AFTER,
                UploadStatus.VERIFIED, null);
        attachEvidence(otherComplaintId, foreignOwner, deptAToken, status().isForbidden());
        attachEvidence(otherComplaintId, afterImage, deptAToken, status().isBadRequest());
        attachEvidence(otherComplaintId, afterImage, deptBToken, status().isNotFound());

        mockMvc.perform(get(BASE + "/uploads/" + afterImage + "/url")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.test/view"));
        mockMvc.perform(get(BASE + "/uploads/" + afterImage + "/url")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/uploads/" + afterImage + "/url")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/uploads/" + afterImage + "/url")
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/uploads/" + pending + "/url")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileUpdatesPersistOnlyOwnDepartmentSettingsAndValidateRequiredFlags() throws Exception {
        mockMvc.perform(get(BASE + "/department/profile")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deptA.getId().toString()))
                .andExpect(jsonPath("$.departmentCode").value("ROAD-A"))
                .andExpect(jsonPath("$.acceptsComplaints").value(true))
                .andExpect(jsonPath("$.autoAssignEnabled").value(true));

        mockMvc.perform(patch(BASE + "/department/profile")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DepartmentProfileUpdateRequest(
                                "  Road maintenance desk  ", "011-22223333", "12 Civic Plaza",
                                false, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Road maintenance desk"))
                .andExpect(jsonPath("$.contactNumber").value("011-22223333"))
                .andExpect(jsonPath("$.officeAddress").value("12 Civic Plaza"))
                .andExpect(jsonPath("$.acceptsComplaints").value(false))
                .andExpect(jsonPath("$.autoAssignEnabled").value(false));

        var saved = departments.findById(deptA.getId()).orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("Road maintenance desk");
        assertThat(saved.isAcceptsComplaints()).isFalse();
        assertThat(saved.isAutoAssignEnabled()).isFalse();

        mockMvc.perform(patch(BASE + "/department/profile")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "Missing both flags"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/profile")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new DepartmentProfileUpdateRequest(null, null, null, true, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void capabilitiesRequireOnePrimaryAndUniqueActiveCategories() throws Exception {
        mockMvc.perform(get(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        CapabilityRequest road = new CapabilityRequest(roadCategory.getId(), true, 1);
        CapabilityRequest pothole = new CapabilityRequest(potholeCategory.getId(), false, 2);
        mockMvc.perform(put(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CapabilityUpdateRequest(List.of(road, pothole)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryCode").value("ROAD"))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].categoryCode").value("POTHOLE"))
                .andExpect(jsonPath("$[1].primary").value(false));
        assertThat(departmentCategories
                .findAllByIdDepartmentIdOrderByPriorityAsc(deptA.getId())).hasSize(2);

        mockMvc.perform(put(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CapabilityUpdateRequest(List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CapabilityUpdateRequest(List.of(
                                new CapabilityRequest(roadCategory.getId(), false, 1),
                                new CapabilityRequest(potholeCategory.getId(), false, 2))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CapabilityUpdateRequest(List.of(
                                new CapabilityRequest(roadCategory.getId(), true, 1),
                                new CapabilityRequest(roadCategory.getId(), false, 2))))))
                .andExpect(status().isBadRequest());

        IssueCategory inactive = new IssueCategory();
        inactive.setCode("INACTIVE");
        inactive.setName("Inactive category");
        inactive.setDisplayOrder(9);
        inactive.setActive(false);
        categories.save(inactive);
        mockMvc.perform(put(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CapabilityUpdateRequest(List.of(
                                new CapabilityRequest(inactive.getId(), true, 1))))))
                .andExpect(status().isBadRequest());

        assertThat(departmentCategories
                .findAllByIdDepartmentIdOrderByPriorityAsc(deptA.getId())).hasSize(2);

        mockMvc.perform(get(BASE + "/department/capabilities")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teamChangesRequireOwnerCannotCreateOwnersAndKeepAPrimaryAdmin() throws Exception {
        User operator = createUser("p7-operator@example.com",
                com.civicsense.domain.Role.DEPARTMENT_ADMIN, deptA, AdminLevel.OPERATOR, false);
        User secondOperator = createUser("p7-second-operator@example.com",
                com.civicsense.domain.Role.DEPARTMENT_ADMIN, deptA, AdminLevel.OPERATOR, false);

        mockMvc.perform(get(BASE + "/department/team")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].email", containsInAnyOrder(
                        deptAdminA.getEmail(), operator.getEmail(), secondOperator.getEmail())))
                .andExpect(jsonPath("$[?(@.email=='p3-admin-a@example.com')].primaryAdmin", hasItem(true)));

        mockMvc.perform(patch(BASE + "/department/team/" + operator.getId())
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.ADMIN, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='p7-operator@example.com')].adminLevel",
                        hasItem(AdminLevel.ADMIN.name())))
                .andExpect(jsonPath("$[?(@.email=='p7-operator@example.com')].primaryAdmin",
                        hasItem(true)))
                .andExpect(jsonPath("$[?(@.email=='p3-admin-a@example.com')].primaryAdmin",
                        hasItem(false)));

        mockMvc.perform(patch(BASE + "/department/team/" + deptAdminA.getId())
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.ADMIN, true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/team/" + operator.getId())
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.OWNER, true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/team/" + operator.getId())
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.ADMIN, false))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/team/" + operator.getId())
                        .header("Authorization", "Bearer " + deptBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.ADMIN, true))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/team/" + secondOperator.getId())
                        .header("Authorization", "Bearer " + tokenFor(operator, deptA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new TeamUpdateRequest(AdminLevel.ADMIN, false))))
                .andExpect(status().isForbidden());

        var savedOperator = departmentAdmins.findByUserId(operator.getId()).orElseThrow();
        assertThat(savedOperator.getAdminLevel()).isEqualTo(AdminLevel.ADMIN);
        assertThat(savedOperator.isPrimaryAdmin()).isTrue();
        assertThat(departmentAdmins.findByUserId(deptAdminA.getId()).orElseThrow().isPrimaryAdmin())
                .isFalse();
    }

    @Test
    void serviceAreasAreValidatedDepartmentScopedAndSoftDeleted() throws Exception {
        String polygonA = "POLYGON((77.1 28.5, 77.3 28.5, 77.3 28.7, 77.1 28.7, 77.1 28.5))";
        String polygonB = "POLYGON((77.4 28.5, 77.6 28.5, 77.6 28.7, 77.4 28.7, 77.4 28.5))";

        UUID areaA = createServiceArea(deptAToken, new ServiceAreaRequest(
                "Ward 12 Roads", polygonA, "WARD-12", "NORTH", ward12.getId()));
        UUID areaB = createServiceArea(deptBToken, new ServiceAreaRequest(
                "Water Works Zone", polygonB, "WARD-09", "SOUTH", null));

        mockMvc.perform(get(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(areaA.toString()))
                .andExpect(jsonPath("$[0].departmentId").value(deptA.getId().toString()))
                .andExpect(jsonPath("$[0].jurisdictionCode").value("WARD-12"));
        mockMvc.perform(get(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(areaB.toString()));
        mockMvc.perform(get(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(BASE + "/department/service-areas/" + areaA)
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ServiceAreaUpdateRequest(
                                "Ward 12 Updated", polygonB, "WARD-12", "EAST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ward 12 Updated"))
                .andExpect(jsonPath("$.coversZone").value("EAST"));
        assertThat(serviceAreas.findById(areaA).orElseThrow().getBoundaryWkt())
                .isEqualTo(polygonB);

        mockMvc.perform(patch(BASE + "/department/service-areas/" + areaA)
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ServiceAreaUpdateRequest(
                                "Bad geometry", "NOT-A-POLYGON", null, null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "/department/service-areas/" + areaA)
                        .header("Authorization", "Bearer " + deptBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ServiceAreaUpdateRequest(
                                "Not yours", polygonA, null, null))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(BASE + "/department/service-areas/" + areaB)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/department/service-areas/" + areaA)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(serviceAreas.findById(areaA).orElseThrow().isActive()).isFalse();
        assertThat(serviceAreas.findById(areaB).orElseThrow().isActive()).isTrue();
    }

    @Test
    void superAdminVerificationAndReassignmentRoutesRemainIntact() throws Exception {
        User pendingAdmin = createUser("p7-pending-admin@example.com",
                com.civicsense.domain.Role.DEPARTMENT_ADMIN, unverifiedDept,
                AdminLevel.OWNER, true);
        String pendingToken = tokenFor(pendingAdmin, unverifiedDept);

        mockMvc.perform(get(BASE + "/admin/departments")
                        .header("Authorization", "Bearer " + pendingToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/admin/departments/" + unverifiedDept.getId() + "/verify")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VerifyDepartmentRequest(VerificationStatus.VERIFIED,
                                "Documents verified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mockMvc.perform(get(BASE + "/department/me")
                        .header("Authorization", "Bearer " + pendingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        UUID complaintId = submitComplaint(citizenAToken, "Road collapse needs an urgent department review");
        assign(complaintId, deptA.getId());
        mockMvc.perform(post(BASE + "/department/complaints/" + complaintId + "/reassign-request")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReassignRequestRequest(deptB.getId(),
                                "This belongs to the water works department"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get(BASE + "/admin/reassignment-requests")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/admin/reassignment-requests")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        UUID requestId = reassignmentRequests
                .findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus.PENDING).get(0).getId();
        mockMvc.perform(post(BASE + "/admin/reassignment-requests/" + requestId + "/review")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReassignDecisionRequest(
                                ReassignmentStatus.APPROVED, "Approved after review"))))
                .andExpect(status().isOk());

        assertThat(complaints.findById(complaintId).orElseThrow().getRelatedDepartment().getId())
                .isEqualTo(deptB.getId());
        mockMvc.perform(get(BASE + "/department/complaints/" + complaintId)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/department/complaints/" + complaintId)
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk());
    }

    private UUID createServiceArea(String token, ServiceAreaRequest request) throws Exception {
        String body = mockMvc.perform(post(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String attachEvidence(UUID complaintId, UUID imageId, String token,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return mockMvc.perform(post(BASE + "/department/complaints/" + complaintId + "/evidence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new EvidenceRequest(imageId))))
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString();
    }
}
