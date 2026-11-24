package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisSource;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.AssignmentType;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintAssignment;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.ReassignmentStatus;
import com.civicsense.domain.RoutingDecision;
import com.civicsense.domain.Severity;
import com.civicsense.domain.UploadStatus;
import com.civicsense.storage.ObjectStorage;
import com.civicsense.storage.PresignedUrl;
import com.fasterxml.jackson.databind.JsonNode;

/** Phase 8 platform queue, AI-history and routing-governance integration coverage. */
class Phase8AiAndRoutingGovernanceIntegrationTest extends Phase7TestSupport {

    @MockitoBean
    ObjectStorage storage;

    @BeforeEach
    void setUp() {
        resetAndSeed();
        when(storage.presignGet(anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(new PresignedUrl("https://storage.test/view",
                        Instant.now().plusSeconds(300), Map.of()));
    }

    @Test
    void governanceRoutesAreForbiddenForCitizensAndDepartmentAdmins() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");

        for (String token : List.of(citizenAToken, deptAToken)) {
            mockMvc.perform(get(BASE + "/admin/complaints/queue")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/admin/complaints/" + complaintId + "/analyses")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(BASE + "/admin/complaints/" + complaintId + "/classification")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("categoryCode", "ROAD",
                                    "subcategoryCode", "POTHOLE",
                                    "reason", "Supervisor reviewed this complaint."))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void superAdminGlobalQueueFiltersAndPagingWorkAlongsideLegacyRoutes() throws Exception {
        UUID firstId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        UUID secondId = submitComplaint(citizenBToken,
                "The streetlight is broken beside the northern pedestrian crossing.");
        setComplaintColumn(secondId, "ward", "WARD-09");
        setComplaintColumn(secondId, "zone", "NORTH");
        setComplaintColumn(secondId, "severity", "HIGH");
        createAiAnalysis(firstId, "AI description of the pothole.");
        createAutoAssignment(secondId);
        assign(firstId, deptA.getId());

        mockMvc.perform(get(BASE + "/admin/complaints/queue")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        String secondPage = performQueue(Map.of("page", "1", "size", "1"));
        assertThat(objectMapper.readTree(secondPage).get("content").get(0).get("id").asText())
                .isNotEqualTo(objectMapper.readTree(performQueue(Map.of("page", "0", "size", "1")))
                        .get("content").get(0).get("id").asText());

        assertThat(queueTotalElements(Map.of("status", List.of("SUBMITTED")))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("categoryCode", "ROAD"))).isEqualTo(2);
        assertThat(queueTotalElements(Map.of("ward", "WARD-09"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("zone", "NORTH"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("severity", "HIGH"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("search", "streetlight"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("overdue", "true"))).isZero();
        assertThat(queueTotalElements(Map.of("aiAssigned", "true"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("from", Instant.now().minusSeconds(600).toString(),
                "to", Instant.now().plusSeconds(600).toString()))).isEqualTo(2);
        assertThat(queueTotalElements(Map.of("routingOutcome", "LOW_CONFIDENCE"))).isEqualTo(2);
        assertThat(queueTotalElements(Map.of("aiStatus", "SUCCESS"))).isEqualTo(1);
        assertThat(queueTotalElements(Map.of("departmentId", deptA.getId().toString()))).isEqualTo(1);

        mockMvc.perform(get(BASE + "/admin/complaints").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(BASE + "/admin/complaints/unassigned")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/admin/complaints/" + firstId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/admin/complaints/" + firstId + "/routing-decisions")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aiHistoryShowsOriginalAnalysisAndAdminOverrideWithSourceAndReason() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        UUID originalId = createAiAnalysis(complaintId, "Original AI description of the pothole.");
        String reason = "Supervisor confirmed the corrected classification after field review.";

        override(complaintId, Map.of("categoryCode", "ROAD", "subcategoryCode", "POTHOLE",
                "reason", reason));

        String response = mockMvc.perform(get(BASE + "/admin/complaints/" + complaintId + "/analyses")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode analyses = objectMapper.readTree(response).get("analyses");
        assertThat(analyses).hasSize(2);
        assertThat(analyses.get(0).get("source").asText()).isEqualTo("ADMIN_OVERRIDE");
        assertThat(analyses.get(0).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(analyses.get(0).get("adminOverrideReason").asText()).isEqualTo(reason);
        assertThat(analyses.get(0).get("categoryCode").asText()).isEqualTo("ROAD");
        assertThat(analyses.get(0).get("requiresHumanReview").asBoolean()).isFalse();
        assertThat(analyses.get(1).get("id").asText()).isEqualTo(originalId.toString());
        assertThat(analyses.get(1).get("source").asText()).isEqualTo("AI");
        assertThat(analyses.get(1).get("description").asText())
                .isEqualTo("Original AI description of the pothole.");
        assertThat(response).doesNotContain("rawResponse", "requestPayload", "parsedResult",
                "provider", "storageKey", "adminUserId");
    }

    @Test
    void overrideAppliesSeverityAndDescriptionAndValidatesItsInputs() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        String description = "Field inspection confirms a deep pothole across the whole carriageway.";

        override(complaintId, Map.of("categoryCode", "ROAD", "subcategoryCode", "POTHOLE",
                "reason", "Supervisor confirmed the corrected classification after field review.",
                "severity", "HIGH", "description", description));

        Complaint complaint = complaints.findById(complaintId).orElseThrow();
        assertThat(complaint.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(complaint.getDescription()).isEqualTo(description);
        AiAnalysis latest = aiAnalyses.findAllByComplaintIdOrderByCreatedAtDesc(complaintId).get(0);
        assertThat(latest.getSeverity()).isEqualTo("HIGH");
        assertThat(latest.getSource()).isEqualTo(AiAnalysisSource.ADMIN_OVERRIDE);
        int analysisCount = aiAnalyses.findAllByComplaintIdOrderByCreatedAtDesc(complaintId).size();

        overrideExpecting(complaintId, Map.of("categoryCode", "ROAD", "subcategoryCode", "POTHOLE",
                "reason", "short"), 400);
        overrideExpecting(complaintId, Map.of("categoryCode", "NO-SUCH-CATEGORY",
                "subcategoryCode", "POTHOLE",
                "reason", "Supervisor confirmed the corrected classification after field review."), 404);

        assertThat(aiAnalyses.findAllByComplaintIdOrderByCreatedAtDesc(complaintId))
                .hasSize(analysisCount);
    }

    @Test
    void forcedReroutePreservesPreviousRoutingDecisions() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        List<RoutingDecision> before = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(complaintId);
        assertThat(before).hasSize(1);
        UUID previousId = before.get(0).getId();

        override(complaintId, Map.of("categoryCode", "ROAD", "subcategoryCode", "POTHOLE",
                "reason", "Supervisor confirmed the corrected classification after field review."));

        List<RoutingDecision> after = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(complaintId);
        assertThat(after).hasSize(2);
        assertThat(after.get(0).isCurrent()).isTrue();
        assertThat(after.get(1).getId()).isEqualTo(previousId);
        assertThat(after.get(1).isCurrent()).isFalse();
    }

    @Test
    void manualAssignUnassignAndReassignmentReviewPreserveHistory() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        assign(complaintId, deptA.getId());
        int decisionCount = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(complaintId).size();

        mockMvc.perform(post(BASE + "/admin/complaints/" + complaintId + "/unassign")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Returned to the platform queue for review."))))
                .andExpect(status().isOk());
        assertThat(complaints.findById(complaintId).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.UNASSIGNED);
        assertThat(assignments.findByComplaintIdAndActiveTrue(complaintId)).isEmpty();

        assign(complaintId, deptA.getId());
        assertThat(assignments.findAllByComplaintIdOrderByAssignedAtDesc(complaintId)).hasSize(2);
        assertThat(assignments.findByComplaintIdAndActiveTrue(complaintId)).isPresent();

        mockMvc.perform(post(BASE + "/department/complaints/" + complaintId + "/reassign-request")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toDepartmentId", deptB.getId().toString(),
                                "reason", "Water team owns this location after field inspection."))))
                .andExpect(status().isOk());
        UUID requestId = reassignmentRequests
                .findAllByStatusOrderByCreatedAtDesc(ReassignmentStatus.PENDING).get(0).getId();
        mockMvc.perform(post(BASE + "/admin/reassignment-requests/" + requestId + "/review")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "APPROVED",
                                "reason", "Approved after verifying the receiving department."))))
                .andExpect(status().isOk());

        Complaint complaint = complaints.findById(complaintId).orElseThrow();
        assertThat(complaint.getRelatedDepartment().getId()).isEqualTo(deptB.getId());
        List<ComplaintAssignment> history = assignments.findAllByComplaintIdOrderByAssignedAtDesc(complaintId);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).getAssignmentType()).isEqualTo(AssignmentType.REASSIGN);
        assertThat(history).filteredOn(assignment -> !assignment.isActive()).hasSize(2);
        assertThat(statusHistory.findAllByComplaintIdOrderByCreatedAtAsc(complaintId).size())
                .isGreaterThanOrEqualTo(4);
        assertThat(routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(complaintId))
                .hasSize(decisionCount);
    }

    @Test
    void superAdminCanObtainEvidenceViewUrl() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken,
                "A deep pothole is blocking the road outside the civic centre.");
        UUID imageId = stageImage(citizenA.getId(), ImageKind.CITIZEN, UploadStatus.VERIFIED, complaintId);

        mockMvc.perform(get(BASE + "/uploads/" + imageId + "/url")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.test/view"));
        mockMvc.perform(get(BASE + "/uploads/" + imageId + "/url")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());
    }

    private void override(UUID complaintId, Map<String, Object> body) throws Exception {
        overrideExpecting(complaintId, body, 200);
    }

    private void overrideExpecting(UUID complaintId, Map<String, Object> body, int expectedStatus)
            throws Exception {
        mockMvc.perform(patch(BASE + "/admin/complaints/" + complaintId + "/classification")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(expectedStatus));
    }

    private String performQueue(Map<String, Object> parameters) throws Exception {
        var request = get(BASE + "/admin/complaints/queue")
                .header("Authorization", "Bearer " + superAdminToken);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof List<?> values) {
                for (Object value : values) {
                    request.param(entry.getKey(), value.toString());
                }
            } else {
                request.param(entry.getKey(), entry.getValue().toString());
            }
        }
        return mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long queueTotalElements(Map<String, Object> parameters) throws Exception {
        return objectMapper.readTree(performQueue(parameters)).get("totalElements").asLong();
    }

    private UUID createAiAnalysis(UUID complaintId, String description) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setComplaint(complaints.getReferenceById(complaintId));
        analysis.setStatus(AiAnalysisStatus.SUCCESS);
        analysis.setSource(AiAnalysisSource.AI);
        analysis.setCategoryCode("ROAD");
        analysis.setSubcategoryCode("POTHOLE");
        analysis.setSeverity("MEDIUM");
        analysis.setDescription(description);
        analysis.setConfidence(0.91);
        analysis.setIsCivicIssue(true);
        analysis.setRequiresHumanReview(false);
        analysis.setCreatedAt(Instant.now());
        return aiAnalyses.save(analysis).getId();
    }

    private void createAutoAssignment(UUID complaintId) {
        ComplaintAssignment assignment = new ComplaintAssignment();
        assignment.setComplaint(complaints.getReferenceById(complaintId));
        assignment.setDepartment(deptA);
        assignment.setAssignmentType(AssignmentType.AUTO);
        assignment.setActive(true);
        assignment.setAssignedAt(Instant.now());
        assignment.setCreatedAt(Instant.now());
        assignments.save(assignment);
    }
}
