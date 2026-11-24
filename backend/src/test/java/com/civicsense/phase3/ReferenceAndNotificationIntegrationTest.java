package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.civicsense.domain.NotificationType;
import com.civicsense.reference.dto.ReferenceDtos.CategoryPatchRequest;
import com.civicsense.reference.dto.ReferenceDtos.CategoryRequest;

class ReferenceAndNotificationIntegrationTest extends Phase3TestSupport {

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void categoryTreeIsPublicAndWritableOnlyBySuperAdmin() throws Exception {
        mockMvc.perform(get(BASE + "/meta/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ROAD"))
                .andExpect(jsonPath("$[0].children[0].code").value("POTHOLE"));

        mockMvc.perform(post(BASE + "/admin/reference/categories")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("LIGHT", "Street Lights", null, null, null, null, 3))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/admin/reference/categories")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryRequest("LIGHT", "Street Lights", null, "ROAD", null, null, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LIGHT"));

        mockMvc.perform(patch(BASE + "/admin/reference/categories/" + roadCategory.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryPatchRequest("Roads and Highways", null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Roads and Highways"));
    }

    @Test
    void inUseCategoryCannotBeDeactivated() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Pothole on the main street needs a fix.");

        mockMvc.perform(patch(BASE + "/admin/reference/categories/" + potholeCategory.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryPatchRequest(null, null, null, null, null, false))))
                .andExpect(status().isConflict());

        assertThat(complaints.findById(complaintId)).isPresent();
    }

    @Test
    void serviceAreasAreValidatedAndScopedToOwnDepartment() throws Exception {
        String polygon = "POLYGON((77.1 28.5, 77.3 28.5, 77.3 28.7, 77.1 28.7, 77.1 28.5))";

        mockMvc.perform(post(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Ward 12 Roads", "boundaryWkt", polygon,
                                "coversWard", "WARD-12", "jurisdictionId", ward12.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(deptA.getId().toString()))
                .andExpect(jsonPath("$.jurisdictionCode").value("WARD-12"));

        mockMvc.perform(post(BASE + "/department/service-areas")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bad area", "boundaryWkt", "NOT-A-POLYGON"))))
                .andExpect(status().isBadRequest());

        UUID areaId = serviceAreas.findAllByDepartmentIdAndActiveTrue(deptA.getId()).get(0).getId();
        mockMvc.perform(delete(BASE + "/department/service-areas/" + areaId)
                        .header("Authorization", "Bearer " + deptBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/department/service-areas/" + areaId)
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        assertThat(serviceAreas.findAllByDepartmentIdAndActiveTrue(deptA.getId())).isEmpty();
    }

    @Test
    void notificationsAreRecipientScopedAndCanBeMarkedRead() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Broken drain cover on the main road.");

        mockMvc.perform(get(BASE + "/notifications")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());

        var citizenNotifications = notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId());
        assertThat(citizenNotifications).anyMatch(item ->
                item.getType() == NotificationType.COMPLAINT_SUBMITTED
                        && item.getComplaint().getId().equals(complaintId));

        mockMvc.perform(post(BASE + "/admin/complaints/" + complaintId + "/assign")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", deptA.getId().toString(),
                                "reason", "Assigning to the verified road department"))))
                .andExpect(status().isOk());

        citizenNotifications = notifications.findAllByRecipientIdOrderByCreatedAtDesc(citizenA.getId());
        assertThat(citizenNotifications).anyMatch(item ->
                item.getType() == NotificationType.COMPLAINT_ASSIGNED
                        && item.getComplaint().getId().equals(complaintId));
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(citizenA.getId())).isPositive();

        UUID notificationId = citizenNotifications.get(0).getId();
        mockMvc.perform(patch(BASE + "/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + citizenBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(BASE + "/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());
        assertThat(notifications.findById(notificationId).orElseThrow().getReadAt()).isNotNull();

        mockMvc.perform(post(BASE + "/notifications/read-all")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk());
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(citizenA.getId())).isZero();
    }

    @Test
    void commentsRequireAccessToTheComplaint() throws Exception {
        UUID complaintId = submitComplaint(citizenAToken, "Fallen tree blocking the footpath.");

        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "I can send a photo if needed"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/citizen/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + citizenBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "This is not my complaint"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(BASE + "/citizen/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("I can send a photo if needed"));
    }
}
