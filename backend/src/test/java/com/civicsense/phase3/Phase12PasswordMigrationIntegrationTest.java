package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.civicsense.security.UpgradingPasswordEncoder;

/** Verifies that legacy BCrypt users are upgraded only after a successful login. */
class Phase12PasswordMigrationIntegrationTest extends Phase3TestSupport {

    private static final String LEGACY_PASSWORD = "Legacy!Pass123";

    @BeforeEach
    void setUp() {
        resetAndSeed();
        citizenA.setPasswordHash(new BCryptPasswordEncoder(4).encode(LEGACY_PASSWORD));
        users.save(citizenA);
    }

    @Test
    void successfulLoginRehashesLegacyPasswordWithoutForcingReset() throws Exception {
        mockMvc.perform(post(BASE + "/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.44.0.11");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", citizenA.getEmail(), "password", LEGACY_PASSWORD))))
                .andExpect(status().isOk());

        assertThat(users.findById(citizenA.getId()).orElseThrow().getPasswordHash())
                .startsWith(UpgradingPasswordEncoder.ARGON2_ID_PREFIX);

        mockMvc.perform(post(BASE + "/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.44.0.12");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", citizenA.getEmail(), "password", LEGACY_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void failedLoginLeavesLegacyHashUnchanged() throws Exception {
        mockMvc.perform(post(BASE + "/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.44.0.13");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", citizenA.getEmail(), "password", "Wrong!Pass123"))))
                .andExpect(status().isUnauthorized());

        assertThat(users.findById(citizenA.getId()).orElseThrow().getPasswordHash())
                .doesNotStartWith(UpgradingPasswordEncoder.ARGON2_ID_PREFIX);
    }
}
