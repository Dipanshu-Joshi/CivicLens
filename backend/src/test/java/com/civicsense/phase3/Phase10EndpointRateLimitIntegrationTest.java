package com.civicsense.phase3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(properties = {
        "civicsense.rate-limits.global-capacity=10000",
        "civicsense.rate-limits.submit-capacity=2",
        "civicsense.rate-limits.presign-capacity=2",
        "civicsense.auth.reset-rate-limit=2"
})
class Phase10EndpointRateLimitIntegrationTest extends Phase3TestSupport {

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void complaintSubmissionLimitReturns429AfterConfiguredAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(BASE + "/citizen/complaints")
                            .with(ip("10.20.0.1"))
                            .header("Authorization", "Bearer " + citizenAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(BASE + "/citizen/complaints")
                        .with(ip("10.20.0.1"))
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void presignLimitReturns429AfterConfiguredAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(BASE + "/uploads/presign")
                            .with(ip("10.20.0.2"))
                            .header("Authorization", "Bearer " + citizenAToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(BASE + "/uploads/presign")
                        .with(ip("10.20.0.2"))
                        .header("Authorization", "Bearer " + citizenAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void passwordResetConfirmLimitReturns429AfterConfiguredAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(BASE + "/auth/reset-password")
                            .with(ip("10.20.0.3"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"invalid-token\",\"password\":\"NewStr0ng!Pass1\"}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(BASE + "/auth/reset-password")
                        .with(ip("10.20.0.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid-token\",\"password\":\"NewStr0ng!Pass1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
