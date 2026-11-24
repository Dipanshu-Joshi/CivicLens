package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.civicsense.complaint.dto.ComplaintDtos.FeedbackRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.UploadStatus;

class ComplaintLifecycleIntegrationTest extends Phase3TestSupport {

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void citizenCanSubmitAndComplaintGetsNumberHistoryAndNotification() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Large pothole on the main road near the school.");

        var complaint = complaints.findById(id).orElseThrow();
        assertThat(complaint.getComplaintNumber()).startsWith("CIV-");
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.SUBMITTED);
        assertThat(complaint.getLocationWkt()).contains("77.209");
        assertThat(complaint.getResolutionDueAt()).isAfter(Instant.now());
        assertThat(statusHistory.findAllByComplaintIdOrderByCreatedAtAsc(id))
                .extracting(entry -> entry.getToStatus())
                .containsExactly(ComplaintStatus.SUBMITTED);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(superAdmin.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.COMPLAINT_SUBMITTED);
        assertThat(outboxEvents.findAll()).isNotEmpty();
    }

    @Test
    void citizenCannotSeeAnotherCitizensComplaint() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Broken street light outside my home.");

        mockMvc.perform(get(BASE + "/citizen/complaints/" + id)
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(BASE + "/citizen/complaints/" + id)
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintNumber").isNotEmpty());
    }

    @Test
    void departmentsAreStrictlyIsolatedForReadsAndWrites() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Garbage pile uncollected for several days.");
        assign(id, deptA.getId());

        mockMvc.perform(get(BASE + "/department/complaints/" + id)
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].departmentId").value(deptA.getId().toString()));
    }

    @Test
    void superAdminCanAssignOnlyEligibleDepartments() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Road collapse blocking the intersection.");

        mockMvc.perform(post(BASE + "/admin/complaints/" + id + "/assign")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", unverifiedDept.getId().toString(),
                                "reason", "This department is still pending verification"))))
                .andExpect(status().isBadRequest());

        assign(id, deptA.getId());

        var complaint = complaints.findById(id).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.ASSIGNED);
        assertThat(complaint.getRelatedDepartment().getId()).isEqualTo(deptA.getId());
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isPresent();
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(deptAdminA.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.COMPLAINT_ASSIGNED);
    }

    @Test
    void invalidTransitionAndMissingReasonReturnProblemDetails() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Water leakage flooding the pedestrian path.");
        assign(id, deptA.getId());

        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED, "fixed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.ON_HOLD, " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void resolutionRequiresVerifiedEvidenceThenCompletesFullLifecycle() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Deep pothole is causing damage to vehicles.");
        assign(id, deptA.getId());
        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.IN_PROGRESS, "Work started"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED,
                                "Repairs completed this morning"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("AFTER")));

        addVerifiedImage(id);
        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED,
                                "Repairs completed this morning"))))
                .andExpect(status().isOk());

        var complaint = complaints.findById(id).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(complaint.getResolvedAt()).isNotNull();
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.COMPLAINT_RESOLVED);

        mockMvc.perform(post(BASE + "/citizen/complaints/" + id + "/feedback")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FeedbackRequest(5, "Fixed quickly", true, null))))
                .andExpect(status().isOk());
        assertThat(complaints.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(feedback.findByComplaintId(id)).isPresent();
    }

    @Test
    void disputedFeedbackReopensComplaint() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Overflowing drain near the market.");
        assign(id, deptA.getId());
        acceptAndResolve(id);

        mockMvc.perform(post(BASE + "/citizen/complaints/" + id + "/feedback")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FeedbackRequest(2, "Not fixed", false,
                                "The water is still overflowing after heavy rain"))))
                .andExpect(status().isOk());
        assertThat(complaints.findById(id).orElseThrow().getStatus()).isEqualTo(ComplaintStatus.REOPENED);
    }

    @Test
    void internalNotesStayWithinTheOwningDepartment() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Damaged footpath needs urgent repair.");
        assign(id, deptA.getId());

        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/notes")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "Contractor visited the site today"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/department/complaints/" + id + "/notes")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Contractor visited the site today"));

        mockMvc.perform(get(BASE + "/department/complaints/" + id + "/notes")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/citizen/complaints/" + id + "/notes")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassignmentMovesOwnershipAndRevokesOldDepartmentAccess() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Street excavation left unattended overnight.");
        assign(id, deptA.getId());

        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/reassign-request")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toDepartmentId", deptB.getId().toString(),
                                "reason", "This belongs to the water works department"))))
                .andExpect(status().isOk());

        var pending = reassignmentRequests.findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus.PENDING);
        assertThat(pending).hasSize(1);

        mockMvc.perform(post(BASE + "/admin/reassignment-requests/" + pending.get(0).getId() + "/review")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED", "reason", "Approved after review"))))
                .andExpect(status().isOk());

        assertThat(complaints.findById(id).orElseThrow().getRelatedDepartment().getId())
                .isEqualTo(deptB.getId());
        mockMvc.perform(get(BASE + "/department/complaints/" + id)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/department/complaints/" + id)
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk());
    }

    @Test
    void departmentAnalyticsUseRealStoredRowsOnly() throws Exception {
        UUID first = submitComplaint(citizenAToken, "Pothole one needs repair on the main road.");
        UUID second = submitComplaint(citizenBToken, "Pothole two needs repair on the side road.");
        assign(first, deptA.getId());
        assign(second, deptB.getId());

        mockMvc.perform(get(BASE + "/department/analytics")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byStatus.ASSIGNED").value(1));

        mockMvc.perform(get(BASE + "/department/analytics")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get(BASE + "/department/analytics")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void citizenFiltersAndAdminQueuesUsePersistedRowsOnly() throws Exception {
        UUID first = submitComplaint(citizenAToken, "Pothole near the school gate needs repair.");
        submitComplaint(citizenAToken, "Broken drain cover on another street.");

        mockMvc.perform(get(BASE + "/citizen/complaints")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .param("search", "pothole"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.toString()));

        mockMvc.perform(get(BASE + "/citizen/complaints")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .param("categoryId", roadCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(BASE + "/admin/complaints")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(BASE + "/admin/complaints/unassigned")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        assign(first, deptA.getId());

        mockMvc.perform(get(BASE + "/admin/complaints/unassigned")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(BASE + "/citizen/complaints")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .param("status", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first.toString()));

        mockMvc.perform(get(BASE + "/admin/complaints")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusTimelineIsRecordedAndExposedToOwner() throws Exception {
        UUID id = submitComplaint(citizenAToken, "Cracked pavement on the busy crossing.");
        assign(id, deptA.getId());
        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/citizen/complaints/" + id + "/timeline")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[2].toStatus").value("ACKNOWLEDGED"));
    }

    private void assign(UUID id, UUID departmentId) throws Exception {
        mockMvc.perform(post(BASE + "/admin/complaints/" + id + "/assign")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", departmentId.toString(),
                                "reason", "Routed to the verified responsible department"))))
                .andExpect(status().isOk());
    }

    private void acceptAndResolve(UUID id) throws Exception {
        mockMvc.perform(post(BASE + "/department/complaints/" + id + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.IN_PROGRESS, "Started work"))))
                .andExpect(status().isOk());
        addVerifiedImage(id);
        mockMvc.perform(patch(BASE + "/department/complaints/" + id + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.RESOLVED, "Work finished"))))
                .andExpect(status().isOk());
    }

    private void addVerifiedImage(UUID complaintId) {
        var image = new com.civicsense.domain.ComplaintImage();
        image.setComplaint(complaints.getReferenceById(complaintId));
        image.setStorageKey("test/" + UUID.randomUUID() + ".jpg");
        image.setImageKind(ImageKind.AFTER);
        image.setUploadStatus(UploadStatus.VERIFIED);
        image.setContentType("image/jpeg");
        image.setCreatedAt(Instant.now());
        images.save(image);
    }
}
