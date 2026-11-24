package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.civicsense.domain.AuditLog;

class Phase12CorrelationIntegrationTest extends Phase3TestSupport {

    private static final String CORRELATION_ID = "11111111-1111-4111-8111-111111111111";

    @BeforeEach
    void setUp() {
        resetAndSeed();
    }

    @Test
    void generatedCorrelationIdIsEchoedOnHealthResponse() throws Exception {
        // Local test profile intentionally reports optional providers DOWN, so a
        // 503 is the expected health gate result; the correlation header still
        // must be present on that response.
        var response = mockMvc.perform(get("/meta/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn().getResponse();

        assertThat(UUID.fromString(response.getHeader("X-Correlation-Id"))).isNotNull();
    }

    @Test
    void suppliedCorrelationIdIsEchoedAndReusedInErrorBodies() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", citizenA.getEmail(), "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));

        mockMvc.perform(get("/citizen/complaints")
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @Test
    void correlationIdReachesPersistedAuditRows() throws Exception {
        mockMvc.perform(post("/admin/departments/" + unverifiedDept.getId() + "/verify")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("decision", "VERIFIED", "reason", "Phase 12 audit correlation"))))
                .andExpect(status().isOk());

        AuditLog latest = auditLogs.findAll().stream()
                .max(Comparator.comparing(AuditLog::getCreatedAt).thenComparing(AuditLog::getId))
                .orElseThrow();
        assertThat(latest.getCorrelationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void corsPreflightExposesCorrelationHeader() throws Exception {
        mockMvc.perform(options("/meta/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Correlation-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Correlation-Id")));
    }
}
