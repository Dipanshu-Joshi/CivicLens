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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.civicsense.complaint.dto.ComplaintDtos.CommentRequest;
import com.civicsense.complaint.dto.ComplaintDtos.EvidenceRequest;
import com.civicsense.complaint.dto.ComplaintDtos.FeedbackRequest;
import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.domain.Channel;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.DeliveryStatus;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.Notification;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.UploadStatus;
import com.civicsense.identity.User;
import com.civicsense.notification.OutboxDispatcher;
import com.civicsense.notification.OutboxEventProcessor;

/** Phase 9: durable delivery plus the complete citizen/department/admin workflow. */
class Phase9NotificationWorkflowIntegrationTest extends Phase7TestSupport {

    @Autowired
    private OutboxEventProcessor processor;

    @Autowired
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void submissionAndAssignmentNotifyEveryRelevantRole() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Pothole outside the school gate needs repair.");

        assertNotified(citizenA.getId(), NotificationType.COMPLAINT_SUBMITTED, complaintId);
        assertNotified(superAdmin.getId(), NotificationType.COMPLAINT_SUBMITTED, complaintId);

        assign(complaintId, deptA.getId());

        assertNotified(citizenA.getId(), NotificationType.COMPLAINT_ASSIGNED, complaintId);
        assertNotified(deptAdminA.getId(), NotificationType.COMPLAINT_ASSIGNED, complaintId);

        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenB.getId())).isEmpty();
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(deptAdminB.getId())).isEmpty();
        assertThat(notifications.findAll()).allSatisfy(notification -> {
            assertThat(notification.getChannel()).isEqualTo(Channel.IN_APP);
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
            assertThat(notification.getOutboxEventId()).isNotNull();
        });
        assertThat(outboxEvents.findAll()).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getProcessedAt()).isNotNull();
        });
    }

    @Test
    void notificationResponseIncludesDeliveryStateAndComplaintNumber() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Large crack across the pedestrian crossing.");

        mockMvc.perform(get(BASE + "/notifications")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("COMPLAINT_SUBMITTED"))
                .andExpect(jsonPath("$[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$[0].sentAt").isNotEmpty())
                .andExpect(jsonPath("$[0].complaintId").value(complaintId.toString()))
                .andExpect(jsonPath("$[0].complaintNumber").isNotEmpty());

        Notification notification = notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()).get(0);
        mockMvc.perform(patch(BASE + "/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(BASE + "/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
        assertThat(notifications.findById(notification.getId()).orElseThrow().getDeliveryStatus())
                .isEqualTo(DeliveryStatus.READ);
    }

    @Test
    void informationEvidenceResolutionAndFeedbackNotifyTheOwningRoles() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Drain cover is broken near the bus stop.");
        assign(complaintId, deptA.getId());
        accept(complaintId);

        postJson(BASE + "/department/complaints/" + complaintId + "/request-info",
                deptAToken, new CommentRequest("Please describe the exact location."), status().isOk());
        assertNotified(citizenA.getId(), NotificationType.INFO_REQUESTED, complaintId);

        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/respond-info")
                        .header("Authorization", "Bearer " + citizenBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CommentRequest("This is not my complaint"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/respond-info")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CommentRequest("It is next to the bus stop sign."))))
                .andExpect(status().isOk());
        assertNotified(deptAdminA.getId(), NotificationType.INFO_RESPONDED, complaintId);

        UUID imageId = stageImage(deptAdminA.getId(), ImageKind.AFTER, UploadStatus.VERIFIED, null);
        postJson(BASE + "/department/complaints/" + complaintId + "/evidence",
                deptAToken, new EvidenceRequest(imageId), status().isOk());
        assertNotified(citizenA.getId(), NotificationType.EVIDENCE_ATTACHED, complaintId);

        patchStatus(complaintId, ComplaintStatus.IN_PROGRESS, "Repairs started");
        patchStatus(complaintId, ComplaintStatus.RESOLVED, "Drain cover replaced");
        assertNotified(citizenA.getId(), NotificationType.COMPLAINT_RESOLVED, complaintId);

        FeedbackRequest feedbackRequest = new FeedbackRequest(5, "Fixed quickly", true, null);
        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/feedback")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(feedbackRequest)))
                .andExpect(status().isOk());
        assertNotified(deptAdminA.getId(), NotificationType.FEEDBACK_RECEIVED, complaintId);
        assertNotified(superAdmin.getId(), NotificationType.FEEDBACK_RECEIVED, complaintId);
        assertNotified(citizenA.getId(), NotificationType.COMPLAINT_CLOSED, complaintId);
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.CLOSED);

        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/feedback")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(feedbackRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void disputedFeedbackReopensAndNotifiesDepartmentAndSuperAdmin() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Footpath remains uneven after repair.");
        assign(complaintId, deptA.getId());
        acceptAndResolve(complaintId);

        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/feedback")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FeedbackRequest(2, "Still uneven", false,
                                "The loose slab was not replaced"))))
                .andExpect(status().isOk());

        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.REOPENED);
        assertNotified(citizenA.getId(), NotificationType.COMPLAINT_REOPENED, complaintId);
        assertNotified(deptAdminA.getId(), NotificationType.COMPLAINT_REOPENED, complaintId);
        assertNotified(superAdmin.getId(), NotificationType.COMPLAINT_REOPENED, complaintId);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(deptAdminB.getId())).isEmpty();
    }

    @Test
    void reassignmentDecisionsNotifyBothDepartments() throws Exception {
        UUID approvedId = submitComplaint(citizenAToken, "Water main leak flooding the road.");
        assign(approvedId, deptA.getId());
        UUID approvedRequestId = requestReassignment(approvedId);
        review(approvedRequestId, ReassignmentStatus.APPROVED, "Water Works owns this asset");
        assertNotified(deptAdminB.getId(), NotificationType.COMPLAINT_ASSIGNED, approvedId);
        assertNotified(deptAdminA.getId(), NotificationType.REASSIGNMENT_APPROVED, approvedId);

        UUID rejectedId = submitComplaint(citizenAToken, "Another water leak near the school.");
        assign(rejectedId, deptA.getId());
        UUID rejectedRequestId = requestReassignment(rejectedId);
        review(rejectedRequestId, ReassignmentStatus.REJECTED, "Road Maintenance must keep this case");
        assertNotified(deptAdminA.getId(), NotificationType.REASSIGNMENT_REJECTED, rejectedId);
        assertThat(hasNotification(deptAdminB.getId(), NotificationType.COMPLAINT_ASSIGNED, rejectedId)).isFalse();
    }

    @Test
    void registrationAndVerificationNotifySuperAdminAndDepartmentOwner() throws Exception {
        String body = json(Map.of(
                "department", Map.of(
                        "name", "Parks Department",
                        "departmentType", "MUNICIPAL",
                        "officialEmail", "parks-p9@example.com",
                        "contactNumber", "+911234567890"),
                "administrator", Map.of(
                        "fullName", "Parks Owner",
                        "email", "parks-owner-p9@example.com",
                        "password", PASSWORD,
                        "phone", "+911234567891")));
        String response = mockMvc.perform(post(BASE + "/auth/register/department")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID departmentId = UUID.fromString(objectMapper.readTree(response).get("departmentId").asText());
        User owner = users.findAll().stream()
                .filter(user -> user.getEmail().equals("parks-owner-p9@example.com"))
                .findFirst().orElseThrow();

        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(superAdmin.getId()))
                .anyMatch(notification -> notification.getType()
                        == NotificationType.DEPARTMENT_APPROVAL_REQUIRED);

        mockMvc.perform(post(BASE + "/admin/departments/" + departmentId + "/verify")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "VERIFIED"))))
                .andExpect(status().isOk());

        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(owner.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.DEPARTMENT_VERIFIED);
    }

    @Test
    void dispatcherRetriesFailuresAndDeduplicatesDelivery() throws Exception {
        UUID eventId = createOutboxEvent(citizenA.getId(), "NOT_A_REAL_TYPE");

        assertThat(processor.process(eventId)).isFalse();
        OutboxEvent failed = outboxEvents.findById(eventId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).isNotBlank();
        assertThat(failed.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId())).isEmpty();

        failed.setPayload(payload(citizenA.getId(), "COMPLAINT_SUBMITTED"));
        failed.setStatus(OutboxStatus.FAILED);
        failed.setNextAttemptAt(Instant.now().minusSeconds(1));
        outboxEvents.save(failed);

        assertThat(processor.process(eventId)).isTrue();
        OutboxEvent processed = outboxEvents.findById(eventId).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(processed.getAttempts()).isEqualTo(2);
        assertThat(processed.getLastError()).isNull();
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .filteredOn(notification -> notification.getOutboxEventId().equals(eventId))
                .hasSize(1);

        assertThat(processor.process(eventId)).isFalse();
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .filteredOn(notification -> notification.getOutboxEventId().equals(eventId))
                .hasSize(1);
    }

    @Test
    void dispatcherSweepDeliversPendingEvents() throws Exception {
        UUID eventId = createOutboxEvent(citizenA.getId(), "COMPLAINT_SUBMITTED");

        assertThat(dispatcher.dispatchDue(10)).isPositive();
        assertThat(outboxEvents.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PROCESSED);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .extracting(Notification::getOutboxEventId)
                .contains(eventId);
    }

    @Test
    void disabledInAppPreferenceSuppressesDeliveryAndExternalChannelsCannotBeEnabled() throws Exception {
        mockMvc.perform(patch(BASE + "/notifications/preferences")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inAppEnabled", false,
                                "emailEnabled", false, "pushEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inAppEnabled").value(false));

        UUID eventId = createOutboxEvent(citizenA.getId(), "COMPLAINT_SUBMITTED");
        assertThat(processor.process(eventId)).isTrue();
        assertThat(outboxEvents.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PROCESSED);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId())).isEmpty();

        mockMvc.perform(patch(BASE + "/notifications/preferences")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inAppEnabled", true,
                                "emailEnabled", true, "pushEnabled", false))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(BASE + "/notifications/preferences")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inAppEnabled").value(false))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(false));
    }

    private void accept(UUID complaintId) throws Exception {
        mockMvc.perform(post(BASE + "/department/complaints/" + complaintId + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
    }

    private void acceptAndResolve(UUID complaintId) throws Exception {
        accept(complaintId);
        patchStatus(complaintId, ComplaintStatus.IN_PROGRESS, "Work started");
        UUID imageId = stageImage(deptAdminA.getId(), ImageKind.AFTER, UploadStatus.VERIFIED, null);
        postJson(BASE + "/department/complaints/" + complaintId + "/evidence",
                deptAToken, new EvidenceRequest(imageId), status().isOk());
        patchStatus(complaintId, ComplaintStatus.RESOLVED, "Repairs completed");
    }

    private void postJson(String url, String token, Object body,
            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(expected);
    }

    private void patchStatus(UUID complaintId, ComplaintStatus target, String reason) throws Exception {
        mockMvc.perform(patch(BASE + "/department/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(target, reason))))
                .andExpect(status().isOk());
    }

    private UUID requestReassignment(UUID complaintId) throws Exception {
        mockMvc.perform(post(BASE + "/department/complaints/" + complaintId
                        + "/reassign-request")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toDepartmentId", deptB.getId().toString(),
                                "reason", "Water Works owns this type of asset"))))
                .andExpect(status().isOk());
        return reassignmentRequests.findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus.PENDING).stream()
                .filter(request -> request.getComplaint().getId().equals(complaintId))
                .findFirst().orElseThrow().getId();
    }

    private void review(UUID requestId, ReassignmentStatus decision, String reason) throws Exception {
        mockMvc.perform(post(BASE + "/admin/reassignment-requests/" + requestId + "/review")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", decision.name(), "reason", reason))))
                .andExpect(status().isOk());
    }

    private void assertNotified(UUID recipientId, NotificationType type, UUID complaintId) {
        assertThat(hasNotification(recipientId, type, complaintId))
                .as("%s notification for complaint %s", type, complaintId)
                .isTrue();
    }

    private boolean hasNotification(UUID recipientId, NotificationType type, UUID complaintId) {
        return notifications.findAllByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .anyMatch(notification -> notification.getType() == type
                        && notification.getComplaint() != null
                        && notification.getComplaint().getId().equals(complaintId));
    }

    private UUID createOutboxEvent(UUID recipientId, String type) throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setEventType("notification.test");
        event.setAggregateType("complaint");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setPayload(payload(recipientId, type));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setIdempotencyKey(UUID.randomUUID().toString());
        event.setCreatedAt(Instant.now());
        return outboxEvents.save(event).getId();
    }

    private String payload(UUID recipientId, String type) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "recipientUserId", recipientId.toString(),
                "complaintId", "",
                "type", type,
                "title", "Test notification",
                "body", "Test body"));
    }
}
