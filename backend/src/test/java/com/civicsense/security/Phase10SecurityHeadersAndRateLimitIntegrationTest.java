package com.civicsense.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "civicsense.rate-limits.global-capacity=2",
        "civicsense.rate-limits.global-window-minutes=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase10SecurityHeadersAndRateLimitIntegrationTest {

    private static final String CORRELATION_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void securityHeadersIncludeConfiguredCsp() throws Exception {
        var response = mockMvc.perform(get("/meta/health"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("frame-ancestors 'none'", "object-src 'none'");
    }

    @Test
    void globalRateLimitReturnsRetryAfterAndRfcProblemBody() throws Exception {
        String ip = "203.0.113.77";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/forgot-password")
                            .with(ip(ip))
                            .header("X-Correlation-Id", CORRELATION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("email", "unknown@example.com"))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/forgot-password")
                        .with(ip(ip))
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "unknown@example.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    private RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
