package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.domain.PasswordResetToken;
import com.civicsense.notification.OutboxDispatcher;
import com.fasterxml.jackson.databind.JsonNode;

class Phase10PasswordResetIntegrationTest extends Phase3TestSupport {

    private static final String GENERIC_MESSAGE =
            "If the account exists, a password reset link has been sent.";
    private static final String NEW_PASSWORD = "NewStr0ng!Pass1";

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void forgotPasswordCreatesHashedSingleUseTokenAndOutboxWithoutInAppLeak() throws Exception {
        String rawToken = forgotToken(citizenA.getEmail(), "10.10.0.1");

        PasswordResetToken stored = passwordResetTokens.findAll().getFirst();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(stored.getUsedAt()).isNull();

        OutboxEvent event = latestResetEvent();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("resetToken").asText()).isEqualTo(rawToken);
        assertThat(payload.get("resetUrl").asText()).contains("/reset-password?token=");

        assertThat(outboxDispatcher.dispatchDue(50)).isPositive();
        assertThat(outboxEvents.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PROCESSED);
        assertThat(notifications.findAll()).isEmpty();

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());

        assertThat(passwordResetTokens.findById(stored.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThat(login(citizenA.getEmail(), NEW_PASSWORD, "10.10.0.1")
                .getResponse().getStatus()).isEqualTo(200);

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "password", "Another!Pass1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEmailDoesNotEnumerateAccountsOrCreateTokens() throws Exception {
        String message = forgotMessage("nobody@example.com", "10.10.0.2");

        assertThat(message).isEqualTo(GENERIC_MESSAGE);
        assertThat(passwordResetTokens.findAll()).isEmpty();
        assertThat(outboxEvents.findAll()).isEmpty();
    }

    @Test
    void weakPasswordIsRejectedAndLeavesTokenUnused() throws Exception {
        String rawToken = forgotToken(citizenA.getEmail(), "10.10.0.3");

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        assertThat(passwordResetTokens.findAll().getFirst().getUsedAt()).isNull();
    }

    @Test
    void expiredAndUnknownTokensAreRejected() throws Exception {
        String rawToken = forgotToken(citizenA.getEmail(), "10.10.0.4");
        PasswordResetToken token = passwordResetTokens.findAll().getFirst();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        passwordResetTokens.save(token);

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "password", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "not-a-real-token", "password", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRevokesExistingRefreshTokenFamilies() throws Exception {
        String firstToken = forgotToken(citizenA.getEmail(), "10.10.0.5");
        reset(firstToken, NEW_PASSWORD);
        MvcResult login = login(citizenA.getEmail(), NEW_PASSWORD, "10.10.0.5");
        var refreshCookie = login.getResponse().getCookie("civicsense_refresh");
        assertThat(refreshCookie).isNotNull();

        String secondToken = forgotToken(citizenA.getEmail(), "10.10.0.5");
        reset(secondToken, "Another!Pass1");

        mockMvc.perform(post(BASE + "/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private String forgotMessage(String email, String ip) throws Exception {
        String body = mockMvc.perform(post(BASE + "/auth/forgot-password")
                        .with(ip(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("message").asText();
    }

    private String forgotToken(String email, String ip) throws Exception {
        forgotMessage(email, ip);
        JsonNode payload = objectMapper.readTree(latestResetEvent().getPayload());
        return payload.get("resetToken").asText();
    }

    private void reset(String rawToken, String password) throws Exception {
        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken, "password", password))))
                .andExpect(status().isOk());
    }

    private MvcResult login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post(BASE + "/auth/login")
                        .with(ip(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private OutboxEvent latestResetEvent() {
        return outboxEvents.findAll().stream()
                .filter(event -> "auth.password_reset_requested".equals(event.getEventType()))
                .max(Comparator.comparing(OutboxEvent::getCreatedAt)
                        .thenComparing(OutboxEvent::getId))
                .orElseThrow();
    }

    private RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
