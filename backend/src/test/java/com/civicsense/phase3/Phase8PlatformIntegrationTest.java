package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.civicsense.admin.AdminExceptions.InvalidAdminActionException;
import com.civicsense.admin.AdminPlatformService;
import com.civicsense.admin.AdminDtos.UserRoleRequest;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.Role;
import com.civicsense.domain.ServiceArea;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.security.AuthenticatedUser;

/** Phase 8 platform governance: RBAC, real-data insight, departments, users and audit. */
class Phase8PlatformIntegrationTest extends Phase7TestSupport {

    @Autowired
    private AdminPlatformService platformService;

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void newAdminRoutesRejectUnauthenticatedCitizenAndDepartmentAdmin() throws Exception {
        String complaintId = UUID.randomUUID().toString();
        String[] readRoutes = {
                "/admin/overview",
                "/admin/analytics",
                "/admin/activity",
                "/admin/departments/" + deptA.getId(),
                "/admin/users",
                "/admin/audit-logs",
                "/admin/categories",
                "/admin/jurisdictions",
                "/admin/service-areas",
                "/admin/complaints/queue",
                "/admin/complaints/" + complaintId + "/analyses"
        };
        for (String route : readRoutes) {
            mockMvc.perform(get(BASE + route)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(BASE + route).header("Authorization", "Bearer " + citizenAToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + route).header("Authorization", "Bearer " + deptAToken))
                    .andExpect(status().isForbidden());
        }

        String reasonBody = json(Map.of("reason", "Documented administrative review completed."));
        mockMvc.perform(patch(BASE + "/admin/departments/" + deptA.getId() + "/status")
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SUSPENDED",
                                "reason", "Documented administrative review completed."))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/admin/users/" + citizenB.getId() + "/status")
                        .header("Authorization", "Bearer " + deptAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/admin/audit-logs")
                        .header("Authorization", "Bearer " + citizenAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void overviewAndAnalyticsUsePersistedRows() throws Exception {
        submitComplaint(citizenAToken, "A pothole is obstructing the main road near the market.");

        mockMvc.perform(get(BASE + "/admin/overview")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.stats.total").value(1))
                .andExpect(jsonPath("$.byStatus.length()").value(1))
                .andExpect(jsonPath("$.activity.length()").value(1))
                .andExpect(jsonPath("$.audit.length()").value(1));

        mockMvc.perform(get(BASE + "/admin/analytics")
                        .param("periodDays", "7")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.stats.total").value(1))
                .andExpect(jsonPath("$.activity.length()").value(0))
                .andExpect(jsonPath("$.audit.length()").value(0));
    }

    @Test
    void departmentSuspensionTakesEffectOnExistingSessionAndReactivationRestoresAccess()
            throws Exception {
        mockMvc.perform(get(BASE + "/department/me")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch(BASE + "/admin/departments/{id}/status", deptA.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SUSPENDED",
                                "reason", "Suspended pending an integrity review."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.acceptsComplaints").value(false));
        assertThat(departments.findById(deptA.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.SUSPENDED);

        mockMvc.perform(get(BASE + "/department/me")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(BASE + "/admin/departments/{id}/status", deptA.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "VERIFIED",
                                "reason", "Integrity review cleared the department."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.acceptsComplaints").value(true));

        mockMvc.perform(get(BASE + "/department/me")
                        .header("Authorization", "Bearer " + deptAToken))
                .andExpect(status().isOk());
        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction().equals("DEPARTMENT_STATUS")
                && log.getReason() != null && log.getReason().contains("integrity review"));
    }

    @Test
    void verificationAndRejectionRequireReasonAndAreAudited() throws Exception {
        mockMvc.perform(post(BASE + "/admin/departments/{id}/verify", unverifiedDept.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECTED", "reason", "too short"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE + "/admin/departments/{id}/verify", unverifiedDept.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "REJECTED",
                                "reason", "Registration documents are incomplete."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));

        assertThat(auditLogs.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "department", unverifiedDept.getId().toString()))
                .anyMatch(log -> log.getAction().equals("DEPARTMENT_VERIFY")
                        && "Registration documents are incomplete.".equals(log.getReason()));
    }

    @Test
    void userManagementBlocksSelfChangesAndAuditsValidChanges() throws Exception {
        mockMvc.perform(patch(BASE + "/admin/users/{id}/status", superAdmin.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SUSPENDED",
                                "reason", "Attempted self suspension during review."))))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(BASE + "/admin/users/{id}/role", citizenB.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "DEPARTMENT_ADMIN",
                                "reason", "Promoted after verified department onboarding."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEPARTMENT_ADMIN"));

        mockMvc.perform(patch(BASE + "/admin/users/{id}/status", citizenA.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SUSPENDED",
                                "reason", "Account temporarily suspended after abuse report."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
        assertThat(users.findById(citizenA.getId()).orElseThrow().getAccountStatus().name())
                .isEqualTo("SUSPENDED");

        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction().equals("USER_ROLE")
                && log.getReason().contains("verified department onboarding"));
        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction().equals("USER_STATUS")
                && log.getReason().contains("abuse report"));

        assertThatThrownBy(() -> platformService.updateUserRole(superAdmin.getId(),
                new UserRoleRequest(Role.CITIZEN, "Attempting to demote the last super admin."),
                new AuthenticatedUser(UUID.randomUUID(), "probe@example.com", Role.SUPER_ADMIN,
                        null, null)))
                .isInstanceOf(InvalidAdminActionException.class);
    }

    @Test
    void auditLogExplorationFiltersByActionTargetActorAndDateAndExposesReason() throws Exception {
        Instant from = Instant.now().minusSeconds(120);
        mockMvc.perform(patch(BASE + "/admin/departments/{id}/status", deptA.getId())
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "SUSPENDED",
                                "reason", "Audit filter verification for suspension."))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/admin/audit-logs")
                        .param("action", "DEPARTMENT_STATUS")
                        .param("entityType", "department")
                        .param("entityId", deptA.getId().toString())
                        .param("actorId", superAdmin.getId().toString())
                        .param("from", from.toString())
                        .param("to", Instant.now().plusSeconds(60).toString())
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("DEPARTMENT_STATUS"))
                .andExpect(jsonPath("$.content[0].actorRole").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.content[0].reason")
                        .value("Audit filter verification for suspension."))
                .andExpect(jsonPath("$.content[0].correlationId").exists());
    }

    @Test
    void referenceAndServiceAreaReadsReturnPersistedRows() throws Exception {
        ServiceArea area = new ServiceArea();
        area.setDepartment(deptA);
        area.setName("Central roads");
        area.setBoundaryWkt("POLYGON ((77.0 28.0, 78.0 28.0, 78.0 29.0, 77.0 29.0, 77.0 28.0))");
        area.setCoversWard("WARD-12");
        area.setActive(true);
        serviceAreas.save(area);

        mockMvc.perform(get(BASE + "/admin/categories")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'ROAD')]").exists());
        mockMvc.perform(get(BASE + "/admin/jurisdictions")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("WARD-12"));
        mockMvc.perform(get(BASE + "/admin/service-areas")
                        .param("departmentId", deptA.getId().toString())
                        .param("active", "true")
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departmentId").value(deptA.getId().toString()));
    }
}
