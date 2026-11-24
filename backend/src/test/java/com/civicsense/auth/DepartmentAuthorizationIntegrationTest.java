package com.civicsense.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.Role;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DepartmentAuthorizationIntegrationTest {

    // MockMvc applies server.servlet.context-path itself; paths are relative to it.
    private static final String BASE = "";
    private static final String ADMIN_PASSWORD = "Sup3r!SecurePass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String deptAdminToken;
    private static String citizenToken;
    private static UUID departmentId;
    private static UUID otherDepartmentId;

    @Test
    @Order(1)
    void unverifiedDepartmentCannotAccessDepartmentEndpoints() throws Exception {
        departmentId = registerDepartment("roads@example.com", "Road Maintenance Department");
        otherDepartmentId = registerDepartment("waste@example.com", "Waste Management Department");

        // The owner account uses a personal email distinct from the department's
        // official email, matching the documented registration contract.
        MvcResult login = login("owner@example.com", "Str0ng!Passw0rd");
        deptAdminToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        JsonNode me = objectMapper.readTree(login.getResponse().getContentAsString()).get("user");
        assertThat(me.get("role").asText()).isEqualTo("DEPARTMENT_ADMIN");
        assertThat(me.get("verificationStatus").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(me.get("departmentId").asText()).isEqualTo(departmentId.toString());

        mockMvc.perform(get(BASE + "/department/me").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/admin/departments").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());

        registerCitizen("scope-citizen@example.com");
        MvcResult citizenLogin = login("scope-citizen@example.com", "Str0ng!Passw0rd");
        citizenToken = objectMapper.readTree(citizenLogin.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(get(BASE + "/department/me").header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void superAdminVerifiesDepartmentAndTokenReflectsNewStatusImmediately() throws Exception {
        createSuperAdmin();
        String adminToken = objectMapper.readTree(
                        login("super@example.com", ADMIN_PASSWORD).getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post(BASE + "/admin/departments/" + departmentId + "/verify")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "VERIFIED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.acceptsComplaints").value(true));

        // The token was issued while the department was still pending. Verification
        // is re-read from the database on every request, so it now succeeds.
        mockMvc.perform(get(BASE + "/department/me").header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        // Own department is allowed, another department is not.
        mockMvc.perform(get(BASE + "/department/scope-probe/" + departmentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/department/scope-probe/" + otherDepartmentId)
                        .header("Authorization", "Bearer " + deptAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/admin/departments").header("Authorization", "Bearer " + citizenToken))
                .andExpect(status().isForbidden());
    }

    private void createSuperAdmin() {
        if (userRepository.findByEmailIgnoreCase("super@example.com").isPresent()) {
            return;
        }
        User admin = new User();
        admin.setEmail("super@example.com");
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin.setFullName("Test Super Administrator");
        userRepository.save(admin);
    }

    private UUID registerDepartment(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/auth/register/department")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "department", Map.of(
                                        "name", name,
                                        "departmentType", "ROAD_MAINTENANCE",
                                        "officialEmail", email,
                                        "city", "Bengaluru",
                                        "country", "India"),
                                "administrator", Map.of(
                                        "fullName", "Department Owner",
                                        "email", email.replace("roads@", "owner@").replace("waste@", "owner-waste@"),
                                        "password", "Str0ng!Passw0rd")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING_VERIFICATION"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("departmentId").asText());
    }

    private void registerCitizen(String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register/citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Scope Citizen",
                                "email", email,
                                "password", "Str0ng!Passw0rd"))))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post(BASE + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
