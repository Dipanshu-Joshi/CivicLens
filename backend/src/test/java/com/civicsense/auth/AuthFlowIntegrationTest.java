package com.civicsense.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.civicsense.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTest {

    // MockMvc applies server.servlet.context-path itself; paths are relative to it.
    private static final String BASE = "";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    @Order(1)
    void citizenCanRegisterLoginReadMeRefreshAndLogout() throws Exception {
        String email = "asha@example.com";
        registerCitizen(email, "Str0ng!Passw0rd");

        MvcResult login = mockMvc.perform(post(BASE + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Str0ng!Passw0rd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtService.accessTokenTtlSeconds()))
                .andExpect(jsonPath("$.user.role").value("CITIZEN"))
                .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        var refreshCookie = login.getResponse().getCookie("civicsense_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get(BASE + "/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.permissions[0]").value("COMPLAINT_SUBMIT"));

        mockMvc.perform(get(BASE + "/auth/me"))
                .andExpect(status().isUnauthorized());

        MvcResult refreshed = mockMvc.perform(post(BASE + "/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        var rotatedCookie = refreshed.getResponse().getCookie("civicsense_refresh");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // Reusing the rotated cookie revokes the whole token family.
        mockMvc.perform(post(BASE + "/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());

        MvcResult relogin = login(email, "Str0ng!Passw0rd");
        String reloginToken = objectMapper.readTree(relogin.getResponse().getContentAsString())
                .get("accessToken").asText();
        var logoutCookie = relogin.getResponse().getCookie("civicsense_refresh");
        mockMvc.perform(post(BASE + "/auth/logout")
                        .header("Authorization", "Bearer " + reloginToken)
                        .cookie(logoutCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/auth/refresh").cookie(logoutCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void duplicateEmailReturnsConflictAndWeakPasswordReturnsBadRequest() throws Exception {
        registerCitizen("duplicate@example.com", "Str0ng!Passw0rd");

        mockMvc.perform(post(BASE + "/auth/register/citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Duplicate User",
                                "email", "DUPLICATE@example.com",
                                "password", "Str0ng!Passw0rd"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(BASE + "/auth/register/citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Weak Password",
                                "email", "weak@example.com",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @Order(3)
    void repeatedFailedLoginsLockTheAccount() throws Exception {
        registerCitizen("locked@example.com", "Str0ng!Passw0rd");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(BASE + "/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "locked@example.com",
                                    "password", "Wr0ng!Passw0rd"))))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(BASE + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "locked@example.com",
                                "password", "Str0ng!Passw0rd"))))
                .andExpect(status().isTooManyRequests());
    }

    private void registerCitizen(String email, String password) throws Exception {
        mockMvc.perform(post(BASE + "/auth/register/citizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Test User",
                                "email", email,
                                "password", password))))
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
