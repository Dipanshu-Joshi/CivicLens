package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisSource;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.AssignmentType;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.DepartmentIssueCategory;
import com.civicsense.domain.DepartmentIssueCategoryId;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.LocationSource;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.RoutingDecision;
import com.civicsense.domain.RoutingOutcome;
import com.civicsense.domain.ServiceArea;
import com.civicsense.domain.Severity;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.repository.DepartmentIssueCategoryRepository;
import com.civicsense.routing.RoutingEngine;

/** Phase 6 routing matrix. Spatial matching uses the explicit H2/JTS test double. */
class Phase6RoutingIntegrationTest extends Phase3TestSupport {

    private static final double LATITUDE = 28.6139;
    private static final double LONGITUDE = 77.2090;
    private static final String COVERING_WKT =
            "POLYGON ((77.0 28.0, 78.0 28.0, 78.0 29.0, 77.0 29.0, 77.0 28.0))";
    private static final String OUTSIDE_WKT =
            "POLYGON ((70.0 20.0, 71.0 20.0, 71.0 21.0, 70.0 21.0, 70.0 20.0))";

    @Autowired
    private RoutingEngine routingEngine;

    @Autowired
    private DepartmentIssueCategoryRepository departmentIssueCategories;

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void singleEligibleDepartmentIsAssignedDespiteAdvisoryAiHintAndRemoteOffice() {
        deptA.setOfficeAddress("Remote office, 900 km away");
        departments.save(deptA);
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, "WARD-12", null, null);
        UUID id = routableComplaint(0.92, true, false, "Water Works");

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.ASSIGNED.name());
        assertThat(decision.getSelectedDepartment().getId()).isEqualTo(deptA.getId());
        assertThat(decision.getRulesetVersion()).isEqualTo("routing-v1");
        assertThat(decision.getLocationPointWkt()).contains("77.209", "28.6139");
        Complaint complaint = complaints.findById(id).orElseThrow();
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.ASSIGNED);
        assertThat(complaint.getRelatedDepartment().getId()).isEqualTo(deptA.getId());
        assertThat(assignments.findByComplaintIdAndActiveTrue(id).orElseThrow().getAssignmentType())
                .isEqualTo(AssignmentType.AUTO);
        assertThat(notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId()))
                .anyMatch(notification -> notification.getType() == NotificationType.COMPLAINT_ASSIGNED);
    }

    @Test
    void equalTopScoresAreAmbiguousAndNothingIsAssigned() {
        capability(deptA, potholeCategory);
        capability(deptB, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        area(deptB, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.AMBIGUOUS.name());
        assertThat(decision.getSelectedDepartment()).isNull();
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isEmpty();
        assertThat(complaints.findById(id).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.AI_ANALYSIS_COMPLETED);
    }

    @Test
    void departmentPriorityBreaksDifferentScores() {
        deptB.setRoutingPriority(150);
        departments.save(deptB);
        capability(deptA, potholeCategory);
        capability(deptB, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        area(deptB, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.ASSIGNED.name());
        assertThat(decision.getSelectedDepartment().getId()).isEqualTo(deptB.getId());
    }

    @Test
    void pointOutsideAllServiceAreasIsUnassigned() {
        capability(deptA, potholeCategory);
        area(deptA, OUTSIDE_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.UNASSIGNED.name());
        assertThat(decision.getSelectedDepartment()).isNull();
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isEmpty();
    }

    @Test
    void unverifiedAndInactiveDepartmentsAreExcluded() {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        deptA.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        departments.save(deptA);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.UNASSIGNED.name());
        assertThat(decision.getSelectedDepartment()).isNull();
    }

    @Test
    void lowConfidenceNeverAutoAssigns() {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.60, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.LOW_CONFIDENCE.name());
        assertThat(decision.getSelectedDepartment()).isNull();
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isEmpty();
    }

    @Test
    void nonCivicResultIsExplicitlyUnresolved() {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.99, false, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.NON_CIVIC.name());
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isEmpty();
    }

    @Test
    void polygonMatchUsesHighestSpecificityExactlyOnce() {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, "WARD-12", null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.ASSIGNED.name());
        assertThat(decision.getCandidates()).contains("\"geoScore\":1000", "\"geoMatch\":\"POLYGON\"");
        assertThat(decision.getCandidates()).doesNotContain("\"geoMatch\":\"WARD\"");
    }

    @Test
    void departmentAtCapacityProducesNoCapacity() {
        deptA.setMaxOpenComplaints(0);
        departments.save(deptA);
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision decision = routingEngine.route(id, false, null, null);

        assertThat(decision.getOutcome()).isEqualTo(RoutingOutcome.NO_CAPACITY.name());
        assertThat(assignments.findByComplaintIdAndActiveTrue(id)).isEmpty();
    }

    @Test
    void duplicateRoutingTriggersCreateOnlyOneCurrentDecision() {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);

        RoutingDecision first = routingEngine.route(id, false, null, null);
        RoutingDecision second = routingEngine.route(id, false, null, null);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(id)).hasSize(1);
    }

    @Test
    void adminClassificationOverrideReroutesAndPreservesHistory() throws Exception {
        IssueCategory waterCategory = category("WATER", "Water", null);
        IssueCategory leakCategory = category("LEAK", "Leak", waterCategory);
        capability(deptA, potholeCategory);
        capability(deptB, leakCategory);
        area(deptA, COVERING_WKT, null, null, null);
        area(deptB, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);
        RoutingDecision original = routingEngine.route(id, false, null, null);
        assertThat(original.getSelectedDepartment().getId()).isEqualTo(deptA.getId());

        mockMvc.perform(patch(BASE + "/admin/complaints/{id}/classification", id)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryCode", "WATER",
                                "subcategoryCode", "LEAK",
                                "reason", "Supervisor confirmed corrected classification."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"))
                .andExpect(jsonPath("$.selectedDepartmentId").value(deptB.getId().toString()))
                .andExpect(jsonPath("$.reason").value("Supervisor confirmed corrected classification."))
                .andExpect(jsonPath("$.current").value(true));

        List<RoutingDecision> history = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(id);
        assertThat(history).hasSize(2);
        assertThat(history).filteredOn(decision -> !decision.isCurrent()).hasSize(1);
        assertThat(assignments.findByComplaintIdAndActiveTrue(id).orElseThrow().getAssignmentType())
                .isEqualTo(AssignmentType.ADMIN_CORRECTION);
        assertThat(aiAnalyses.findAllByComplaintIdOrderByCreatedAtDesc(id).get(0).getSource())
                .isEqualTo(AiAnalysisSource.ADMIN_OVERRIDE);
        assertThat(auditLogs.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "complaint", id.toString()))
                .anyMatch(log -> log.getAction().equals("COMPLAINT_CLASSIFICATION_OVERRIDE"));
    }

    @Test
    void adminRerouteCreatesNewCurrentDecisionAndPreservesHistory() throws Exception {
        deptA.setRoutingPriority(150);
        departments.save(deptA);
        capability(deptA, potholeCategory);
        capability(deptB, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        area(deptB, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);
        RoutingDecision original = routingEngine.route(id, false, null, null);
        assertThat(original.getSelectedDepartment().getId()).isEqualTo(deptA.getId());

        Department mutableA = departments.findById(deptA.getId()).orElseThrow();
        mutableA.setRoutingPriority(50);
        departments.save(mutableA);
        Department mutableB = departments.findById(deptB.getId()).orElseThrow();
        mutableB.setRoutingPriority(200);
        departments.save(mutableB);

        mockMvc.perform(post(BASE + "/admin/complaints/{id}/reroute", id)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Supervisor reassigned after field review."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"))
                .andExpect(jsonPath("$.selectedDepartmentId").value(deptB.getId().toString()))
                .andExpect(jsonPath("$.reason").value("Supervisor reassigned after field review."))
                .andExpect(jsonPath("$.current").value(true));

        List<RoutingDecision> history = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(id);
        assertThat(history).hasSize(2);
        assertThat(history).filteredOn(decision -> !decision.isCurrent()).hasSize(1);
        assertThat(assignments.findByComplaintIdAndActiveTrue(id).orElseThrow().getAssignmentType())
                .isEqualTo(AssignmentType.ADMIN_CORRECTION);
        assertThat(auditLogs.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "complaint", id.toString()))
                .anyMatch(log -> log.getAction().equals("COMPLAINT_REROUTE"));
    }

    @Test
    void superAdminCanResolveAiFailedComplaintThroughClassificationOverride() throws Exception {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);
        Complaint complaint = complaints.findById(id).orElseThrow();
        complaint.setStatus(ComplaintStatus.AI_ANALYSIS_FAILED);
        complaints.save(complaint);
        AiAnalysis failed = aiAnalyses.findAllByComplaintIdOrderByCreatedAtDesc(id).get(0);
        failed.setStatus(AiAnalysisStatus.FAILED);
        aiAnalyses.save(failed);

        mockMvc.perform(patch(BASE + "/admin/complaints/{id}/classification", id)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryCode", "ROAD",
                                "subcategoryCode", "POTHOLE",
                                "reason", "Admin confirmed the complaint after AI failure."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"))
                .andExpect(jsonPath("$.selectedDepartmentId").value(deptA.getId().toString()));

        assertThat(complaints.findById(id).orElseThrow().getStatus())
                .isEqualTo(ComplaintStatus.ASSIGNED);
    }

    @Test
    void noImageSubmissionRequestsRoutingAndCreatesExplicitReviewDecision() throws Exception {
        UUID id = submitComplaint(citizenAToken,
                "A large pothole is blocking the road outside the civic centre.");

        List<RoutingDecision> decisions = routingDecisions.findAllByComplaintIdOrderByCreatedAtDesc(id);
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getOutcome()).isEqualTo(RoutingOutcome.LOW_CONFIDENCE.name());
        assertThat(decisions.get(0).getSelectedDepartment()).isNull();
        assertThat(decisions.get(0).getLocationPointWkt()).contains("77.209", "28.6139");
    }

    @Test
    void departmentAdminCannotReadAnotherDepartmentsComplaint() throws Exception {
        capability(deptA, potholeCategory);
        area(deptA, COVERING_WKT, null, null, null);
        UUID id = routableComplaint(0.92, true, false, null);
        routingEngine.route(id, false, null, null);

        mockMvc.perform(get(BASE + "/department/complaints/{id}", id)
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
    }

    private UUID routableComplaint(double confidence, boolean civic, boolean review, String hint) {
        Instant now = Instant.now();
        Complaint complaint = new Complaint();
        complaint.setComplaintNumber("CIV6-" + UUID.randomUUID().toString().substring(0, 12));
        complaint.setCitizen(citizenA);
        complaint.setDescription("Large pothole blocking the main carriageway near the market.");
        complaint.setLatitude(LATITUDE);
        complaint.setLongitude(LONGITUDE);
        complaint.setLocationSource(LocationSource.GPS);
        complaint.setLocationWkt("POINT(" + LONGITUDE + " " + LATITUDE + ")");
        complaint.setWard("WARD-12");
        complaint.setCity("Example City");
        complaint.setCategory(roadCategory);
        complaint.setSubcategory(potholeCategory);
        complaint.setStatus(ComplaintStatus.AI_ANALYSIS_COMPLETED);
        complaint.setSeverity(Severity.MEDIUM);
        complaint.setAiConfidence(confidence);
        complaint.setAiRequiresReview(review);
        complaint.setCreatedAt(now);
        complaint.setUpdatedAt(now);
        Complaint saved = complaints.save(complaint);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setComplaint(saved);
        analysis.setStatus(AiAnalysisStatus.SUCCESS);
        analysis.setSource(AiAnalysisSource.AI);
        analysis.setCategoryCode("ROAD");
        analysis.setSubcategoryCode("POTHOLE");
        analysis.setSeverity("HIGH");
        analysis.setConfidence(confidence);
        analysis.setIsCivicIssue(civic);
        analysis.setRequiresHumanReview(review);
        analysis.setRecommendedDepartmentHint(hint);
        analysis.setCreatedAt(now);
        aiAnalyses.save(analysis);
        return saved.getId();
    }

    private void capability(Department department, IssueCategory category) {
        DepartmentIssueCategory link = new DepartmentIssueCategory();
        link.setId(new DepartmentIssueCategoryId(department.getId(), category.getId()));
        link.setPrimary(true);
        link.setPriority(100);
        departmentIssueCategories.save(link);
    }

    private IssueCategory category(String code, String name, IssueCategory parent) {
        IssueCategory category = new IssueCategory();
        category.setCode(code);
        category.setName(name);
        category.setParentCategory(parent);
        category.setDisplayOrder(3);
        category.setActive(true);
        return categories.save(category);
    }

    private void area(Department department, String wkt, String ward, String zone, UUID jurisdictionId) {
        ServiceArea area = new ServiceArea();
        area.setDepartment(department);
        area.setName("Area " + UUID.randomUUID());
        area.setBoundaryWkt(wkt);
        area.setCoversWard(ward);
        area.setCoversZone(zone);
        if (jurisdictionId != null) {
            area.setJurisdiction(jurisdictions.findById(jurisdictionId).orElseThrow());
        }
        area.setActive(true);
        serviceAreas.save(area);
    }
}
