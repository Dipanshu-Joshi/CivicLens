package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import com.civicsense.complaint.dto.ComplaintDtos.StatusUpdateRequest;
import com.civicsense.domain.AssignmentType;
import com.civicsense.domain.ComplaintAssignment;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;

/** Phase 7 queue, analytics, activity and notification dashboard endpoints. */
class Phase7QueueAndAnalyticsIntegrationTest extends Phase7TestSupport {

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void queueIsDepartmentScopedPaginatedSortedAndLegacyListStillWorks() throws Exception {
        UUID road = submitComplaint(citizenAToken, "Pothole near the school gate needs repair");
        UUID water = submitComplaint(citizenBToken, "Water leak under the bridge needs repair");
        UUID otherDept = submitComplaint(citizenAToken, "Pothole inside the other department area");
        UUID unassigned = submitComplaint(citizenBToken, "Unassigned pothole on the side lane");
        assign(road, deptA.getId());
        assign(water, deptA.getId());
        assign(otherDept, deptB.getId());

        Instant reference = Instant.now();
        setComplaintInstant(road, "created_at", reference.minus(Duration.ofDays(3)));
        setComplaintInstant(water, "created_at", reference.minus(Duration.ofDays(1)));
        setComplaintInstant(otherDept, "created_at", reference.minus(Duration.ofHours(6)));
        setComplaintInstant(unassigned, "created_at", reference.minus(Duration.ofHours(2)));

        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(
                        road.toString(), water.toString())))
                .andExpect(jsonPath("$.content[0].id").value(water.toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(deptA.getId().toString()));

        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(otherDept.toString()));

        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .param("size", "1").param("page", "0")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(water.toString()));
        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .param("size", "1").param("page", "1")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(road.toString()));

        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .param("sort", "createdAt,asc")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(road.toString()));
        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .param("sort", "complaintNumber,asc")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].complaintNumber")
                        .value(complaintNumber(road).compareTo(complaintNumber(water)) < 0
                                ? complaintNumber(road) : complaintNumber(water)));
        // Unknown sort properties fall back to createdAt,desc.
        mockMvc.perform(get(BASE + "/department/complaints/queue")
                        .param("sort", "notAColumn,asc")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(water.toString()));

        // The pre-Phase-7 list endpoint remains a plain, department-scoped array.
        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        road.toString(), water.toString())));
        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherDept.toString()));
    }

    @Test
    void queueFiltersUseOnlyPersistedRowsAndDimensions() throws Exception {
        IssueCategory lights = new IssueCategory();
        lights.setCode("LIGHT");
        lights.setName("Street Lights");
        lights.setDisplayOrder(3);
        lights.setActive(true);
        categories.save(lights);

        UUID road = submitComplaint(citizenAToken, "Pothole near the school gate needs repair");
        UUID water = submitComplaint(citizenBToken, "Water leak under the bridge needs repair");
        assign(road, deptA.getId());
        assign(water, deptA.getId());

        Instant reference = Instant.now();
        setComplaintInstant(road, "created_at", reference.minus(Duration.ofDays(10)));
        setComplaintInstant(water, "created_at", reference.minus(Duration.ofDays(8)));
        setComplaintInstant(road, "resolution_due_at", reference.plus(Duration.ofDays(2)));
        setComplaintInstant(water, "resolution_due_at", reference.minus(Duration.ofDays(1)));
        setComplaintColumn(road, "category_id", roadCategory.getId());
        setComplaintColumn(water, "category_id", lights.getId());
        setComplaintColumn(road, "ward", "WARD-12");
        setComplaintColumn(water, "ward", "WARD-09");
        setComplaintColumn(road, "zone", "NORTH");
        setComplaintColumn(water, "zone", "SOUTH");
        setComplaintColumn(road, "severity", "HIGH");
        setComplaintColumn(water, "severity", "LOW");

        assertQueueIds(deptAToken, params("status", "ASSIGNED"), road, water);
        assertQueueIds(deptAToken, params("status", "ACKNOWLEDGED"));
        assertQueueIds(deptAToken, params("status", "ASSIGNED", "status", "ACKNOWLEDGED"), road, water);

        assertQueueIds(deptAToken, params("categoryCode", "ROAD"), road);
        assertQueueIds(deptAToken, params("categoryCode", "LIGHT"), water);
        assertQueueIds(deptAToken, params("categoryCode", "MISSING"));

        assertQueueIds(deptAToken, params("ward", "WARD-12"), road);
        assertQueueIds(deptAToken, params("ward", "WARD-09"), water);
        assertQueueIds(deptAToken, params("zone", "NORTH"), road);
        assertQueueIds(deptAToken, params("zone", "SOUTH"), water);

        assertQueueIds(deptAToken, params("severity", "HIGH"), road);
        assertQueueIds(deptAToken, params("severity", "LOW"), water);
        assertQueueIds(deptAToken, params("severity", "CRITICAL"));

        assertQueueIds(deptAToken, params("search", "pothole"), road);
        assertQueueIds(deptAToken, params("search", "WATER LEAK"), water);
        assertQueueIds(deptAToken, params("search", "WARD-09"), water);
        assertQueueIds(deptAToken, params("search", "New Delhi"), road, water);
        assertQueueIds(deptAToken, params("search", "not present"));

        assertQueueIds(deptAToken, params("overdue", "true"), water);
        assertQueueIds(deptAToken, params("overdue", "false"), road);

        Instant from = reference.minus(Duration.ofDays(9));
        assertQueueIds(deptAToken, params("from", from.toString()), water);
        assertQueueIds(deptAToken, params("to", from.toString()), road);
        assertQueueIds(deptAToken,
                params("from", reference.minus(Duration.ofDays(20)).toString(),
                        "to", reference.minus(Duration.ofDays(15)).toString()));

        // Manual admin assignments are not AI assignments until an AUTO row exists.
        assertQueueIds(deptAToken, params("aiAssigned", "false"), road, water);
        assertQueueIds(deptAToken, params("aiAssigned", "true"));
        ComplaintAssignment auto = new ComplaintAssignment();
        auto.setComplaint(complaints.getReferenceById(road));
        auto.setDepartment(deptA);
        auto.setAssignmentType(AssignmentType.AUTO);
        auto.setActive(true);
        auto.setAssignedAt(Instant.now());
        auto.setCreatedAt(Instant.now());
        assignments.save(auto);
        assertQueueIds(deptAToken, params("aiAssigned", "true"), road);
        assertQueueIds(deptAToken, params("aiAssigned", "false"), water);

        // Legacy list still honours status/date filters and never leaks dept B.
        UUID otherDept = submitComplaint(citizenAToken, "Pothole in the other department");
        assign(otherDept, deptB.getId());
        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(road.toString(), water.toString())));
        mockMvc.perform(get(BASE + "/department/complaints")
                        .param("status", ComplaintStatus.ASSIGNED.name())
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(BASE + "/department/complaints")
                        .param("from", from.toString())
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(water.toString()));
        mockMvc.perform(get(BASE + "/department/complaints")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherDept.toString()));
    }

    @Test
    void overdueFalseShouldExcludeOverdueComplaints() throws Exception {
        UUID fresh = submitComplaint(citizenAToken, "Pothole with a future resolution deadline");
        UUID overdue = submitComplaint(citizenBToken, "Pothole with a missed resolution deadline");
        assign(fresh, deptA.getId());
        assign(overdue, deptA.getId());
        Instant now = Instant.now();
        setComplaintInstant(fresh, "resolution_due_at", now.plus(Duration.ofDays(2)));
        setComplaintInstant(overdue, "resolution_due_at", now.minus(Duration.ofDays(2)));

        assertQueueIds(deptAToken, params("overdue", "false"), fresh);
    }

    @Test
    void analyticsUseRealRowsForAllSupportedPeriodsWithoutCrossDepartmentLeakage() throws Exception {
        IssueCategory lights = new IssueCategory();
        lights.setCode("LIGHT");
        lights.setName("Street Lights");
        lights.setDisplayOrder(3);
        lights.setActive(true);
        categories.save(lights);

        UUID recent = submitComplaint(citizenAToken, "Pothole resolved quickly on the main road");
        UUID mid = submitComplaint(citizenAToken, "Pothole received within the thirty day window");
        UUID old = submitComplaint(citizenBToken, "Old street light fault awaiting repair");
        UUID otherDept = submitComplaint(citizenAToken, "Pothole owned by the other department");
        UUID unassigned = submitComplaint(citizenBToken, "Unassigned complaint for analytics");
        assign(recent, deptA.getId());
        assign(mid, deptA.getId());
        assign(old, deptA.getId());
        assign(otherDept, deptB.getId());

        Instant reference = Instant.now();
        setComplaintInstant(recent, "created_at", reference.minus(Duration.ofDays(3)));
        setComplaintInstant(mid, "created_at", reference.minus(Duration.ofDays(20)));
        setComplaintInstant(old, "created_at", reference.minus(Duration.ofDays(45)));
        setComplaintInstant(otherDept, "created_at", reference.minus(Duration.ofDays(2)));
        setComplaintInstant(unassigned, "created_at", reference.minus(Duration.ofDays(1)));
        setComplaintInstant(recent, "resolved_at", reference.minus(Duration.ofDays(2)));
        setComplaintInstant(old, "resolution_due_at", reference.minus(Duration.ofDays(1)));

        setComplaintColumn(recent, "status", ComplaintStatus.RESOLVED.name());
        setComplaintColumn(recent, "severity", "MEDIUM");
        setComplaintColumn(recent, "ward", "WARD-12");
        setComplaintColumn(recent, "category_id", roadCategory.getId());
        setComplaintColumn(mid, "category_id", lights.getId());
        setComplaintColumn(mid, "ward", "WARD-09");
        setComplaintColumn(old, "status", ComplaintStatus.ASSIGNED.name());
        setComplaintColumn(old, "severity", "HIGH");
        setComplaintColumn(old, "ward", "WARD-09");
        setComplaintColumn(old, "category_id", lights.getId());
        setComplaintColumn(otherDept, "status", ComplaintStatus.ASSIGNED.name());
        setComplaintColumn(otherDept, "ward", "WARD-12");
        setComplaintColumn(otherDept, "category_id", roadCategory.getId());

        assertThat(complaints.countByRelatedDepartmentId(deptA.getId())).isEqualTo(3);

        JsonNode seven = analytics(deptAToken, 7);
        assertThat(seven.get("total").asLong()).isEqualTo(3);
        assertThat(seven.get("byStatus").get("RESOLVED").asLong()).isEqualTo(1);
        assertThat(seven.get("byStatus").get("ASSIGNED").asLong()).isEqualTo(2);
        assertThat(seven.get("resolved").asLong()).isEqualTo(1);
        assertThat(seven.get("overdue").asLong()).isEqualTo(1);
        assertThat(seven.get("unassigned").asLong()).isZero();
        assertThat(seven.get("pending").asLong()).isZero();
        assertThat(seven.get("accepted").asLong()).isEqualTo(2);
        assertThat(seven.get("inProgress").asLong()).isZero();
        assertThat(seven.get("rejected").asLong()).isZero();
        assertThat(seven.get("closed").asLong()).isZero();
        assertThat(seven.get("periodDays").asInt()).isEqualTo(7);
        assertThat(seven.get("receivedInPeriod").asLong()).isEqualTo(1);
        assertThat(seven.get("resolvedInPeriod").asLong()).isEqualTo(1);
        assertThat(seven.get("averageResolutionHours").asDouble())
                .isBetween(23.9, 24.1);
        assertThat(seven.get("averageOpenAgeHours").asDouble()).isPositive();
        assertThat(seven.get("slaCompliancePercent").asDouble()).isEqualTo(100.0);
        assertThat(seven.get("byCategory").size()).isEqualTo(1);
        assertThat(seven.get("byCategory").get(0).get("code").asText()).isEqualTo("ROAD");
        assertThat(seven.get("byCategory").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(seven.get("byWard").size()).isEqualTo(1);
        assertThat(seven.get("byWard").get(0).get("ward").asText()).isEqualTo("WARD-12");
        assertThat(seven.get("trend").size()).isEqualTo(8);
        assertThat(trendReceived(seven)).isEqualTo(1);

        JsonNode thirty = analytics(deptAToken, 30);
        assertThat(thirty.get("periodDays").asInt()).isEqualTo(30);
        assertThat(thirty.get("total").asLong()).isEqualTo(3);
        assertThat(thirty.get("receivedInPeriod").asLong()).isEqualTo(2);
        assertThat(thirty.get("byCategory").size()).isEqualTo(2);
        assertThat(thirty.get("byCategory").get(0).get("code").asText()).isEqualTo("LIGHT");
        assertThat(thirty.get("byCategory").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(thirty.get("byCategory").get(1).get("code").asText()).isEqualTo("ROAD");
        assertThat(thirty.get("byCategory").get(1).get("count").asLong()).isEqualTo(1);
        assertThat(thirty.get("byWard").size()).isEqualTo(2);
        assertThat(thirty.get("byWard").get(0).get("ward").asText()).isEqualTo("WARD-09");
        assertThat(thirty.get("byWard").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(thirty.get("byWard").get(1).get("ward").asText()).isEqualTo("WARD-12");
        assertThat(thirty.get("byWard").get(1).get("count").asLong()).isEqualTo(1);
        assertThat(thirty.get("trend").size()).isEqualTo(31);
        assertThat(trendReceived(thirty)).isEqualTo(2);

        JsonNode ninety = analytics(deptAToken, 90);
        assertThat(ninety.get("periodDays").asInt()).isEqualTo(90);
        assertThat(ninety.get("total").asLong()).isEqualTo(3);
        assertThat(ninety.get("receivedInPeriod").asLong()).isEqualTo(3);
        assertThat(ninety.get("byCategory").size()).isEqualTo(2);
        assertThat(ninety.get("trend").size()).isBetween(13, 14);
        assertThat(trendReceived(ninety)).isEqualTo(3);

        JsonNode deptBAnalytics = analytics(deptBToken, 30);
        assertThat(deptBAnalytics.get("total").asLong()).isEqualTo(1);
        assertThat(deptBAnalytics.get("byStatus").get("ASSIGNED").asLong()).isEqualTo(1);
        assertThat(deptBAnalytics.get("byCategory").get(0).get("code").asText()).isEqualTo("ROAD");
        assertThat(deptBAnalytics.get("byWard").get(0).get("ward").asText()).isEqualTo("WARD-12");

        mockMvc.perform(get(BASE + "/department/analytics")
                        .param("periodDays", "7")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/department/analytics")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void overviewActivityAndNotificationsAreScopedToOwnDepartment() throws Exception {
        UUID mine = submitComplaint(citizenAToken, "Pothole awaiting repair on my street");
        UUID theirs = submitComplaint(citizenBToken, "Pothole awaiting repair in another ward");
        assign(mine, deptA.getId());
        assign(theirs, deptB.getId());

        mockMvc.perform(post(BASE + "/department/complaints/" + mine + "/accept")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "/department/complaints/" + mine + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new StatusUpdateRequest(ComplaintStatus.IN_PROGRESS, "Work started"))))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/department/complaints/" + theirs + "/accept")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk());

        long expectedActivity = statusHistory.findRecentByDepartmentId(
                deptA.getId(), PageRequest.of(0, 100)).size();
        String activity = mockMvc.perform(get(BASE + "/department/activity")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value((int) expectedActivity))
                .andExpect(jsonPath("$[*].complaintId",
                        everyItem(is(mine.toString()))))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(activity).size()).isEqualTo(expectedActivity);

        mockMvc.perform(get(BASE + "/department/activity")
                        .param("limit", "1")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].complaintId").value(mine.toString()));
        mockMvc.perform(get(BASE + "/department/activity")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].complaintId",
                        everyItem(is(theirs.toString()))));
        mockMvc.perform(get(BASE + "/department/activity")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isForbidden());

        long myNotifications = notifications.findAllByRecipientIdOrderByCreatedAtDesc(
                deptAdminA.getId()).size();
        mockMvc.perform(get(BASE + "/department/notifications")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value((int) myNotifications))
                .andExpect(jsonPath("$[*].complaintId", everyItem(is(mine.toString()))))
                .andExpect(jsonPath("$[*].type", hasItem(NotificationType.COMPLAINT_ASSIGNED.name())));

        long unread = notifications.countByRecipientIdAndReadAtIsNull(deptAdminA.getId());
        mockMvc.perform(get(BASE + "/notifications/unread-count")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(unread));

        mockMvc.perform(post(BASE + "/notifications/read-all")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/notifications/unread-count")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(deptAdminA.getId())).isZero();

        mockMvc.perform(get(BASE + "/department/overview")
                        .param("periodDays", "7")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.departmentId").value(deptA.getId().toString()))
                .andExpect(jsonPath("$.analytics.periodDays").value(7))
                .andExpect(jsonPath("$.analytics.total").value(1))
                .andExpect(jsonPath("$.analytics.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.activity.length()").value((int) expectedActivity))
                .andExpect(jsonPath("$.activity[*].complaintId", everyItem(is(mine.toString()))));
    }

    @Test
    void superAdminRoutesRemainUnaffectedByDepartmentSurface() throws Exception {
        UUID first = submitComplaint(citizenAToken, "Pothole that stays unassigned for admin view");
        UUID second = submitComplaint(citizenBToken, "Another pothole that stays unassigned");

        mockMvc.perform(get(BASE + "/admin/complaints")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        first.toString(), second.toString())));
        mockMvc.perform(get(BASE + "/admin/complaints/unassigned")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(BASE + "/admin/departments")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        assign(first, deptA.getId());
        mockMvc.perform(get(BASE + "/admin/complaints/unassigned")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        for (String token : List.of(deptAToken, citizenAToken)) {
            mockMvc.perform(get(BASE + "/admin/complaints")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/admin/departments")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    private void assertQueueIds(String token, String[] queryParams, UUID... expectedIds)
            throws Exception {
        var request = get(BASE + "/department/complaints/queue")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < queryParams.length; i += 2) {
            request = request.param(queryParams[i], queryParams[i + 1]);
        }
        String[] ids = java.util.Arrays.stream(expectedIds)
                .map(UUID::toString).toArray(String[]::new);
        request = request.param("size", "100");
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ids.length))
                .andExpect(jsonPath("$.content.length()").value(ids.length));
        if (ids.length > 0) {
            result.andExpect(jsonPath("$.content[*].id", containsInAnyOrder(ids)));
        }
    }

    private static String[] params(String... pairs) {
        return pairs;
    }

    private JsonNode analytics(String token, int periodDays) throws Exception {
        String body = mockMvc.perform(get(BASE + "/department/analytics")
                        .param("periodDays", String.valueOf(periodDays))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static long trendReceived(JsonNode analytics) {
        long total = 0;
        for (JsonNode point : analytics.get("trend")) {
            total += point.get("received").asLong();
        }
        return total;
    }

    private String complaintNumber(UUID id) {
        return complaints.findById(id).orElseThrow().getComplaintNumber();
    }
}
